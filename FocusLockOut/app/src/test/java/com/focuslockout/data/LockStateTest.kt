package com.focuslockout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockStateTest {

    private val start = 1_000_000L
    private val end = start + 30 * 60_000L
    private val armed = LockState(
        isSessionActive = true,
        sessionStartTime = start,
        sessionEndTime = end,
        blockedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
    )

    @Test
    fun engagedOnlyBeforeEndTime() {
        assertTrue(armed.isEngaged(start))
        assertTrue(armed.isEngaged(end - 1))
        assertFalse(armed.isEngaged(end))
        assertTrue(armed.isExpired(end))
        assertFalse(LockState().isEngaged(start))
        assertFalse(LockState().isExpired(start))
    }

    @Test
    fun blocksOnlyListedAppsWhileEngaged() {
        assertTrue(armed.shouldBlock("com.instagram.android", start + 1))
        assertFalse(armed.shouldBlock("com.android.chrome", start + 1))
        assertFalse(armed.shouldBlock("com.instagram.android", end))
        assertFalse(armed.copy(isSessionActive = false).shouldBlock("com.instagram.android", start + 1))
    }

    @Test
    fun gracePeriodTemporarilyAllowsAnApp() {
        val graceUntil = start + LockPolicy.GRACE_PERIOD_MILLIS
        val withGrace = armed.copy(graceExpiries = mapOf("com.instagram.android" to graceUntil))
        assertFalse(withGrace.shouldBlock("com.instagram.android", graceUntil - 1))
        assertTrue(withGrace.shouldBlock("com.instagram.android", graceUntil))
        // Grace is per app.
        assertTrue(withGrace.shouldBlock("com.zhiliaoapp.musically", start + 1))
    }

    @Test
    fun remainingTimeAndFraction() {
        assertEquals(30 * 60_000L, armed.remainingMillis(start))
        assertEquals(0L, armed.remainingMillis(end + 5))
        assertEquals(1f, armed.remainingFraction(start), 0.0001f)
        assertEquals(0.5f, armed.remainingFraction(start + 15 * 60_000L), 0.0001f)
        assertEquals(0f, armed.remainingFraction(end))
        assertEquals(0f, LockState().remainingFraction(start))
    }

    @Test
    fun elapsedMinutesForShameText() {
        assertEquals(0, armed.elapsedMinutes(start + 59_999))
        assertEquals(3, armed.elapsedMinutes(start + 3 * 60_000L + 10))
        assertEquals(0, LockState().elapsedMinutes(start))
    }

    @Test
    fun graceCodecRoundTrips() {
        val entries = mapOf("com.instagram.android" to 123L, "com.reddit.frontpage" to 456L)
        assertEquals(entries, GraceCodec.decode(GraceCodec.encode(entries)))
    }

    @Test
    fun graceCodecSkipsMalformedEntries() {
        val decoded = GraceCodec.decode(setOf("com.ok.app|99", "garbage", "|12", "com.bad.app|notanumber"))
        assertEquals(mapOf("com.ok.app" to 99L), decoded)
    }

    @Test
    fun challengeTypeFallsBackToDefault() {
        assertEquals(ChallengeType.MATH, ChallengeType.fromKey("MATH"))
        assertEquals(ChallengeType.DEFAULT, ChallengeType.fromKey(null))
        assertEquals(ChallengeType.DEFAULT, ChallengeType.fromKey("JUGGLING"))
    }

    @Test
    fun sliderStepsLandOnFiveMinuteStops() {
        val stops = LockPolicy.DURATION_SLIDER_STEPS + 2
        val span = LockPolicy.MAX_DURATION_MINUTES - LockPolicy.MIN_DURATION_MINUTES
        assertEquals(LockPolicy.DURATION_STEP_MINUTES, span / (stops - 1))
        assertEquals(10, LockPolicy.clampDuration(1))
        assertEquals(120, LockPolicy.clampDuration(500))
    }
}
