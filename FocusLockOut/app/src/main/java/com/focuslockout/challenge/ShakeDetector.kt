package com.focuslockout.challenge

import kotlin.math.sqrt

/**
 * Turns raw accelerometer samples (m/s², including gravity) into discrete shakes.
 *
 * Gravity is tracked with a low-pass filter and subtracted, so holding the phone at any angle reads
 * as zero. A shake is a spike of linear acceleration above [thresholdMs2]; a refractory window of
 * [minIntervalNanos] stops one vigorous swing from counting several times.
 */
class ShakeDetector(
    private val thresholdMs2: Float = DEFAULT_THRESHOLD_MS2,
    private val minIntervalNanos: Long = DEFAULT_MIN_INTERVAL_NANOS,
) {
    private val gravity = FloatArray(3)
    private var initialized = false
    private var lastShakeNanos: Long? = null

    /** Feeds one sample; returns true when it registers a new shake. */
    fun onSample(x: Float, y: Float, z: Float, timestampNanos: Long): Boolean {
        if (!initialized) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            initialized = true
            return false
        }
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z

        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)
        if (magnitude < thresholdMs2) return false

        val last = lastShakeNanos
        if (last != null && timestampNanos - last < minIntervalNanos) return false
        lastShakeNanos = timestampNanos
        return true
    }

    fun reset() {
        initialized = false
        lastShakeNanos = null
    }

    companion object {
        const val ALPHA = 0.8f
        const val DEFAULT_THRESHOLD_MS2 = 12f
        const val DEFAULT_MIN_INTERVAL_NANOS = 150_000_000L
    }
}
