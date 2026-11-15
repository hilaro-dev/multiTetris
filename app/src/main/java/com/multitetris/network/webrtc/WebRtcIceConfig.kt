package com.multitetris.network.webrtc

import org.webrtc.PeerConnection

/**
 * ICE server configuration combining Google & Metered STUN servers with TURN relay
 * fallbacks (UDP/80, TCP/443, and TLS/443) for restrictive CGNAT and university networks.
 */
object WebRtcIceConfig {
    const val STUN_SERVER_PRIMARY = "stun:stun.l.google.com:19302"
    const val STUN_SERVER_SECONDARY = "stun:stun.relay.metered.ca:80"

    const val TURN_UDP_URL = "turn:global.relay.metered.ca:80"
    const val TURN_TCP_443_URL = "turn:global.relay.metered.ca:443?transport=tcp"
    const val TURN_TLS_443_URL = "turns:global.relay.metered.ca:443?transport=tcp"
    const val TURN_USERNAME = "4a085edebea859938271e5cc"
    const val TURN_CREDENTIAL = "4h/6kvPPzMTDozSQ"

    fun buildIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            PeerConnection.IceServer.builder(STUN_SERVER_PRIMARY).createIceServer(),
            PeerConnection.IceServer.builder(STUN_SERVER_SECONDARY).createIceServer(),
            PeerConnection.IceServer.builder(TURN_UDP_URL)
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_CREDENTIAL)
                .createIceServer(),
            PeerConnection.IceServer.builder(TURN_TCP_443_URL)
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_CREDENTIAL)
                .createIceServer(),
            PeerConnection.IceServer.builder(TURN_TLS_443_URL)
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_CREDENTIAL)
                .createIceServer()
        )
    }

    fun createRtcConfiguration(): PeerConnection.RTCConfiguration {
        return PeerConnection.RTCConfiguration(buildIceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }
    }
}