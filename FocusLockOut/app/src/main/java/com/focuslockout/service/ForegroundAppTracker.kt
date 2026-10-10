package com.focuslockout.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.focuslockout.permissions.PermissionChecker

sealed interface ForegroundSnapshot {
    /** Usage access is not granted, so this signal is unavailable. */
    data object Unavailable : ForegroundSnapshot

    /** [packageName] is the app with a resumed activity, or null when none is (e.g. on the lock screen). */
    data class Known(val packageName: String?) : ForegroundSnapshot
}

/**
 * Second, independent foreground-app signal built on [UsageStatsManager]. The accessibility
 * service reacts to window events instantly; this tracker lets its watchdog catch anything those
 * events miss (a blocked app still open when its grace period runs out, a dropped event, ...).
 *
 * Events are read incrementally from the last query, so each poll only scans a few seconds of data.
 */
class ForegroundAppTracker(context: Context) {

    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)
    private var lastQueryEnd = 0L
    private var foregroundPackage: String? = null

    @Synchronized
    fun snapshot(now: Long = System.currentTimeMillis()): ForegroundSnapshot {
        val manager = usageStatsManager
        if (manager == null || !PermissionChecker.hasUsageStatsAccess(appContext)) {
            lastQueryEnd = 0L
            foregroundPackage = null
            return ForegroundSnapshot.Unavailable
        }

        // Re-read a small overlap: events are sometimes committed a moment after they happen.
        val begin = if (lastQueryEnd == 0L) now - INITIAL_LOOKBACK_MS else lastQueryEnd - OVERLAP_MS
        val events = try {
            manager.queryEvents(begin, now)
        } catch (e: SecurityException) {
            Log.w(TAG, "Usage access revoked mid-query", e)
            return ForegroundSnapshot.Unavailable
        } ?: return ForegroundSnapshot.Known(foregroundPackage)

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_ACTIVITY_RESUMED -> foregroundPackage = event.packageName
                EVENT_ACTIVITY_PAUSED -> if (event.packageName == foregroundPackage) foregroundPackage = null
            }
        }
        lastQueryEnd = now
        return ForegroundSnapshot.Known(foregroundPackage)
    }

    private companion object {
        const val TAG = "ForegroundAppTracker"
        const val INITIAL_LOOKBACK_MS = 60 * 60 * 1000L
        const val OVERLAP_MS = 2_000L

        // UsageEvents.Event.ACTIVITY_RESUMED / ACTIVITY_PAUSED (API 29) share their values with the
        // older MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND, so these work on every supported API level.
        const val EVENT_ACTIVITY_RESUMED = 1
        const val EVENT_ACTIVITY_PAUSED = 2
    }
}
