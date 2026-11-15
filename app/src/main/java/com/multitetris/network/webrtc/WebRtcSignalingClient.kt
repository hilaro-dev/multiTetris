package com.multitetris.network.webrtc

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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
import kotlinx.coroutines.flow.update
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class IncomingPeerPacket(
    val fromUid: String,
    val slotIndex: Int,
    val packet: NetPacket
)

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
 * Handles Firebase-assisted Star-Topology WebRTC SDP/ICE signaling over
 * `/matches/{matchId}/peers/{guestUid}` for 2-to-4 player rooms:
 * - Host acts as the central hub managing up to 3 simultaneous Guest PeerConnections/DataChannels.
 * - Each Guest maintains a single PeerConnection/DataChannel directly to the Host.
 * - Preserves Metered TURN relay configuration via WebRtcIceConfig and per-peer
 *   PendingIceCandidateQueue buffering prior to setRemoteDescription().
 */
data class PeerLeftEvent(
    val uid: String,
    val slotIndex: Int
)

class WebRtcSignalingClient(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val appContext: Context = context.applicationContext
    private val connectionLock = Any()
    private val sessionCounter = AtomicLong(0L)

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var activeRoom: MatchRoom? = null
    private var matchRef: DatabaseReference? = null

    private val peerSlots = ConcurrentHashMap<String, PeerSlot>()

    private val _signalState = MutableStateFlow<WebRtcSignalState>(WebRtcSignalState.Idle)
    val signalState: StateFlow<WebRtcSignalState> = _signalState.asStateFlow()

    private val _connectedPeerUids = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeerUids: StateFlow<Set<String>> = _connectedPeerUids.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<NetPacket>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<NetPacket> = _incomingPackets.asSharedFlow()

    private val _peerIncomingPackets = MutableSharedFlow<IncomingPeerPacket>(extraBufferCapacity = 128)
    val peerIncomingPackets: SharedFlow<IncomingPeerPacket> = _peerIncomingPackets.asSharedFlow()

    private val _peerLeftEvents = MutableSharedFlow<PeerLeftEvent>(extraBufferCapacity = 16)
    val peerLeftEvents: SharedFlow<PeerLeftEvent> = _peerLeftEvents.asSharedFlow()

    private inner class PeerSlot(
        val peerUid: String,
        @Volatile var slotIndex: Int,
        val token: Long,
        val peerRef: DatabaseReference
    ) {
        val pendingIceQueue = PendingIceCandidateQueue<IceCandidate>()
        @Volatile var leftNotified: Boolean = false
        @Volatile var peerConnection: PeerConnection? = null
        @Volatile var dataChannel: DataChannel? = null
        @Volatile var pcObserver: PeerConnection.Observer? = null
        @Volatile var dcObserver: DataChannel.Observer? = null
        @Volatile var sdpListener: ValueEventListener? = null
        @Volatile var sdpListenRef: DatabaseReference? = null
        @Volatile var remoteIceListener: ChildEventListener? = null
        @Volatile var remoteIceListenRef: DatabaseReference? = null
        @Volatile var hasProcessedRemoteSdp: Boolean = false
        @Volatile var timeoutJob: Job? = null

        fun isOpen(): Boolean = dataChannel?.state() == DataChannel.State.OPEN

        fun close() {
            timeoutJob?.cancel()
            timeoutJob = null
            sdpListener?.let { listener -> sdpListenRef?.removeEventListener(listener) }
            sdpListener = null
            sdpListenRef = null
            remoteIceListener?.let { listener -> remoteIceListenRef?.removeEventListener(listener) }
            remoteIceListener = null
            remoteIceListenRef = null

            val dc = dataChannel
            val pc = peerConnection
            dataChannel = null
            peerConnection = null
            dcObserver = null
            pcObserver = null
            pendingIceQueue.reset()

            runCatching { dc?.unregisterObserver() }
            runCatching { dc?.close() }
            runCatching { dc?.dispose() }
            runCatching { pc?.close() }
            runCatching { pc?.dispose() }
        }
    }

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
        }
        _connectedPeerUids.value = emptySet()

        val guests = room.resolvedPlayers().filter { it.uid != room.hostUid && it.uid.isNotBlank() }
        val statusText = if (room.myRole == MatchRole.HOST && guests.isEmpty()) {
            "Room ready — invite up to 3 friends..."
        } else {
            "Exchanging SDP & ICE candidates..."
        }
        _signalState.value = WebRtcSignalState.Connecting(room, statusText)

        val db = runCatching { FirebaseDatabase.getInstance() }.getOrNull()
        if (db == null) {
            _signalState.value = WebRtcSignalState.TimeoutError(
                room = room,
                message = "Firebase Database unavailable for WebRTC signaling."
            )
            return
        }

        ensurePeerConnectionFactory()
        val roomMatchRef = db.getReference("matches").child(room.matchId)
        matchRef = roomMatchRef
        if (room.myRole == MatchRole.HOST) {
            roomMatchRef.onDisconnect().removeValue()
            syncHostRoomPeers(room)
        } else {
            startGuestPeerConnection(token, room, roomMatchRef)
        }
    }

    fun updateRoom(updatedRoom: MatchRoom) {
        val prevRoom: MatchRoom?
        synchronized(connectionLock) {
            prevRoom = activeRoom
            if (prevRoom?.matchId != updatedRoom.matchId) return
            activeRoom = updatedRoom
        }

        if (updatedRoom.myRole == MatchRole.HOST) {
            syncHostRoomPeers(updatedRoom)
        }

        if (updatedRoom.state != "lobby") {
            notifyMatchStarted(updatedRoom)
        } else if (_signalState.value is WebRtcSignalState.Connecting) {
            val connectedCount = _connectedPeerUids.value.size
            val totalGuests = (updatedRoom.resolvedPlayers().size - 1).coerceAtLeast(0)
            val status = if (updatedRoom.myRole == MatchRole.HOST) {
                if (totalGuests == 0) {
                    "Room ready — invite up to 3 friends..."
                } else {
                    "Connected to $connectedCount / $totalGuests friend(s)"
                }
            } else {
                if (connectedCount > 0) {
                    "Connected to host — waiting for host to start match..."
                } else {
                    "Connecting to host..."
                }
            }
            _signalState.value = WebRtcSignalState.Connecting(updatedRoom, status)
        }
    }

    fun syncHostRoomPeers(room: MatchRoom) {
        if (room.myRole != MatchRole.HOST) return
        val roomMatchRef = matchRef ?: return
        val token = sessionCounter.get()
        if (sessionCounter.get() != token) return

        val desiredGuests = room.resolvedPlayers()
            .filter { it.uid != room.hostUid && it.uid.isNotBlank() }
            .take(MAX_GUEST_PEERS)
        val desiredUids = desiredGuests.map { it.uid }.toSet()

        // Remove any guest that left the room
        val toRemove = peerSlots.keys.filter { it !in desiredUids }
        for (removedUid in toRemove) {
            peerSlots.remove(removedUid)?.close()
            _connectedPeerUids.update { it - removedUid }
        }

        // Start HostPeerSession for each newly joined guest
        for (guest in desiredGuests) {
            val existing = peerSlots[guest.uid]
            if (existing != null) {
                existing.slotIndex = guest.slotIndex
                continue
            }
            startHostPeerForGuest(
                token = token,
                room = room,
                roomRef = roomMatchRef,
                guestUid = guest.uid,
                slotIndex = guest.slotIndex
            )
        }
    }

    fun notifyMatchStarted(room: MatchRoom) {
        if (room.state != "playing" && room.state != "signaling") return
        if (_signalState.value is WebRtcSignalState.Connected) return
        val anyOpen = peerSlots.values.any { it.isOpen() }
        if (anyOpen || _connectedPeerUids.value.isNotEmpty()) {
            Log.d(TAG, "Transitioning to Connected [${room.myRole}] matchId=${room.matchId}")
            _signalState.value = WebRtcSignalState.Connected(room)
        }
    }

    private fun startHostPeerForGuest(
        token: Long,
        room: MatchRoom,
        roomRef: DatabaseReference,
        guestUid: String,
        slotIndex: Int
    ) {
        val factory = ensurePeerConnectionFactory()
        val rtcConfig = WebRtcIceConfig.createRtcConfiguration()
        val peerRef = roomRef.child("peers").child(guestUid)
        val slot = PeerSlot(
            peerUid = guestUid,
            slotIndex = slotIndex,
            token = token,
            peerRef = peerRef
        )

        val pcObserver = createPeerConnectionObserver(slot, room, isHostSide = true)
        val pc = synchronized(connectionLock) {
            if (sessionCounter.get() != token) return
            slot.pcObserver = pcObserver
            factory.createPeerConnection(rtcConfig, pcObserver)
        }
        if (pc == null) {
            Log.e(TAG, "Failed to create PeerConnection for guest=$guestUid")
            return
        }

        synchronized(connectionLock) {
            if (sessionCounter.get() != token) {
                pc.dispose()
                return
            }
            slot.peerConnection = pc
            peerSlots[guestUid] = slot
        }

        startPeerTimeout(slot, room)

        // 1. Host creates DataChannel BEFORE createOffer
        val init = DataChannel.Init().apply { ordered = true }
        val channel = pc.createDataChannel(DATA_CHANNEL_NAME, init)
        if (channel != null) {
            attachDataChannel(slot, channel, isHostSide = true)
        }

        // 2. Listen for this Guest's ICE candidates
        val guestCandidatesRef = peerRef.child("guestCandidates")
        val iceChildListener = createRemoteIceListener(slot, pc, "HOST<-$guestUid")
        slot.remoteIceListenRef = guestCandidatesRef
        slot.remoteIceListener = iceChildListener
        guestCandidatesRef.addChildEventListener(iceChildListener)

        // 3. Listen for this Guest's SDP Answer
        val answerRef = peerRef.child("answer")
        val answerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (sessionCounter.get() != token || slot.hasProcessedRemoteSdp || !snapshot.exists()) return
                val sdpText = snapshot.child("sdp").getValue(String::class.java)
                if (sdpText.isNullOrBlank()) return
                slot.hasProcessedRemoteSdp = true
                Log.d(TAG, "Answer received [HOST] from guest=$guestUid")

                val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdpText)
                pc.setRemoteDescription(
                    SimpleSdpObserver(
                        onSetSuccess = {
                            if (sessionCounter.get() != token) return@SimpleSdpObserver
                            val buffered = slot.pendingIceQueue.pendingCount()
                            Log.d(TAG, "Host setRemoteDescription(ANSWER) for guest=$guestUid; flushing $buffered ICE")
                            slot.pendingIceQueue.markRemoteDescriptionSetAndDrain { candidate ->
                                pc.addIceCandidate(candidate)
                            }
                        },
                        onFailure = { err ->
                            Log.e(TAG, "Failed setting remote answer for $guestUid: $err")
                        }
                    ),
                    remoteDesc
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Answer listener cancelled for $guestUid: ${error.message}")
            }
        }
        slot.sdpListenRef = answerRef
        slot.sdpListener = answerListener
        answerRef.addValueEventListener(answerListener)

        // 4. Create SDP Offer and write to /matches/{matchId}/peers/{guestUid}/offer
        pc.createOffer(
            SimpleSdpObserver(
                onCreateSuccess = { offerSdp ->
                    if (sessionCounter.get() != token) return@SimpleSdpObserver
                    pc.setLocalDescription(
                        SimpleSdpObserver(
                            onSetSuccess = {
                                if (sessionCounter.get() != token) return@SimpleSdpObserver
                                peerRef.child("offer").setValue(
                                    mapOf(
                                        "type" to offerSdp.type.canonicalForm(),
                                        "sdp" to offerSdp.description
                                    )
                                ).addOnSuccessListener {
                                    Log.d(TAG, "Offer written [HOST] peer=$guestUid")
                                }.addOnFailureListener { err ->
                                    Log.e(TAG, "Failed writing offer for $guestUid: ${err.message}")
                                }
                            },
                            onFailure = { err ->
                                Log.e(TAG, "Failed setting local offer for $guestUid: $err")
                            }
                        ),
                        offerSdp
                    )
                },
                onFailure = { err ->
                    Log.e(TAG, "Failed creating SDP offer for $guestUid: $err")
                }
            ),
            MediaConstraints()
        )
    }

    private fun startGuestPeerConnection(
        token: Long,
        room: MatchRoom,
        roomRef: DatabaseReference
    ) {
        val myUid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }
            .getOrNull()
            .orEmpty()
            .ifBlank { room.guestUid }
        if (myUid.isBlank()) {
            _signalState.value = WebRtcSignalState.TimeoutError(
                room = room,
                message = "Guest UID missing for WebRTC signaling."
            )
            return
        }

        val factory = ensurePeerConnectionFactory()
        val rtcConfig = WebRtcIceConfig.createRtcConfiguration()
        val peerRef = roomRef.child("peers").child(myUid)
        val slot = PeerSlot(
            peerUid = room.hostUid,
            slotIndex = 1,
            token = token,
            peerRef = peerRef
        )

        val pcObserver = createPeerConnectionObserver(slot, room, isHostSide = false)
        val pc = synchronized(connectionLock) {
            if (sessionCounter.get() != token) return
            slot.pcObserver = pcObserver
            factory.createPeerConnection(rtcConfig, pcObserver)
        }
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
            slot.peerConnection = pc
            peerSlots[room.hostUid] = slot
        }

        startPeerTimeout(slot, room)

        // 1. Listen for Host ICE candidates under /peers/{myUid}/hostCandidates
        val hostCandidatesRef = peerRef.child("hostCandidates")
        val iceChildListener = createRemoteIceListener(slot, pc, "GUEST<-HOST")
        slot.remoteIceListenRef = hostCandidatesRef
        slot.remoteIceListener = iceChildListener
        hostCandidatesRef.addChildEventListener(iceChildListener)

        // 2. Listen for Host SDP Offer under /peers/{myUid}/offer
        val offerRef = peerRef.child("offer")
        val offerValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (sessionCounter.get() != token || slot.hasProcessedRemoteSdp || !snapshot.exists()) return
                val sdpText = snapshot.child("sdp").getValue(String::class.java)
                if (sdpText.isNullOrBlank()) return
                slot.hasProcessedRemoteSdp = true
                Log.d(TAG, "Offer received [GUEST] myUid=$myUid matchId=${room.matchId}")

                val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, sdpText)
                pc.setRemoteDescription(
                    SimpleSdpObserver(
                        onSetSuccess = {
                            if (sessionCounter.get() != token) return@SimpleSdpObserver
                            val buffered = slot.pendingIceQueue.pendingCount()
                            Log.d(TAG, "Guest setRemoteDescription(OFFER) succeeded; flushing $buffered ICE")
                            slot.pendingIceQueue.markRemoteDescriptionSetAndDrain { candidate ->
                                pc.addIceCandidate(candidate)
                            }
                            createAndSendAnswer(slot, pc, peerRef)
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
        slot.sdpListenRef = offerRef
        slot.sdpListener = offerValueListener
        offerRef.addValueEventListener(offerValueListener)
    }

    private fun createAndSendAnswer(
        slot: PeerSlot,
        pc: PeerConnection,
        peerRef: DatabaseReference
    ) {
        val token = slot.token
        pc.createAnswer(
            SimpleSdpObserver(
                onCreateSuccess = { answerSdp ->
                    if (sessionCounter.get() != token) return@SimpleSdpObserver
                    pc.setLocalDescription(
                        SimpleSdpObserver(
                            onSetSuccess = {
                                if (sessionCounter.get() != token) return@SimpleSdpObserver
                                peerRef.child("answer").setValue(
                                    mapOf(
                                        "type" to answerSdp.type.canonicalForm(),
                                        "sdp" to answerSdp.description
                                    )
                                ).addOnSuccessListener {
                                    Log.d(TAG, "Answer written [GUEST] -> host")
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
        slot: PeerSlot,
        pc: PeerConnection,
        logPrefix: String
    ): ChildEventListener {
        return object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (sessionCounter.get() != slot.token) return
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                if (sdp.isNullOrBlank()) return
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java) ?: ""
                val sdpMLineIndex = (snapshot.child("sdpMLineIndex").value as? Number)?.toInt() ?: 0
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                val key = "$sdpMid:$sdpMLineIndex:$sdp"

                slot.pendingIceQueue.enqueueOrDispatch(key, candidate) { readyCandidate ->
                    pc.addIceCandidate(readyCandidate)
                    Log.d(
                        TAG,
                        "Remote ICE added [$logPrefix] mid=${readyCandidate.sdpMid} idx=${readyCandidate.sdpMLineIndex}"
                    )
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Remote ICE listener cancelled [$logPrefix]: ${error.message}")
            }
        }
    }

    private fun createPeerConnectionObserver(
        slot: PeerSlot,
        room: MatchRoom,
        isHostSide: Boolean
    ): PeerConnection.Observer {
        val roleTag = if (isHostSide) "HOST->${slot.peerUid}" else "GUEST->HOST"
        return object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                if (sessionCounter.get() != slot.token) return
                Log.d(
                    TAG,
                    "Local ICE gathered [$roleTag] mid=${candidate.sdpMid} idx=${candidate.sdpMLineIndex} sdp=${candidate.sdp}"
                )
                val nodeName = if (isHostSide) "hostCandidates" else "guestCandidates"
                slot.peerRef.child(nodeName).push().setValue(
                    mapOf(
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "sdp" to candidate.sdp
                    )
                ).addOnFailureListener { err ->
                    Log.e(TAG, "Failed writing local ICE [$roleTag]: ${err.message}")
                }
            }

            override fun onDataChannel(channel: DataChannel) {
                if (sessionCounter.get() != slot.token) return
                Log.d(TAG, "onDataChannel [$roleTag] label=${channel.label()} state=${channel.state()}")
                if (channel.label() == DATA_CHANNEL_NAME || slot.dataChannel == null) {
                    attachDataChannel(slot, channel, isHostSide)
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                if (sessionCounter.get() != slot.token) return
                Log.d(TAG, "onIceConnectionChange [$roleTag] -> $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.FAILED -> {
                        if (!isHostSide && _signalState.value is WebRtcSignalState.Connecting) {
                            triggerNatTimeoutOrFailure(slot.token, room)
                        } else if (!isHostSide && _signalState.value is WebRtcSignalState.Connected) {
                            handleRemoteDisconnected(slot.token, "Host left or disconnected.")
                        } else if (isHostSide) {
                            _connectedPeerUids.update { it - slot.peerUid }
                            notifyPeerLeftIfHost(slot)
                        }
                    }

                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        if (!isHostSide && _signalState.value is WebRtcSignalState.Connected) {
                            handleRemoteDisconnected(slot.token, "Host left or disconnected.")
                        } else if (isHostSide) {
                            _connectedPeerUids.update { it - slot.peerUid }
                            notifyPeerLeftIfHost(slot)
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

    private fun attachDataChannel(slot: PeerSlot, channel: DataChannel, isHostSide: Boolean) {
        val dcObserver = object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                if (sessionCounter.get() != slot.token) return
                val state = channel.state()
                Log.d(TAG, "DataChannel.onStateChange peer=${slot.peerUid} label=${channel.label()} -> $state")
                when (state) {
                    DataChannel.State.OPEN -> handlePeerChannelOpen(slot, isHostSide)
                    DataChannel.State.CLOSING,
                    DataChannel.State.CLOSED -> {
                        _connectedPeerUids.update { it - slot.peerUid }
                        if (isHostSide) {
                            notifyPeerLeftIfHost(slot)
                        } else if (_signalState.value is WebRtcSignalState.Connected) {
                            handleRemoteDisconnected(slot.token, "Host left or disconnected.")
                        }
                    }
                    else -> Unit
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (sessionCounter.get() != slot.token) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val raw = bytes.toString(Charsets.UTF_8)
                NetPacketCodec.decode(raw)?.let { packet ->
                    _incomingPackets.tryEmit(packet)
                    _peerIncomingPackets.tryEmit(
                        IncomingPeerPacket(
                            fromUid = slot.peerUid,
                            slotIndex = slot.slotIndex,
                            packet = packet
                        )
                    )
                    // If Host began broadcasting GameStatePacket while Guest was in lobby, ensure Connected
                    if (!isHostSide && packet is GameStatePacket && _signalState.value !is WebRtcSignalState.Connected) {
                        val room = synchronized(connectionLock) { activeRoom }
                        if (room != null) {
                            _signalState.value = WebRtcSignalState.Connected(room)
                        }
                    }
                }
            }
        }

        synchronized(connectionLock) {
            if (sessionCounter.get() != slot.token) return
            slot.dataChannel = channel
            slot.dcObserver = dcObserver
        }

        channel.registerObserver(dcObserver)
        if (channel.state() == DataChannel.State.OPEN) {
            handlePeerChannelOpen(slot, isHostSide)
        }
    }

    private fun handlePeerChannelOpen(slot: PeerSlot, isHostSide: Boolean) {
        val room: MatchRoom
        synchronized(connectionLock) {
            if (sessionCounter.get() != slot.token) return
            room = activeRoom ?: return
            slot.timeoutJob?.cancel()
            slot.timeoutJob = null
        }

        _connectedPeerUids.update { it + slot.peerUid }
        Log.d(TAG, "DataChannel OPEN [${if (isHostSide) "HOST" else "GUEST"}] peer=${slot.peerUid} connected=${_connectedPeerUids.value.size}")

        if (room.state != "lobby") {
            _signalState.value = WebRtcSignalState.Connected(room)
        } else if (_signalState.value is WebRtcSignalState.Connecting) {
            val totalGuests = (room.resolvedPlayers().size - 1).coerceAtLeast(1)
            val status = if (isHostSide) {
                "Connected to ${_connectedPeerUids.value.size} / $totalGuests friend(s)"
            } else {
                "Connected to host — waiting for host to start..."
            }
            _signalState.value = WebRtcSignalState.Connecting(room, status)
        }
    }

    private fun startPeerTimeout(slot: PeerSlot, room: MatchRoom) {
        slot.timeoutJob?.cancel()
        slot.timeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (sessionCounter.get() != slot.token) return@launch
            if (!slot.isOpen()) {
                Log.w(TAG, "Peer ${slot.peerUid} timed out after ${CONNECTION_TIMEOUT_MS}ms")
                if (room.myRole == MatchRole.GUEST && _connectedPeerUids.value.isEmpty()) {
                    triggerNatTimeoutOrFailure(slot.token, room)
                } else if (room.myRole == MatchRole.HOST &&
                    _connectedPeerUids.value.isEmpty() &&
                    room.state != "lobby"
                ) {
                    triggerNatTimeoutOrFailure(slot.token, room)
                }
            }
        }
    }

    private fun triggerNatTimeoutOrFailure(
        token: Long,
        room: MatchRoom
    ) {
        if (sessionCounter.get() != token) return
        val refToClean = if (room.myRole == MatchRole.HOST) matchRef else null
        closeActiveConnectionInternal(resetStateToIdle = false)
        runCatching { refToClean?.removeValue() }
        _signalState.value = WebRtcSignalState.TimeoutError(
            room = room,
            message = WebRtcSignalState.FIREWALL_TIMEOUT_MESSAGE
        )
    }

    private fun handleSignalingFailure(token: Long, reason: String) {
        Log.e(TAG, "Signaling failure: $reason")
        val room = activeRoom ?: return
        if (sessionCounter.get() != token) return
        if (_signalState.value is WebRtcSignalState.Connecting && _connectedPeerUids.value.isEmpty()) {
            val refToClean = if (room.myRole == MatchRole.HOST) matchRef else null
            closeActiveConnectionInternal(resetStateToIdle = false)
            runCatching { refToClean?.removeValue() }
            _signalState.value = WebRtcSignalState.TimeoutError(
                room = room,
                message = WebRtcSignalState.FIREWALL_TIMEOUT_MESSAGE
            )
        }
    }

    private fun notifyPeerLeftIfHost(slot: PeerSlot) {
        if (slot.leftNotified) return
        if (_signalState.value !is WebRtcSignalState.Connected) return
        slot.leftNotified = true
        _peerLeftEvents.tryEmit(PeerLeftEvent(uid = slot.peerUid, slotIndex = slot.slotIndex))
    }

    private fun handleRemoteDisconnected(token: Long, reason: String) {
        if (sessionCounter.get() != token) return
        _signalState.value = WebRtcSignalState.Disconnected(reason)
    }

    fun sendPacket(packet: NetPacket): Boolean {
        val jsonBytes = NetPacketCodec.encode(packet).toByteArray(Charsets.UTF_8)
        var anySent = false
        for (slot in peerSlots.values) {
            val channel = slot.dataChannel ?: continue
            if (channel.state() != DataChannel.State.OPEN) continue
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(jsonBytes), false)
            if (runCatching { channel.send(buffer) }.getOrDefault(false)) {
                anySent = true
            }
        }
        return anySent
    }

    fun sendPacketToPeer(peerUid: String, packet: NetPacket): Boolean {
        val channel = peerSlots[peerUid]?.dataChannel ?: return false
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
        sessionCounter.incrementAndGet()
        val slotsToClose = peerSlots.values.toList()
        peerSlots.clear()
        slotsToClose.forEach { it.close() }
        matchRef = null
        _connectedPeerUids.value = emptySet()

        synchronized(connectionLock) {
            activeRoom = null
        }

        if (resetStateToIdle) {
            _signalState.value = WebRtcSignalState.Idle
        }
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
        private const val MAX_GUEST_PEERS = 3
        const val CONNECTION_TIMEOUT_MS = 20_000L
        private val factoryInitialized = AtomicBoolean(false)
    }
}
