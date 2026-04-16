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

    fun nthTcpRetransmission(peerModel: String, count: Int): NarratorMessage =
        NarratorMessage(
            title = "Retransmission #$count",
            explanation = "Another retry to $peerModel. TCP keeps trying because reliability " +
                "is its core promise — every byte arrives, in order, or the sender is notified."
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
            explanation = "Fired and forgot to $peerModel. " +
                "UDP doesn't wait for confirmation — it's faster but unreliable. " +
                "If this packet is lost, nobody will know."
        )

    fun nthUdpDrop(count: Int): NarratorMessage =
        NarratorMessage(
            title = "UDP Drop #$count",
            explanation = "Another packet vanished. UDP has no recovery mechanism — " +
                "applications must tolerate loss or add their own error correction."
        )

    // --- CSMA/CD Events ---

    fun firstCollision(peerCount: Int): NarratorMessage =
        NarratorMessage(
            title = "CSMA/CD Collision!",
            explanation = "Two devices tried to transmit at the same time on the shared medium. " +
                "With $peerCount peers, collisions are expected. The CSMA/CD protocol detects this " +
                "and both devices back off for a random time before retrying."
        )

    fun nthCollision(count: Int): NarratorMessage =
        NarratorMessage(
            title = "Collision #$count",
            explanation = "The shared medium is getting busy. More collisions mean " +
                "longer backoff waits and lower effective throughput for everyone."
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

    fun reElectionStarted(peerCount: Int): NarratorMessage =
        NarratorMessage(
            title = "Re-election Triggered",
            explanation = "A topology change (peer joined or left) triggered a new election " +
                "among ${peerCount + 1} devices. The Bully Algorithm re-runs to find the new " +
                "highest-ID coordinator."
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

    // --- Consensus / Split Brain ---

    fun staleCoordinatorRejected(senderId: Long, senderTerm: Int, localTerm: Int): NarratorMessage =
        NarratorMessage(
            title = "Stale Leader Rejected",
            explanation = "Device ${senderId.toString().takeLast(4)} claimed leadership with term $senderTerm, " +
                "but our current term is $localTerm. The claim was rejected — this prevents " +
                "'split brain' where two nodes think they're both the leader. " +
                "Term numbers ensure only the latest election result is accepted."
        )

    fun configReplicated(followerCount: Int): NarratorMessage =
        NarratorMessage(
            title = "Configuration Replicated",
            explanation = "The leader replicated network settings (drop rates, timeouts) to $followerCount follower(s). " +
                "This is state replication — the leader maintains a consistent configuration across all nodes. " +
                "In distributed databases, the same pattern keeps replicas in sync."
        )

    fun coordinatorTimeout(): NarratorMessage =
        NarratorMessage(
            title = "Coordinator Timeout",
            explanation = "No COORDINATOR message arrived after receiving OK. " +
                "This is a failure detector in action — if the expected leader doesn't announce " +
                "within the timeout window, a new election starts. Distributed systems use " +
                "timeouts as the primary mechanism to detect unresponsive nodes."
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
