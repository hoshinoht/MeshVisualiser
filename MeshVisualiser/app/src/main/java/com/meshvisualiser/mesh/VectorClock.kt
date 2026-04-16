package com.meshvisualiser.mesh

/**
 * Immutable vector clock for causal ordering in a distributed system.
 *
 * Each peer maintains a map of peerId -> logical counter. The rules:
 * - Internal event: increment own entry
 * - Send: increment own entry, attach full clock to message
 * - Receive: element-wise max with received clock, then increment own entry
 *
 * Comparison determines causal relationships:
 * - HAPPENED_BEFORE: every entry in A <= corresponding entry in B, at least one strictly less
 * - HAPPENED_AFTER: inverse of HAPPENED_BEFORE
 * - CONCURRENT: neither happened-before the other
 * - IDENTICAL: all entries are equal
 */
data class VectorClock(val entries: Map<Long, Int> = emptyMap()) {

    /** Increment the local peer's counter. */
    fun increment(localId: Long): VectorClock {
        val current = entries[localId] ?: 0
        return VectorClock(entries + (localId to current + 1))
    }

    /** Merge with a received clock (element-wise max), then increment local. */
    fun mergeAndIncrement(localId: Long, received: VectorClock): VectorClock {
        val allKeys = entries.keys + received.entries.keys
        val merged = allKeys.associateWith { key ->
            maxOf(entries[key] ?: 0, received.entries[key] ?: 0)
        }
        val local = (merged[localId] ?: 0) + 1
        return VectorClock(merged + (localId to local))
    }

    /** Compare two vector clocks to determine causal relationship. */
    fun compareTo(other: VectorClock): CausalRelation {
        val allKeys = this.entries.keys + other.entries.keys
        if (allKeys.isEmpty()) return CausalRelation.IDENTICAL

        var hasLess = false
        var hasGreater = false

        for (key in allKeys) {
            val a = this.entries[key] ?: 0
            val b = other.entries[key] ?: 0
            if (a < b) hasLess = true
            if (a > b) hasGreater = true
            if (hasLess && hasGreater) return CausalRelation.CONCURRENT
        }

        return when {
            !hasLess && !hasGreater -> CausalRelation.IDENTICAL
            hasLess && !hasGreater -> CausalRelation.HAPPENED_BEFORE
            !hasLess && hasGreater -> CausalRelation.HAPPENED_AFTER
            else -> CausalRelation.CONCURRENT // both true — already returned above
        }
    }

    /** Compact display: "3·5·2" in a stable peer order. */
    fun toCompactString(): String {
        if (entries.isEmpty()) return "0"
        return entries.toSortedMap().values.joinToString("·")
    }

    /** Get the counter for a specific peer. */
    operator fun get(peerId: Long): Int = entries[peerId] ?: 0

    /** Convert to a map suitable for JSON serialization in MeshMessage. */
    fun toMap(): Map<Long, Int> = entries
}

enum class CausalRelation {
    HAPPENED_BEFORE,  // this → other (this caused other)
    HAPPENED_AFTER,   // other → this (other caused this)
    CONCURRENT,       // neither caused the other
    IDENTICAL         // same clock state
}
