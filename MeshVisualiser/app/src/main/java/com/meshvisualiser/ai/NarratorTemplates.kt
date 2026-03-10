package com.meshvisualiser.ai

/**
 * Pre-written templates for common protocol events — used instead of LLM calls
 * for fast, consistent explanations of well-understood events.
 */
object NarratorTemplates {

    data class NarratorMessage(
        val title: String,
        val explanation: String,
        val isTemplate: Boolean = true
    )

    // --- TCP Events ---

    fun firstTcpRetransmission(peerModel: String, seqNum: Int, timeoutMs: Long): NarratorMessage =
        NarratorMessage(
            title = "TCP Retransmission",
            explanation = "No ACK received from $peerModel for packet #$seqNum after ${timeoutMs}ms. " +
                "TCP automatically retries — this is its reliability guarantee in action. " +
                "In real networks, this happens when packets are lost or delayed beyond the timeout."
        )

    fun tcpDelivered(peerModel: String, rttMs: Long): NarratorMessage =
        NarratorMessage(
            title = "TCP Delivery Confirmed",
            explanation = "$peerModel confirmed receipt in ${rttMs}ms. " +
                "This round-trip time (RTT) measures the full journey: your device → peer → ACK back. " +
                "TCP uses RTT to tune its retransmission timeout."
        )

    fun tcpFailed(peerModel: String, retries: Int): NarratorMessage =
        NarratorMessage(
            title = "TCP Gave Up",
            explanation = "After $retries retries, TCP couldn't deliver to $peerModel. " +
                "In real networks, this usually means the connection is broken. " +
                "Applications get notified of this failure and can reconnect or alert the user."
        )

    // --- UDP Events ---

    fun firstUdpDrop(): NarratorMessage =
        NarratorMessage(
            title = "UDP Packet Lost",
            explanation = "A UDP packet was dropped and neither sender nor receiver knows! " +
                "This is UDP's trade-off: it's fast because it doesn't wait for confirmations, " +
                "but lost packets are simply gone. Video streaming and gaming accept this trade-off."
        )

    fun udpSent(peerModel: String): NarratorMessage =
        NarratorMessage(
            title = "UDP Sent",
            explanation = "Packet fired off to $peerModel with no delivery guarantee. " +
                "UDP is 'fire and forget' — the sender moves on immediately. " +
                "This makes it faster than TCP for time-sensitive data like voice calls."
        )

    // --- CSMA/CD Events ---

    fun firstCollision(peerCount: Int): NarratorMessage =
        NarratorMessage(
            title = "CSMA/CD Collision!",
            explanation = "Two devices tried to transmit at the same time on the shared medium. " +
                "With $peerCount peers, collisions are expected. The CSMA/CD protocol detects this " +
                "and both devices back off for a random time before retrying."
        )

    fun backoffStarted(attempt: Int, slots: Int, backoffMs: Long): NarratorMessage =
        NarratorMessage(
            title = "Exponential Backoff",
            explanation = "Attempt #$attempt: waiting $slots slots (${backoffMs}ms) before retrying. " +
                "Each collision doubles the maximum backoff window — this is 'exponential backoff'. " +
                "It prevents repeated collisions by spreading out retry times."
        )

    fun csmaSuccess(attempts: Int): NarratorMessage =
        NarratorMessage(
            title = "Medium Acquired",
            explanation = if (attempts == 0)
                "Channel was clear — transmitted on the first try. " +
                    "CSMA/CD's 'listen before talk' approach works well when traffic is light."
            else
                "Successfully transmitted after $attempts collision(s). " +
                    "The exponential backoff algorithm resolved the contention."
        )

    // --- Leader Election ---

    fun electionStarted(peerCount: Int): NarratorMessage =
        NarratorMessage(
            title = "Leader Election Started",
            explanation = "The Bully Algorithm is selecting a leader from ${peerCount + 1} devices. " +
                "Each device with a higher ID challenges others — the highest unchallenged ID wins. " +
                "This is how distributed systems agree on a coordinator without a central server."
        )

    fun leaderElected(isLocal: Boolean): NarratorMessage =
        NarratorMessage(
            title = if (isLocal) "You're the Leader!" else "Leader Elected",
            explanation = if (isLocal)
                "Your device has the highest ID — you won the Bully Algorithm election. " +
                    "As leader, you'll host the Cloud Anchor that aligns everyone's AR view."
            else
                "Another device had a higher ID and became leader. " +
                    "Your device will follow the leader's Cloud Anchor for shared AR coordinates."
        )

    // --- Escalation messages (when events repeat) ---

    fun repeatedRetransmissions(count: Int, peerModel: String): NarratorMessage =
        NarratorMessage(
            title = "Frequent Retransmissions",
            explanation = "That's $count retransmissions to $peerModel already. " +
                "High packet loss is forcing TCP to work overtime. In real networks, " +
                "this degrades throughput significantly — TCP slows down its sending rate " +
                "via congestion control when it detects losses.",
            isTemplate = false // Suggests LLM could provide deeper analysis
        )

    fun repeatedCollisions(count: Int): NarratorMessage =
        NarratorMessage(
            title = "Heavy Contention",
            explanation = "$count collisions detected in this session. " +
                "The shared medium is congested. In old Ethernet networks, this led to " +
                "'collision domains' — modern switches eliminate this by giving each port " +
                "its own collision domain.",
            isTemplate = false
        )

    fun repeatedUdpDrops(count: Int): NarratorMessage =
        NarratorMessage(
            title = "High UDP Loss Rate",
            explanation = "$count UDP packets dropped this session. " +
                "Applications using UDP (like video calls) handle this at the application layer — " +
                "they might reduce video quality, use forward error correction, or simply skip " +
                "lost frames. TCP would retry each one, causing delays instead.",
            isTemplate = false
        )
}
