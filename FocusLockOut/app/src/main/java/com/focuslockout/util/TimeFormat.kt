package com.focuslockout.util

import java.util.Locale

object TimeFormat {
    /**
     * Formats a countdown as `HH:MM:SS`. Seconds round up so the display reads 00:00:01 during the
     * final second and only hits 00:00:00 when the session is actually over.
     */
    fun countdown(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
