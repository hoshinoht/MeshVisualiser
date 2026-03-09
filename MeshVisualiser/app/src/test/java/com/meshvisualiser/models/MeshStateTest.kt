package com.meshvisualiser.models

import org.junit.Assert.*
import org.junit.Test

class MeshStateTest {

    @Test
    fun `all expected states exist`() {
        val states = MeshState.entries.map { it.name }
        assertTrue(states.contains("DISCOVERING"))
        assertTrue(states.contains("ELECTING"))
        assertTrue(states.contains("CONNECTED"))
        assertEquals(3, states.size)
    }
}
