package com.meshvisualiser.models

/**
 * Represents the current state of the mesh network.
 */
enum class MeshState {
    /** Initial state - discovering nearby devices */
    DISCOVERING,

    /** Running the Bully Algorithm to elect a leader */
    ELECTING,

    /** Leader is hosting / followers are resolving the Cloud Anchor */
    RESOLVING,

    /** Fully connected — shared anchor established, exchanging poses */
    CONNECTED
}
