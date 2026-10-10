package com.focuslockout.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatsHoursMinutesSeconds() {
        assertEquals("02:00:00", TimeFormat.countdown(120 * 60_000L))
        assertEquals("00:45:00", TimeFormat.countdown(45 * 60_000L))
        assertEquals("01:01:01", TimeFormat.countdown(3_661_000L))
    }

    @Test
    fun roundsUpPartialSeconds() {
        assertEquals("00:00:01", TimeFormat.countdown(1L))
        assertEquals("00:00:01", TimeFormat.countdown(999L))
        assertEquals("00:00:00", TimeFormat.countdown(0L))
        assertEquals("00:00:00", TimeFormat.countdown(-5_000L))
    }
}
