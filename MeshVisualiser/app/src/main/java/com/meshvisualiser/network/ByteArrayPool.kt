package com.meshvisualiser.network

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe object pool for [ByteArray] buffers used during mesh message serialization.
 *
 * Every call to [MeshMessage.toBytes()] allocates a new ByteArray via Gson → String → UTF-8 encode.
 * On the pose-broadcast hot path (every frame × N peers), this creates significant GC pressure.
 *
 * This pool recycles fixed-capacity buffers so the serialization path can write into a
 * pre-allocated array instead of allocating a fresh one each time.
 *
 * Usage:
 * ```
 * val buf = ByteArrayPool.acquire()   // may return a recycled buffer
 * // ... write into buf ...
 * ByteArrayPool.release(buf)          // return to pool for reuse
 * ```
 *
 * Buffers larger than [MAX_POOLED_SIZE] are not pooled (let GC handle outliers).
 * The pool holds at most [MAX_POOL_SIZE] buffers to bound memory.
 */
object ByteArrayPool {
    /** Default buffer capacity — covers the vast majority of MeshMessage JSON payloads. */
    const val DEFAULT_CAPACITY = 512

    /** Buffers above this size are not returned to the pool. */
    private const val MAX_POOLED_SIZE = 4096

    /** Maximum number of buffers retained in the pool. */
    private const val MAX_POOL_SIZE = 32

    private val pool = ConcurrentLinkedQueue<ByteArray>()

    /** Acquire a buffer of at least [minCapacity] bytes. May return a recycled buffer. */
    fun acquire(minCapacity: Int = DEFAULT_CAPACITY): ByteArray {
        // Try to reuse a pooled buffer that is large enough
        val buf = pool.poll()
        return if (buf != null && buf.size >= minCapacity) buf else ByteArray(maxOf(minCapacity, DEFAULT_CAPACITY))
    }

    /** Return a buffer to the pool for reuse. Oversized or excess buffers are discarded. */
    fun release(buf: ByteArray) {
        if (buf.size <= MAX_POOLED_SIZE && pool.size < MAX_POOL_SIZE) {
            pool.offer(buf)
        }
    }

    /** Number of buffers currently in the pool (for testing/diagnostics). */
    fun poolSize(): Int = pool.size

    /** Drain all pooled buffers (useful in tests or on cleanup). */
    fun clear() {
        pool.clear()
    }
}
