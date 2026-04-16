package com.meshvisualiser.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageTypeInstrumentedTest {

    @Test
    fun fromValue_returns_correct_enum_for_all_values() {
        assertEquals(MessageType.HANDSHAKE, MessageType.fromValue(0))
        assertEquals(MessageType.ELECTION, MessageType.fromValue(1))
        assertEquals(MessageType.OK, MessageType.fromValue(2))
        assertEquals(MessageType.COORDINATOR, MessageType.fromValue(3))
        assertEquals(MessageType.POSE_UPDATE, MessageType.fromValue(4))
        assertEquals(MessageType.DEVICE_INFO, MessageType.fromValue(5))
        assertEquals(MessageType.DATA_TCP, MessageType.fromValue(6))
        assertEquals(MessageType.DATA_UDP, MessageType.fromValue(7))
        assertEquals(MessageType.START_MESH, MessageType.fromValue(8))
        assertEquals(MessageType.CONFIG_SYNC, MessageType.fromValue(9))
        assertEquals(MessageType.ANIM_EVENT, MessageType.fromValue(10))
    }

    @Test
    fun fromValue_returns_null_for_unknown() {
        assertNull(MessageType.fromValue(-1))
        assertNull(MessageType.fromValue(99))
    }

    @Test
    fun all_values_are_unique() {
        val values = MessageType.entries.map { it.value }
        assertEquals(values.size, values.toSet().size)
    }
}
