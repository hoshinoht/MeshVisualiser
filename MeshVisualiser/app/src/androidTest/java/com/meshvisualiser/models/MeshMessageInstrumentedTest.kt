package com.meshvisualiser.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeshMessageInstrumentedTest {

    private val testId = 99999L

    @Test
    fun roundtrip_handshake() {
        val original = MeshMessage.handshake(testId)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.HANDSHAKE.value, restored!!.type)
        assertEquals(testId, restored.senderId)
    }

    @Test
    fun roundtrip_coordinator_with_data() {
        val anchorId = "anchor-abc"
        val original = MeshMessage.coordinator(testId, anchorId)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(anchorId, restored!!.data)
    }

    @Test
    fun roundtrip_poseUpdate() {
        val original = MeshMessage.poseUpdate(testId, 1f, 2f, 3f, 0.1f, 0.2f, 0.3f, 0.9f)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        val pose = restored!!.parsePoseData()
        assertNotNull(pose)
        assertEquals(1f, pose!!.x, 0.001f)
        assertEquals(0.9f, pose.qw, 0.001f)
    }

    @Test
    fun parsePoseData_3components_identity_quaternion() {
        val msg = MeshMessage(MessageType.POSE_UPDATE.value, testId, "4.0,5.0,6.0")
        val pose = msg.parsePoseData()
        assertNotNull(pose)
        assertEquals(4f, pose!!.x, 0.001f)
        assertEquals(1f, pose.qw, 0.001f)
    }

    @Test
    fun fromBytes_invalidJson_returnsNull() {
        assertNull(MeshMessage.fromBytes("garbage".toByteArray()))
    }

    @Test
    fun fromBytes_emptyBytes_returnsNull() {
        assertNull(MeshMessage.fromBytes(ByteArray(0)))
    }

    @Test
    fun dataTcp_format() {
        val msg = MeshMessage.dataTcp(testId, "payload", 7)
        assertEquals("seq|7|payload", msg.data)
    }

    @Test
    fun dataTcpAck_format() {
        val msg = MeshMessage.dataTcpAck(testId, 7)
        assertEquals("ack|7", msg.data)
    }
}
