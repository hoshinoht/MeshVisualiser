package com.example.meshvisualiser.models

import com.google.gson.Gson

/** Parsed pose data from a POSE_UPDATE message. */
data class PoseData(
    val x: Float,
    val y: Float,
    val z: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float
)

/** Serializable message exchanged between peers in the mesh network. */
data class MeshMessage(val type: Int, val senderId: Long, val data: String = "") {
    companion object {
        private val gson = Gson()

        fun fromBytes(bytes: ByteArray): MeshMessage? {
            return try {
                gson.fromJson(String(bytes, Charsets.UTF_8), MeshMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun handshake(localId: Long): MeshMessage {
            return MeshMessage(MessageType.HANDSHAKE.value, localId)
        }

        fun election(localId: Long): MeshMessage {
            return MeshMessage(MessageType.ELECTION.value, localId)
        }

        fun ok(localId: Long): MeshMessage {
            return MeshMessage(MessageType.OK.value, localId)
        }

        fun coordinator(localId: Long, cloudAnchorId: String): MeshMessage {
            return MeshMessage(MessageType.COORDINATOR.value, localId, cloudAnchorId)
        }

        fun deviceInfo(localId: Long, model: String): MeshMessage {
            return MeshMessage(MessageType.DEVICE_INFO.value, localId, model)
        }

        fun dataTcp(localId: Long, payload: String, seqNum: Int): MeshMessage {
            return MeshMessage(MessageType.DATA_TCP.value, localId, "seq|$seqNum|$payload")
        }

        fun dataTcpAck(localId: Long, seqNum: Int): MeshMessage {
            return MeshMessage(MessageType.DATA_TCP.value, localId, "ack|$seqNum")
        }

        fun dataUdp(localId: Long, payload: String): MeshMessage {
            return MeshMessage(MessageType.DATA_UDP.value, localId, payload)
        }

        fun poseUpdate(
            localId: Long,
            x: Float,
            y: Float,
            z: Float,
            qx: Float,
            qy: Float,
            qz: Float,
            qw: Float
        ): MeshMessage {
            return MeshMessage(MessageType.POSE_UPDATE.value, localId, "$x,$y,$z,$qx,$qy,$qz,$qw")
        }
    }

    fun toBytes(): ByteArray {
        return gson.toJson(this).toByteArray(Charsets.UTF_8)
    }

    fun getMessageType(): MessageType? = MessageType.fromValue(type)

    /**
     * Parse pose data from POSE_UPDATE message. Supports both legacy 3-component (position only)
     * and full 7-component (position + quaternion) formats.
     *
     * @return PoseData or null if parsing fails
     */
    fun parsePoseData(): PoseData? {
        return try {
            val parts = data.split(",")
            when (parts.size) {
                7 -> PoseData(
                    parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat(),
                    parts[3].toFloat(), parts[4].toFloat(), parts[5].toFloat(), parts[6].toFloat()
                )
                3 -> PoseData(
                    parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat(),
                    0f, 0f, 0f, 1f // identity rotation for backward compat
                )
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
