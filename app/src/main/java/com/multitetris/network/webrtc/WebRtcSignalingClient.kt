package com.multitetris.network.webrtc

import android.content.Context
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.multitetris.network.social.MatchRole
import com.multitetris.network.social.MatchRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed interface WebRtcSignalState {
    data object Idle : WebRtcSignalState
    data class Connecting(
        val room: MatchRoom,
        val statusText: String = "Exchanging SDP & ICE candidates..."
    ) : WebRtcSignalState

    data class Connected(val room: MatchRoom) : WebRtcSignalState

    data class TimeoutError(
        val room: MatchRoom,
        val message: String = FIREWALL_TIMEOUT_MESSAGE
    ) : WebRtcSignalState

    data class Disconnected(val reason: String) : WebRtcSignalState

    companion object {
        const val FIREWALL_TIMEOUT_MESSAGE =
            "Direct P2P connection blocked by network firewall — try connecting on Wi-Fi"
    }
}

/**
 * Handles Firebase-assisted WebRTC SDP/ICE signaling over /matches/{matchId},
 * pending ICE candidate buffering prior to setRemoteDescription(), 12s NAT timeout,
 * database cleanup once both DataChannels open, and bidirectional NetPacket transport.
 */
class WebRtcSignalingClient(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val appContext: Context = context.applicationContext
    private val connectionLock = Any()
    private val sessionCounter = AtomicLong(0L)

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var activePcObserver: PeerConnection.Observer? = null
    private var activeDcObserver: DataChannel.Observer? = null

    private var activeRoom: MatchRoom? = null
    private var timeoutJob: Job? = null

    private var matchRef: DatabaseReference? = null
    private var sdpListener: ValueEventListener? = null
    private var sdpListenRef: DatabaseReference? = null
    private var remoteIceListener: ChildEventListener? = null
    private var remoteIceListenRef: DatabaseReference? = null
    private var openAckListener: ValueEventListener? = null

    private val pendingIceQueue = PendingIceCandidateQueue<IceCandidate>()
    private var hasAnsweredOffer = false
    private var hasProcessedAnswer = false

    private val _signalState = MutableStateFlow<WebRtcSignalState>(WebRtcSignalState.Idle)
    val signalState: StateFlow<WebRtcSignalState> = _signalState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<NetPacket>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<NetPacket> = _incomingPackets.asSharedFlow()

    fun connect(room: MatchRoom) {
        synchronized(connectionLock) {
            val currentState = _signalState.value
            if (activeRoom?.matchId == room.matchId &&
                (currentState is WebRtcSignalState.Connecting || currentState is WebRtcSignalState.Connected)
            ) {
                Log.d(TAG, "Ignoring duplicate connect(${room.matchId}) while $currentState")
                return
            }
        }

        closeActiveConnectionInternal(resetStateToIdle = false)
        val token = sessionCounter.incrementAndGet()

        synchronized(connectionLock) {
            activeRoom = room
            hasAnsweredOffer = false
            hasProcessedAnswer = false
            pendingIceQueue.reset()
        }

        _signalState.value = WebRtcSignalState.Connecting(room)

        val db = runCatching { FirebaseDatabase.getInstance() }.getOrNull()
        if (db == null) {
            _signalState.value = WebRtcSignalState.TimeoutError(
                room = room,
                message = "Firebase Database unavailable for WebRTC signaling."
            )
            return
        }

        val factory = ensurePeerConnectionFactory()
        val rtcConfig = WebRtcIceConfig.createRtcConfiguration()

        val roomMatchRef = db.getReference("matches").child(room.matchId)
        matchRef = roomMatchRef
        roomMatchRef.onDisconnect().removeValue()

        val pcObserver = createPeerConnectionObserver(token, room, roomMatchRef)
        synchronized(connectionLock) {
            if (sessionCounter.get() != token) return
            activePcObserver = pcObserver
        }

        val pc = factory.createPeerConnection(rtcConfig, pcObserver)
        if (pc == null) {
            _signalState.value = WebRtcSignalState.TimeoutError(
                room = room,
                message = "Could not initialize WebRTC PeerConnection."
            )
            return
        }

        synchronized(connectionLock) {
            if (sessionCounter.get() != token) {
                pc.dispose()
                return
            }
            peerConnection = pc
        }

        startConnectionTimeout(token, room, roomMatchRef)

        if (room.myRole == MatchRole.HOST) {
            startHostSignaling(token, pc, room, roomMatchRef)
        } else {
            startGuestSignaling(token, pc, room, roomMatchRef)
        }
    }

    private fun startHostSignaling(
        token: Long,
        pc: PeerConnection,
        room: MatchRoom,
        roomRef: DatabaseReference
    ) {
        // 1. Host MUST create the DataChannel BEFORE calling createOffer
        val init = DataChannel.Init().apply {
            ordered = true
        }
        val channel = pc.createDataChannel(DATA_CHANNEL_NAME, init)
        if (channel != null) {
            attachDataChannel(token, channel)
        }

        // 2. Listen for Guest ICE candidates
        val guestCandidatesRef = roomRef.child("guestCandidates")
        val iceChildListener = createRemoteIceListener(token, room, pc)
        remoteIceListenRef = guestCandidatesRef
        remoteIceListener = iceChildListener
        guestCandidatesRef.addChildEventListener(iceChildListener)

        // 3. Persistent listener for Guest SDP Answer
        val answerRef = roomRef.child("answer")
        val answerValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (sessionCounter.get() != token || hasProcessedAnswer || !snapshot.exists()) return
                val sdpText = snapshot.child("sdp").getValue(String::class.java)
                if (sdpText.isNullOrBlank()) return
                hasProcessedAnswer = true
                Log.d(TAG, "Answer received [HOST] matchId=${room.matchId}")

                val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdpText)
                pc.setRemoteDescription(
                    SimpleSdpObserver(
                        onSetSuccess = {
                            if (sessionCounter.get() != token) return@SimpleSdpObserver
                            val buffered = pendingIceQueue.pendingCount()
                            Log.d(TAG, "Host setRemoteDescription(ANSWER) succeeded; flushing $buffered queued ICE candidates")
                            pendingIceQueue.markRemoteDescriptionSetAndDrain { candidate ->
                                pc.addIceCandidate(candidate)
                                Log.d(
                                    TAG,
                                    "Remote ICE added [HOST] mid=${candidate.sdpMid} idx=${candidate.sdpMLineIndex}"
                                )
                            }
                        },
                        onFailure = { err ->
                            handleSignalingFailure(token, "Failed setting remote answer: $err")
                        }
                    ),
                    remoteDesc
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Answer listener cancelled: ${error.message}")
            }
        }
        sdpListenRef = answerRef
        sdpListener = answerValueListener
        answerRef.addValueEventListener(answerValueListener)

        // 4. Ensure /matches/{matchId} already has hostUid & guestUid before creating Offer
        roomRef.updateChildren(
            mapOf(
                "hostUid" to room.hostUid,
                "guestUid" to room.guestUid
            )
        ).addOnSuccessListener {
            if (sessionCounter.get() != token) return@addOnSuccessListener
            pc.createOffer(
                SimpleSdpObserver(
                    onCreateSuccess = { offerSdp ->
                        if (sessionCounter.get() != token) return@SimpleSdpObserver
                        pc.setLocalDescription(
                            SimpleSdpObserver(
                                onSetSuccess = {
                                    if (sessionCounter.get() != token) return@SimpleSdpObserver
                                    roomRef.child("offer").setValue(
                                        mapOf(
                                            "type" to offerSdp.type.canonicalForm(),
                                            "sdp" to offerSdp.description
                                        )
                                    ).addOnSuccessListener {
                                        Log.d(TAG, "Offer written [HOST] matchId=${room.matchId}")
                                    }.addOnFailureListener { err ->
                                        handleSignalingFailure(token, "Failed writing SDP offer: ${err.message}")
                                    }
                                },
                                onFailure = { err ->
                                    handleSignalingFailure(token, "Failed setting local offer: $err")
                                }
                            ),
                            offerSdp
                        )
                    },
                    onFailure = { err ->
                        handleSignalingFailure(token, "Failed creating SDP offer: $err")
                    }
                ),
                MediaConstraints()
            )
        }.addOnFailureListener { err ->
            handleSignalingFailure(token, "Failed writing hostUid/guestUid to /matches/${room.matchId}: ${err.message}")
        }
    }

    private fun startGuestSignaling(
        token: Long,
        pc: PeerConnection,
        room: MatchRoom,
        roomRef: DatabaseReference
    ) {
        // 1. Listen for Host ICE candidates
        val hostCandidatesRef = roomRef.child("hostCandidates")
        val iceChildListener = createRemoteIceListener(token, room, pc)
        remoteIceListenRef = hostCandidatesRef
        remoteIceListener = iceChildListener
        hostCandidatesRef.addChildEventListener(iceChildListener)

        // 2. Attach persistent ValueEventListener to /matches/{matchId}/offer
        val offerRef = roomRef.child("offer")
        val offerValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (sessionCounter.get() != token || hasAnsweredOffer || !snapshot.exists()) return
                val sdpText = snapshot.child("sdp").getValue(String::class.java)
                if (sdpText.isNullOrBlank()) return
                hasAnsweredOffer = true
                Log.d(TAG, "Offer received [GUEST] matchId=${room.matchId}")

                val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, sdpText)
                pc.setRemoteDescription(
                    SimpleSdpObserver(
                        onSetSuccess = {
                            if (sessionCounter.get() != token) return@SimpleSdpObserver
                            val buffered = pendingIceQueue.pendingCount()
                            Log.d(TAG, "Guest setRemoteDescription(OFFER) succeeded; flushing $buffered queued ICE candidates")
                            pendingIceQueue.markRemoteDescriptionSetAndDrain { candidate ->
                                pc.addIceCandidate(candidate)
                                Log.d(
                                    TAG,
                                    "Remote ICE added [GUEST] mid=${candidate.sdpMid} idx=${candidate.sdpMLineIndex}"
                                )
                            }
                            createAndSendAnswer(token, pc, room, roomRef)
                        },
                        onFailure = { err ->
                            handleSignalingFailure(token, "Failed setting remote offer: $err")
                        }
                    ),
                    remoteOffer
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Offer listener cancelled: ${error.message}")
            }
        }
        sdpListenRef = offerRef
        sdpListener = offerValueListener
        offerRef.addValueEventListener(offerValueListener)
    }

    private fun createAndSendAnswer(
        token: Long,
        pc: PeerConnection,
        room: MatchRoom,
        roomRef: DatabaseReference
    ) {
        pc.createAnswer(
            SimpleSdpObserver(
                onCreateSuccess = { answerSdp ->
                    if (sessionCounter.get() != token) return@SimpleSdpObserver
                    pc.setLocalDescription(
                        SimpleSdpObserver(
                            onSetSuccess = {
                                if (sessionCounter.get() != token) return@SimpleSdpObserver
                                roomRef.child("answer").setValue(
                                    mapOf(
                                        "type" to answerSdp.type.canonicalForm(),
                                        "sdp" to answerSdp.description
                                    )
                                ).addOnSuccessListener {
                                    Log.d(TAG, "Answer written [GUEST] matchId=${room.matchId}")
                                }.addOnFailureListener { err ->
                                    handleSignalingFailure(token, "Failed writing SDP answer: ${err.message}")
                                }
                            },
                            onFailure = { err ->
                                handleSignalingFailure(token, "Failed setting local answer: $err")
                            }
                        ),
                        answerSdp
                    )
                },
                onFailure = { err ->
                    handleSignalingFailure(token, "Failed creating SDP answer: $err")
                }
            ),
            MediaConstraints()
        )
    }

    private fun createRemoteIceListener(
        token: Long,
        room: MatchRoom,
        pc: PeerConnection
    ): ChildEventListener {
        return object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (sessionCounter.get() != token) return
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                if (sdp.isNullOrBlank()) return
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java) ?: ""
                val sdpMLineIndex = (snapshot.child("sdpMLineIndex").value as? Number)?.toInt() ?: 0
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                val key = "$sdpMid:$sdpMLineIndex:$sdp"

                pendingIceQueue.enqueueOrDispatch(key, candidate) { readyCandidate ->
                    pc.addIceCandidate(readyCandidate)
                    Log.d(
                        TAG,
                        "Remote ICE added [${room.myRole}] mid=${readyCandidate.sdpMid} idx=${readyCandidate.sdpMLineIndex}"
                    )
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Remote ICE listener cancelled: ${error.message}")
            }
        }
    }

    private fun createPeerConnectionObserver(
        token: Long,
        room: MatchRoom,
        roomRef: DatabaseReference
    ): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                if (sessionCounter.get() != token) return
                Log.d(
                    TAG,
                    "Local ICE gathered [${room.myRole}] mid=${candidate.sdpMid} idx=${candidate.sdpMLineIndex} sdp=${candidate.sdp}"
                )
                val nodeName = if (room.myRole == MatchRole.HOST) "hostCandidates" else "guestCandidates"
                roomRef.child(nodeName).push().setValue(
                    mapOf(
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "sdp" to candidate.sdp
                    )
                ).addOnFailureListener { err ->
                    Log.e(TAG, "Failed writing local ICE [${room.myRole}]: ${err.message}")
                }
            }

            override fun onDataChannel(channel: DataChannel) {
                if (sessionCounter.get() != token) return
                Log.d(TAG, "onDataChannel [${room.myRole}] label=${channel.label()} state=${channel.state()}")
                if (channel.label() == DATA_CHANNEL_NAME || dataChannel == null) {
                    attachDataChannel(token, channel)
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                if (sessionCounter.get() != token) return
                Log.d(TAG, "onIceConnectionChange [${room.myRole}] -> $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.FAILED -> {
                        if (_signalState.value is WebRtcSignalState.Connecting) {
                            triggerNatTimeoutOrFailure(token, room, roomRef)
                        } else if (_signalState.value is WebRtcSignalState.Connected) {
                            handleRemoteDisconnected(token, "Connection lost (ICE failed)")
                        }
                    }

                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        if (_signalState.value is WebRtcSignalState.Connected) {
                            handleRemoteDisconnected(token, "Opponent disconnected")
                        }
                    }

                    else -> Unit
                }
            }

            override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit
        }
    }

    private fun attachDataChannel(token: Long, channel: DataChannel) {
        val dcObserver = object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                if (sessionCounter.get() != token) return
                val state = channel.state()
                Log.d(TAG, "DataChannel.onStateChange label=${channel.label()} -> $state")
                when (state) {
                    DataChannel.State.OPEN -> handleDataChannelOpen(token)
                    DataChannel.State.CLOSING,
                    DataChannel.State.CLOSED -> {
                        if (_signalState.value is WebRtcSignalState.Connected) {
                            handleRemoteDisconnected(token, "Opponent left the match")
                        }
                    }
                    else -> Unit
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (sessionCounter.get() != token) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val raw = bytes.toString(Charsets.UTF_8)
                NetPacketCodec.decode(raw)?.let { packet ->
                    _incomingPackets.tryEmit(packet)
                }
            }
        }

        synchronized(connectionLock) {
            if (sessionCounter.get() != token) return
            dataChannel = channel
            activeDcObserver = dcObserver
        }

        channel.registerObserver(dcObserver)

        if (channel.state() == DataChannel.State.OPEN) {
            handleDataChannelOpen(token)
        }
    }

    private fun handleDataChannelOpen(token: Long) {
        val room: MatchRoom
        val roomRef: DatabaseReference?
        synchronized(connectionLock) {
            if (sessionCounter.get() != token) return
            room = activeRoom ?: return
            roomRef = matchRef
            timeoutJob?.cancel()
            timeoutJob = null
        }

        detachSignalingListenersOnly()

        // Mark this peer's DataChannel open, then delete /matches/{matchId} once BOTH devices reach OPEN
        if (roomRef != null) {
            val ackField = if (room.myRole == MatchRole.HOST) "hostOpen" else "guestOpen"
            roomRef.child(ackField).setValue(true)

            val ackListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val hostReady = snapshot.child("hostOpen").getValue(Boolean::class.java) == true
                    val guestReady = snapshot.child("guestOpen").getValue(Boolean::class.java) == true
                    if (hostReady && guestReady) {
                        roomRef.removeEventListener(this)
                        openAckListener = null
                        roomRef.removeValue()
                    }
                }

                override fun onCancelled(error: DatabaseError) = Unit
            }
            openAckListener = ackListener
            roomRef.addValueEventListener(ackListener)
        }

        _signalState.value = WebRtcSignalState.Connected(room)
    }

    private fun startConnectionTimeout(
        token: Long,
        room: MatchRoom,
        roomRef: DatabaseReference
    ) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (sessionCounter.get() != token) return@launch
            if (_signalState.value is WebRtcSignalState.Connecting) {
                triggerNatTimeoutOrFailure(token, room, roomRef)
            }
        }
    }

    private fun triggerNatTimeoutOrFailure(
        token: Long,
        room: MatchRoom,
        roomRef: DatabaseReference
    ) {
        if (sessionCounter.get() != token) return
        closeActiveConnectionInternal(resetStateToIdle = false)
        runCatching { roomRef.removeValue() }
        _signalState.value = WebRtcSignalState.TimeoutError(
            room = room,
            message = WebRtcSignalState.FIREWALL_TIMEOUT_MESSAGE
        )
    }

    private fun handleSignalingFailure(token: Long, reason: String) {
        Log.e(TAG, "Signaling failure: $reason")
        val room = activeRoom ?: return
        if (sessionCounter.get() != token) return
        if (_signalState.value is WebRtcSignalState.Connecting) {
            val ref = matchRef
            closeActiveConnectionInternal(resetStateToIdle = false)
            runCatching { ref?.removeValue() }
            _signalState.value = WebRtcSignalState.TimeoutError(
                room = room,
                message = WebRtcSignalState.FIREWALL_TIMEOUT_MESSAGE
            )
        }
    }

    private fun handleRemoteDisconnected(token: Long, reason: String) {
        if (sessionCounter.get() != token) return
        _signalState.value = WebRtcSignalState.Disconnected(reason)
    }

    fun sendPacket(packet: NetPacket): Boolean {
        val channel = synchronized(connectionLock) { dataChannel } ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        val jsonBytes = NetPacketCodec.encode(packet).toByteArray(Charsets.UTF_8)
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(jsonBytes), false)
        return runCatching { channel.send(buffer) }.getOrDefault(false)
    }

    fun close() {
        closeActiveConnectionInternal(resetStateToIdle = true)
    }

    fun dispose() {
        close()
        synchronized(connectionLock) {
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        }
    }

    private fun closeActiveConnectionInternal(resetStateToIdle: Boolean) {
        // Increment token BEFORE closing DataChannel/PeerConnection so intentional teardown
        // never emits spurious onIceConnectionChange(CLOSED) callbacks for an active session.
        sessionCounter.incrementAndGet()
        timeoutJob?.cancel()
        timeoutJob = null
        detachSignalingListenersOnly()
        openAckListener?.let { listener ->
            matchRef?.removeEventListener(listener)
        }
        openAckListener = null
        matchRef = null

        val dcToClose: DataChannel?
        val pcToClose: PeerConnection?
        synchronized(connectionLock) {
            dcToClose = dataChannel
            pcToClose = peerConnection
            dataChannel = null
            peerConnection = null
            activeDcObserver = null
            activePcObserver = null
            activeRoom = null
            pendingIceQueue.reset()
        }

        runCatching { dcToClose?.unregisterObserver() }
        runCatching { dcToClose?.close() }
        runCatching { dcToClose?.dispose() }
        runCatching { pcToClose?.close() }
        runCatching { pcToClose?.dispose() }

        if (resetStateToIdle) {
            _signalState.value = WebRtcSignalState.Idle
        }
    }

    private fun detachSignalingListenersOnly() {
        sdpListener?.let { sdpListenRef?.removeEventListener(it) }
        sdpListener = null
        sdpListenRef = null

        remoteIceListener?.let { remoteIceListenRef?.removeEventListener(it) }
        remoteIceListener = null
        remoteIceListenRef = null
    }

    private fun ensurePeerConnectionFactory(): PeerConnectionFactory {
        synchronized(connectionLock) {
            peerConnectionFactory?.let { return it }
            if (factoryInitialized.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
            }
            val factory = PeerConnectionFactory.builder()
                .createPeerConnectionFactory()
            peerConnectionFactory = factory
            return factory
        }
    }

    private class SimpleSdpObserver(
        private val onCreateSuccess: (SessionDescription) -> Unit = {},
        private val onSetSuccess: () -> Unit = {},
        private val onFailure: (String) -> Unit = {}
    ) : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = onCreateSuccess.invoke(sdp)
        override fun onSetSuccess() = onSetSuccess.invoke()
        override fun onCreateFailure(error: String) = onFailure.invoke(error)
        override fun onSetFailure(error: String) = onFailure.invoke(error)
    }

    companion object {
        private const val TAG = "MultiTetrisWebRTC"
        private const val DATA_CHANNEL_NAME = "tetris-sync"
        const val CONNECTION_TIMEOUT_MS = 20_000L
        private val factoryInitialized = AtomicBoolean(false)
    }
}
