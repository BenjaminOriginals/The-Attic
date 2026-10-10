package com.focuslockout.data

/** Product rules shared by the UI, the services and the challenges. */
object LockPolicy {
    const val MIN_DURATION_MINUTES = 10
    const val MAX_DURATION_MINUTES = 120
    const val DURATION_STEP_MINUTES = 5
    const val DEFAULT_DURATION_MINUTES = 30

    /** How long a blocked app stays reachable after its challenge is passed, before the lock re-arms. */
    const val GRACE_PERIOD_MINUTES = 5
    const val GRACE_PERIOD_MILLIS = GRACE_PERIOD_MINUTES * 60_000L

    const val SHAKE_TARGET = 100
    const val MATH_STREAK_TARGET = 5

    /** Number of discrete stops between the slider ends (Material's `steps` excludes both ends). */
    const val DURATION_SLIDER_STEPS =
        (MAX_DURATION_MINUTES - MIN_DURATION_MINUTES) / DURATION_STEP_MINUTES - 1

    fun clampDuration(minutes: Int): Int = minutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
}
