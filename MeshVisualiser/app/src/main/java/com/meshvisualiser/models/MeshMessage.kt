package com.meshvisualiser.models

import android.util.Log
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
        private const val MAX_PAYLOAD_BYTES = 4096

        fun fromBytes(bytes: ByteArray): MeshMessage? {
            if (bytes.size > MAX_PAYLOAD_BYTES) return null
            return try {
                gson.fromJson(String(bytes, Charsets.UTF_8), MeshMessage::class.java)
            } catch (e: Exception) {
                Log.w("MeshMessage", "Failed to deserialize ${bytes.size}B payload: ${e.message}")
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

        fun startMesh(localId: Long): MeshMessage {
            return MeshMessage(MessageType.START_MESH.value, localId)
        }

        /** Sync simulation config to all peers. Data format: "udpDrop|tcpDrop|tcpTimeoutMs" */
        fun configSync(localId: Long, udpDrop: Float, tcpDrop: Float, tcpTimeoutMs: Long): MeshMessage {
            return MeshMessage(MessageType.CONFIG_SYNC.value, localId, "$udpDrop|$tcpDrop|$tcpTimeoutMs")
        }

        fun animEvent(localId: Long, fromId: Long, toId: Long, type: String): MeshMessage {
            return MeshMessage(MessageType.ANIM_EVENT.value, localId, "$fromId:$toId:$type")
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
            val data = "$x,$y,$z,$qx,$qy,$qz,$qw"
            return MeshMessage(MessageType.POSE_UPDATE.value, localId, data)
        }
    }

    fun toBytes(): ByteArray {
        return gson.toJson(this).toByteArray(Charsets.UTF_8)
    }

    fun getMessageType(): MessageType? = MessageType.fromValue(type)

    /**
     * Parse pose data from POSE_UPDATE message. Supports both legacy 3-component (position only)
     * and full 7-component (position + quaternion) formats.
     * Uses index-based parsing to avoid split() array allocation on network hot path.
     *
     * @return PoseData or null if parsing fails
     */
    fun parsePoseData(): PoseData? {
        return try {
            val floats = FloatArray(7)
            var start = 0
            var idx = 0
            for (i in data.indices) {
                if (data[i] == ',') {
                    floats[idx++] = data.substring(start, i).toFloat()
                    start = i + 1
                    if (idx >= 7) break
                }
            }
            // Last segment (no trailing comma)
            if (idx < 7 && start < data.length) {
                floats[idx++] = data.substring(start).toFloat()
            }
            when (idx) {
                7 -> PoseData(
                    floats[0], floats[1], floats[2],
                    floats[3], floats[4], floats[5], floats[6]
                )
                3 -> PoseData(
                    floats[0], floats[1], floats[2],
                    0f, 0f, 0f, 1f // identity rotation for backward compat
                )
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
