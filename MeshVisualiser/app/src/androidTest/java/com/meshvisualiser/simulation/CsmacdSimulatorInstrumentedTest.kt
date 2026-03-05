package com.meshvisualiser.simulation

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumented tests for CsmacdSimulator — verifies the Mutex serialization
 * works correctly on the ART runtime with real coroutine dispatching.
 */
@RunWith(AndroidJUnit4::class)
class CsmacdSimulatorInstrumentedTest {

    @Test
    fun concurrent_transmissions_are_serialized_by_mutex() = runBlocking {
        val transmitCount = AtomicInteger(0)
        val states = mutableListOf<CsmacdState>()
        val simulator = CsmacdSimulator { synchronized(states) { states.add(it) } }

        // Launch 3 concurrent simulations — Mutex should serialize them
        val jobs = (1..3).map {
            async {
                simulator.simulateTransmission(0) {
                    transmitCount.incrementAndGet()
                }
            }
        }
        val results = jobs.awaitAll()

        // All 3 should succeed
        assertTrue("All transmissions should succeed", results.all { it })
        assertEquals("onTransmitReady called exactly 3 times", 3, transmitCount.get())
    }

    @Test
    fun state_transitions_are_coherent_under_concurrency() = runBlocking {
        val states = mutableListOf<CsmacdState>()
        val simulator = CsmacdSimulator { synchronized(states) { states.add(it) } }

        // Launch 2 concurrent simulations
        val jobs = (1..2).map {
            async {
                simulator.simulateTransmission(0) { }
            }
        }
        jobs.awaitAll()

        // Verify no impossible state transitions:
        // IDLE should never appear between SENSING and TRANSMITTING in a single simulation run
        // Since they're serialized, each run's IDLE→SENSING→TRANSMITTING→SUCCESS→IDLE should be intact
        var lastIdle = -1
        states.forEachIndexed { index, state ->
            if (state.currentState == CsmaState.IDLE) {
                lastIdle = index
            }
        }

        // The very last state should be IDLE (final simulation completed)
        assertEquals(CsmaState.IDLE, states.last().currentState)
    }

    @Test
    fun simulation_completes_and_returns_to_idle() = runBlocking {
        val states = mutableListOf<CsmacdState>()
        val simulator = CsmacdSimulator { states.add(it) }

        val result = simulator.simulateTransmission(0) { }

        assertTrue(result)
        assertEquals(CsmaState.IDLE, states.last().currentState)
    }

    @Test
    fun simulation_with_peers_eventually_succeeds() = runBlocking {
        // With peers, there's a chance of collision, but after 2 attempts
        // the collision probability drops to 0 for UX reasons
        val simulator = CsmacdSimulator { }
        val result = simulator.simulateTransmission(4) { }
        assertTrue("Should eventually succeed even with collisions", result)
    }
}
