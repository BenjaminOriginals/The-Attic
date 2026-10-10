package com.focuslockout.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    private val gravity = 9.81f
    private val sampleIntervalNanos = 20_000_000L // 50 Hz, SENSOR_DELAY_GAME

    @Test
    fun stillPhoneNeverShakes() {
        val detector = ShakeDetector()
        var shakes = 0
        // Five seconds lying flat, then five seconds held upright: gravity alone is not a shake.
        for (i in 0 until 250) if (detector.onSample(0f, 0f, gravity, i * sampleIntervalNanos)) shakes++
        for (i in 250 until 500) if (detector.onSample(0f, gravity, 0f, i * sampleIntervalNanos)) shakes++
        assertEquals(0, shakes)
    }

    @Test
    fun vigorousShakingIsCounted() {
        val detector = ShakeDetector()
        var shakes = 0
        // Swing hard along x, reversing direction every 200 ms, for four seconds.
        for (i in 0 until 200) {
            val direction = if ((i / 10) % 2 == 0) 1f else -1f
            if (detector.onSample(25f * direction, 0f, gravity, i * sampleIntervalNanos)) shakes++
        }
        // 20 reversals; each one registers once.
        assertTrue("shakes=$shakes", shakes in 15..21)
    }

    @Test
    fun refractoryWindowCapsTheRate() {
        val detector = ShakeDetector()
        var shakes = 0
        // Reversing every sample (25 Hz) is far faster than the 150 ms refractory window allows.
        for (i in 0 until 50) {
            val direction = if (i % 2 == 0) 1f else -1f
            if (detector.onSample(30f * direction, 0f, gravity, i * sampleIntervalNanos)) shakes++
        }
        // One second of samples can yield at most ceil(1000 / 150) = 7 shakes.
        assertTrue("shakes=$shakes", shakes in 1..7)
    }

    @Test
    fun resetForgetsHistory() {
        val detector = ShakeDetector()
        detector.onSample(0f, 0f, gravity, 0L)
        detector.reset()
        // First sample after reset only seeds gravity.
        assertEquals(false, detector.onSample(40f, 0f, gravity, sampleIntervalNanos))
    }
}
