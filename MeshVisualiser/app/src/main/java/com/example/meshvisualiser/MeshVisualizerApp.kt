package com.example.meshvisualiser

import android.app.Application

class MeshVisualizerApp : Application() {
    
    companion object {
        // Unique service ID for Nearby Connections discovery
        const val SERVICE_ID = "com.example.meshvisualiser.nearby"
        
        // Timeouts
        const val ELECTION_TIMEOUT_MS = 2000L
        const val MESH_FORMATION_TIMEOUT_MS = 15000L
        const val POSE_BROADCAST_INTERVAL_MS = 33L // ~30fps
        
        // Cloud Anchor TTL (in days)
        const val CLOUD_ANCHOR_TTL_DAYS = 1

        /** Build a group-specific service ID so only devices in the same group discover each other. */
        fun serviceIdForGroup(groupCode: String): String = "$SERVICE_ID.$groupCode"
    }
    
    override fun onCreate() {
        super.onCreate()
    }
}
