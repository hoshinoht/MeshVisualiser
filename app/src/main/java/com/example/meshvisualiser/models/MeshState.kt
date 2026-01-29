package com.example.meshvisualiser.models

/**
 * Represents the current state of the mesh network.
 */
enum class MeshState {
    /** Initial state - discovering nearby devices */
    DISCOVERING,

    /** Running the Bully Algorithm to elect a leader */
    ELECTING,

    /** Fully connected — local anchor placed, exchanging poses */
    CONNECTED
}
