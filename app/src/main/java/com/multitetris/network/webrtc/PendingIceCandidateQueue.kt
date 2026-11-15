package com.multitetris.network.webrtc

/**
 * Buffers remote ICE candidates that arrive before PeerConnection.setRemoteDescription()
 * finishes, preventing race-condition drops during Firebase-assisted WebRTC signaling.
 */
class PendingIceCandidateQueue<T> {
    private val lock = Any()
    private val pending = ArrayDeque<T>()
    private val seenKeys = HashSet<String>()
    private var remoteDescriptionSet = false

    fun enqueueOrDispatch(
        key: String,
        candidate: T,
        dispatch: (T) -> Unit
    ) {
        val shouldDispatchNow: Boolean
        synchronized(lock) {
            if (!seenKeys.add(key)) {
                return
            }
            if (remoteDescriptionSet) {
                shouldDispatchNow = true
            } else {
                pending.addLast(candidate)
                shouldDispatchNow = false
            }
        }
        if (shouldDispatchNow) {
            dispatch(candidate)
        }
    }

    fun markRemoteDescriptionSetAndDrain(dispatch: (T) -> Unit) {
        val drained: List<T>
        synchronized(lock) {
            remoteDescriptionSet = true
            if (pending.isEmpty()) {
                return
            }
            drained = pending.toList()
            pending.clear()
        }
        for (candidate in drained) {
            dispatch(candidate)
        }
    }

    fun pendingCount(): Int = synchronized(lock) { pending.size }

    fun reset() {
        synchronized(lock) {
            pending.clear()
            seenKeys.clear()
            remoteDescriptionSet = false
        }
    }
}
