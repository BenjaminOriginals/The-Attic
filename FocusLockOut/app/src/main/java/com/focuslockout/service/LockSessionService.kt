package com.focuslockout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.focuslockout.MainActivity
import com.focuslockout.R
import com.focuslockout.focusRepository
import com.focuslockout.permissions.PermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that owns an armed session: it shows the live countdown notification, ends
 * the session when time runs out, and stops itself as soon as the session is no longer engaged.
 */
class LockSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sessionJob: Job? = null
    private var engagedEndTime = 0L

    private val notificationManager: NotificationManager
        get() = requireNotNull(getSystemService(NotificationManager::class.java))

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels()
        // startForeground() must run promptly after startForegroundService(), before any disk read.
        val hintedEndTime = intent?.getLongExtra(EXTRA_END_TIME, 0L) ?: 0L
        if (!enterForeground(hintedEndTime)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (sessionJob?.isActive != true) {
            sessionJob = scope.launch { followSession() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun followSession() {
        val repository = focusRepository
        repository.lockState.collectLatest { state ->
            val now = System.currentTimeMillis()
            if (!state.isEngaged(now)) {
                if (state.isExpired(now)) repository.endSessionIfExpired(now)
                // Ran to the end (rather than being emergency-unlocked): celebrate it.
                if (engagedEndTime != 0L && now >= engagedEndTime) postCompletionNotification()
                engagedEndTime = 0L
                shutdown()
                return@collectLatest
            }
            engagedEndTime = state.sessionEndTime
            notificationManager.notify(NOTIFICATION_ID, buildSessionNotification(state.sessionEndTime))
            // delay() runs on the monotonic clock while the end time is wall-clock: re-check until due.
            var remaining = state.sessionEndTime - now
            while (remaining > 0) {
                delay(remaining)
                remaining = state.sessionEndTime - System.currentTimeMillis()
            }
            repository.endSessionIfExpired()
        }
    }

    private fun enterForeground(endTime: Long): Boolean = try {
        val notification = buildSessionNotification(endTime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Could not enter the foreground", e)
        false
    }

    private fun shutdown() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildSessionNotification(endTime: Long): Notification {
        val builder = Notification.Builder(this, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setColor(getColor(R.color.neon_green))
            .setContentTitle(getString(R.string.notification_session_title))
            .setContentText(getString(R.string.notification_session_text))
            .setContentIntent(openAppIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (endTime > System.currentTimeMillis()) {
            // The system renders the countdown itself; no need to re-post every second.
            builder.setWhen(endTime)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun postCompletionNotification() {
        if (!PermissionChecker.hasNotificationPermission(this)) return
        val notification = Notification.Builder(this, CHANNEL_COMPLETE)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setColor(getColor(R.color.neon_green))
            .setContentTitle(getString(R.string.notification_complete_title))
            .setContentText(getString(R.string.notification_complete_text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannels() {
        val manager = notificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION,
                getString(R.string.notification_channel_session),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COMPLETE,
                getString(R.string.notification_channel_complete),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        private const val TAG = "LockSessionService"
        private const val CHANNEL_SESSION = "focus_lock_session"
        private const val CHANNEL_COMPLETE = "focus_lock_complete"
        private const val NOTIFICATION_ID = 0xF0C5
        private const val COMPLETE_NOTIFICATION_ID = 0xF0C6
        private const val EXTRA_END_TIME = "com.focuslockout.extra.END_TIME"

        /**
         * Starts (or refreshes) the session service. Safe to call repeatedly. Background starts can be
         * refused on Android 12+; the lock itself still works because the accessibility service
         * enforces it, only the countdown notification is missing until the app is next opened.
         */
        fun start(context: Context, sessionEndTime: Long) {
            val intent = Intent(context, LockSessionService::class.java)
                .putExtra(EXTRA_END_TIME, sessionEndTime)
            try {
                context.startForegroundService(intent)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Session service start refused", e)
            }
        }
    }
}
