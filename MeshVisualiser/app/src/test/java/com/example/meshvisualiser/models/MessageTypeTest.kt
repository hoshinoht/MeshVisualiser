package com.example.meshvisualiser.models

import org.junit.Assert.*
import org.junit.Test

class MessageTypeTest {

    @Test
    fun `fromValue returns correct enum for each known value`() {
        assertEquals(MessageType.HANDSHAKE, MessageType.fromValue(0))
        assertEquals(MessageType.ELECTION, MessageType.fromValue(1))
        assertEquals(MessageType.OK, MessageType.fromValue(2))
        assertEquals(MessageType.COORDINATOR, MessageType.fromValue(3))
        assertEquals(MessageType.POSE_UPDATE, MessageType.fromValue(4))
        assertEquals(MessageType.DEVICE_INFO, MessageType.fromValue(5))
        assertEquals(MessageType.DATA_TCP, MessageType.fromValue(6))
        assertEquals(MessageType.DATA_UDP, MessageType.fromValue(7))
        assertEquals(MessageType.START_MESH, MessageType.fromValue(8))
    }

    @Test
    fun `fromValue returns null for unknown value`() {
        assertNull(MessageType.fromValue(99))
        assertNull(MessageType.fromValue(-1))
        assertNull(MessageType.fromValue(100))
    }

    @Test
    fun `all values are unique`() {
        val values = MessageType.entries.map { it.value }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun `value assignments match spec 0 through 8`() {
        val expected = (0..8).toList()
        val actual = MessageType.entries.map { it.value }.sorted()
        assertEquals(expected, actual)
    }
}
