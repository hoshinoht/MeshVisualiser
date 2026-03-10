package com.meshvisualiser.models

import org.junit.Assert.*
import org.junit.Test

class MeshMessageTest {

    private val testId = 12345L

    // --- Serialization roundtrip ---

    @Test
    fun `handshake roundtrip`() {
        val original = MeshMessage.handshake(testId)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.HANDSHAKE.value, restored!!.type)
        assertEquals(testId, restored.senderId)
    }

    @Test
    fun `election roundtrip`() {
        val original = MeshMessage.election(testId)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.ELECTION.value, restored!!.type)
        assertEquals(testId, restored.senderId)
    }

    @Test
    fun `ok roundtrip`() {
        val original = MeshMessage.ok(testId)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.OK.value, restored!!.type)
        assertEquals(testId, restored.senderId)
    }

    @Test
    fun `coordinator roundtrip`() {
        val anchorId = "cloud-anchor-123"
        val original = MeshMessage.coordinator(testId, anchorId)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.COORDINATOR.value, restored!!.type)
        assertEquals(testId, restored.senderId)
        assertEquals(anchorId, restored.data)
    }

    @Test
    fun `deviceInfo roundtrip`() {
        val model = "Pixel 7"
        val original = MeshMessage.deviceInfo(testId, model)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.DEVICE_INFO.value, restored!!.type)
        assertEquals(model, restored.data)
    }

    @Test
    fun `poseUpdate roundtrip`() {
        val original = MeshMessage.poseUpdate(testId, 1.0f, 2.0f, 3.0f, 0.1f, 0.2f, 0.3f, 0.9f)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.POSE_UPDATE.value, restored!!.type)
        assertEquals(testId, restored.senderId)
    }

    @Test
    fun `startMesh roundtrip`() {
        val original = MeshMessage.startMesh(testId)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.START_MESH.value, restored!!.type)
        assertEquals(testId, restored.senderId)
    }

    @Test
    fun `dataUdp roundtrip`() {
        val payload = "hello-udp"
        val original = MeshMessage.dataUdp(testId, payload)
        val restored = MeshMessage.fromBytes(original.toBytes())
        assertNotNull(restored)
        assertEquals(MessageType.DATA_UDP.value, restored!!.type)
        assertEquals(payload, restored.data)
    }

    // --- Factory method correctness ---

    @Test
    fun `each factory method produces correct type and senderId`() {
        assertEquals(MessageType.HANDSHAKE.value, MeshMessage.handshake(testId).type)
        assertEquals(MessageType.ELECTION.value, MeshMessage.election(testId).type)
        assertEquals(MessageType.OK.value, MeshMessage.ok(testId).type)
        assertEquals(MessageType.COORDINATOR.value, MeshMessage.coordinator(testId, "").type)
        assertEquals(MessageType.DEVICE_INFO.value, MeshMessage.deviceInfo(testId, "").type)
        assertEquals(MessageType.DATA_TCP.value, MeshMessage.dataTcp(testId, "p", 1).type)
        assertEquals(MessageType.DATA_UDP.value, MeshMessage.dataUdp(testId, "p").type)
        assertEquals(MessageType.START_MESH.value, MeshMessage.startMesh(testId).type)
        assertEquals(MessageType.POSE_UPDATE.value, MeshMessage.poseUpdate(testId, 0f, 0f, 0f, 0f, 0f, 0f, 1f).type)
    }

    // --- getMessageType ---

    @Test
    fun `getMessageType returns correct enum`() {
        assertEquals(MessageType.HANDSHAKE, MeshMessage.handshake(testId).getMessageType())
        assertEquals(MessageType.ELECTION, MeshMessage.election(testId).getMessageType())
        assertEquals(MessageType.COORDINATOR, MeshMessage.coordinator(testId, "").getMessageType())
    }

    // --- parsePoseData ---

    @Test
    fun `parsePoseData with 7 components`() {
        val msg = MeshMessage.poseUpdate(testId, 1.0f, 2.0f, 3.0f, 0.1f, 0.2f, 0.3f, 0.9f)
        val pose = msg.parsePoseData()
        assertNotNull(pose)
        assertEquals(1.0f, pose!!.x, 0.001f)
        assertEquals(2.0f, pose.y, 0.001f)
        assertEquals(3.0f, pose.z, 0.001f)
        assertEquals(0.1f, pose.qx, 0.001f)
        assertEquals(0.2f, pose.qy, 0.001f)
        assertEquals(0.3f, pose.qz, 0.001f)
        assertEquals(0.9f, pose.qw, 0.001f)
    }

    @Test
    fun `parsePoseData with 3 components uses identity quaternion`() {
        val msg = MeshMessage(MessageType.POSE_UPDATE.value, testId, "1.0,2.0,3.0")
        val pose = msg.parsePoseData()
        assertNotNull(pose)
        assertEquals(1.0f, pose!!.x, 0.001f)
        assertEquals(2.0f, pose.y, 0.001f)
        assertEquals(3.0f, pose.z, 0.001f)
        assertEquals(0f, pose.qx, 0.001f)
        assertEquals(0f, pose.qy, 0.001f)
        assertEquals(0f, pose.qz, 0.001f)
        assertEquals(1f, pose.qw, 0.001f)
    }

    @Test
    fun `parsePoseData with invalid data returns null`() {
        val msg = MeshMessage(MessageType.POSE_UPDATE.value, testId, "not,a,valid,float")
        assertNull(msg.parsePoseData())
    }

    @Test
    fun `parsePoseData with wrong component count returns null`() {
        val msg = MeshMessage(MessageType.POSE_UPDATE.value, testId, "1.0,2.0")
        assertNull(msg.parsePoseData())

        val msg5 = MeshMessage(MessageType.POSE_UPDATE.value, testId, "1.0,2.0,3.0,4.0,5.0")
        assertNull(msg5.parsePoseData())
    }

    // --- fromBytes edge cases ---

    @Test
    fun `fromBytes with invalid JSON returns null`() {
        assertNull(MeshMessage.fromBytes("not json".toByteArray()))
    }

    @Test
    fun `fromBytes with empty bytes returns null`() {
        assertNull(MeshMessage.fromBytes(ByteArray(0)))
    }

    // --- dataTcp format ---

    @Test
    fun `dataTcp format is seq pipe seqNum pipe payload`() {
        val msg = MeshMessage.dataTcp(testId, "hello", 42)
        assertEquals("seq|42|hello", msg.data)
    }

    @Test
    fun `dataTcpAck format is ack pipe seqNum`() {
        val msg = MeshMessage.dataTcpAck(testId, 42)
        assertEquals("ack|42", msg.data)
    }
}
