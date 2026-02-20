package com.example.meshvisualiser.simulation

import kotlinx.coroutines.delay

enum class CsmaState { IDLE, SENSING, TRANSMITTING, COLLISION, BACKOFF, SUCCESS }

data class CsmacdState(
    val currentState: CsmaState = CsmaState.IDLE,
    val collisionCount: Int = 0,
    val backoffSlots: Int = 0,
    val backoffRemainingMs: Long = 0L,
    val mediumBusy: Boolean = false,
    val currentStep: String = "Idle"
)

class CsmacdSimulator(
    private val onStateChanged: (CsmacdState) -> Unit
) {
    companion object {
        private const val SENSING_DURATION_MS = 500L
        private const val TRANSMIT_DURATION_MS = 300L
        private const val JAM_DURATION_MS = 300L
        private const val SLOT_TIME_MS = 200L
        private const val MAX_RETRIES = 10
        private const val BASE_COLLISION_PROB = 0.15
    }

    private var state = CsmacdState()

    /**
     * Run the CSMA/CD simulation. Suspends until transmission succeeds or max retries.
     * @param peerCount number of active peers (affects collision probability)
     * @param onTransmitReady callback when medium is acquired and ready to send
     * @return true if transmission succeeded
     */
    suspend fun simulateTransmission(peerCount: Int, onTransmitReady: suspend () -> Unit): Boolean {
        var attempt = 0

        while (attempt < MAX_RETRIES) {
            // 1. SENSING
            updateState(CsmacdState(
                currentState = CsmaState.SENSING,
                collisionCount = attempt,
                currentStep = "Sensing medium... (${SENSING_DURATION_MS}ms)",
                mediumBusy = false
            ))
            delay(SENSING_DURATION_MS)

            // 2. Check for collision (simulated)
            val collisionProb = if (attempt < 2) {
                (BASE_COLLISION_PROB * peerCount).coerceAtMost(0.6)
            } else {
                0.0 // After 2 attempts, let it through for UX
            }

            val collision = Math.random() < collisionProb

            if (collision) {
                // COLLISION detected
                attempt++
                updateState(CsmacdState(
                    currentState = CsmaState.COLLISION,
                    collisionCount = attempt,
                    currentStep = "Collision detected! Sending JAM signal...",
                    mediumBusy = true
                ))
                delay(JAM_DURATION_MS)

                // BACKOFF: exponential backoff
                val maxSlots = (1 shl minOf(attempt, 10)) - 1
                val slots = (0..maxSlots).random()
                val backoffMs = slots * SLOT_TIME_MS

                updateState(CsmacdState(
                    currentState = CsmaState.BACKOFF,
                    collisionCount = attempt,
                    backoffSlots = slots,
                    backoffRemainingMs = backoffMs,
                    currentStep = "Backoff: $slots slots (${backoffMs}ms)",
                    mediumBusy = false
                ))

                // Countdown backoff
                var remaining = backoffMs
                while (remaining > 0) {
                    delay(minOf(remaining, 100L))
                    remaining -= 100L
                    updateState(state.copy(backoffRemainingMs = maxOf(remaining, 0)))
                }
            } else {
                // 3. TRANSMITTING
                updateState(CsmacdState(
                    currentState = CsmaState.TRANSMITTING,
                    collisionCount = attempt,
                    currentStep = "Transmitting data...",
                    mediumBusy = true
                ))
                delay(TRANSMIT_DURATION_MS)

                // Actually send
                onTransmitReady()

                // SUCCESS
                updateState(CsmacdState(
                    currentState = CsmaState.SUCCESS,
                    collisionCount = attempt,
                    currentStep = "Transmission successful!",
                    mediumBusy = false
                ))
                delay(500) // Show success briefly

                updateState(CsmacdState(
                    currentState = CsmaState.IDLE,
                    currentStep = "Idle"
                ))
                return true
            }
        }

        // Max retries exhausted
        updateState(CsmacdState(
            currentState = CsmaState.IDLE,
            collisionCount = attempt,
            currentStep = "Transmission failed after $MAX_RETRIES attempts"
        ))
        return false
    }

    private fun updateState(newState: CsmacdState) {
        state = newState
        onStateChanged(newState)
    }
}
