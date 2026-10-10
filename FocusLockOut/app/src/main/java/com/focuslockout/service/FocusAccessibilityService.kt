package com.focuslockout.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focuslockout.data.DataStoreRepository
import com.focuslockout.data.LockState
import com.focuslockout.focusRepository
import com.focuslockout.overlay.OverlayActivity
import com.focuslockout.util.applicationLabelOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detects the foreground app and throws the lock overlay over anything on the block list.
 *
 * Primary signal: `TYPE_WINDOW_STATE_CHANGED` events (instant). Backup signal: a watchdog that polls
 * [ForegroundAppTracker] every few seconds while a session is engaged, which catches a blocked app
 * that is still open when its grace period expires, or an event the system never delivered.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var repository: DataStoreRepository
    private lateinit var foregroundTracker: ForegroundAppTracker
    private var powerManager: PowerManager? = null
    private var keyguardManager: KeyguardManager? = null

    /** Latest persisted state, mirrored here so event handling never blocks on disk. */
    @Volatile
    private var lockState = LockState()
    private var protectedPackages: Set<String> = emptySet()
    private var transientPackages: Set<String> = emptySet()
    private var lastForegroundPackage: String? = null
    private var lastLaunchPackage: String? = null
    private var lastLaunchAtElapsed = 0L

    private var stateJob: Job? = null
    private var watchdogJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        repository = focusRepository
        foregroundTracker = ForegroundAppTracker(this)
        powerManager = getSystemService(PowerManager::class.java)
        keyguardManager = getSystemService(KeyguardManager::class.java)
        refreshPackageSets()

        stateJob?.cancel()
        stateJob = scope.launch {
            var wasEngaged = false
            repository.lockState.collect { state ->
                lockState = state
                val now = System.currentTimeMillis()
                if (state.isEngaged(now)) {
                    if (!wasEngaged) {
                        // New session, or one restored after a reboot / process death.
                        refreshPackageSets()
                        LockSessionService.start(this@FocusAccessibilityService, state.sessionEndTime)
                    }
                    wasEngaged = true
                    startWatchdog()
                } else {
                    wasEngaged = false
                    stopWatchdog()
                    if (state.isExpired(now)) repository.endSessionIfExpired(now)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName in transientPackages) return
        lastForegroundPackage = packageName
        evaluate(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isConnected = false
        scope.cancel()
        super.onDestroy()
    }

    private fun evaluate(packageName: String) {
        val state = lockState
        val now = System.currentTimeMillis()
        if (!state.isSessionActive) return
        if (state.isExpired(now)) {
            scope.launch { repository.endSessionIfExpired(now) }
            return
        }
        if (packageName in protectedPackages) return
        if (!state.shouldBlock(packageName, now)) return
        if (OverlayActivity.isShowing) return

        val elapsed = SystemClock.elapsedRealtime()
        if (packageName == lastLaunchPackage && elapsed - lastLaunchAtElapsed < LAUNCH_DEBOUNCE_MS) return
        lastLaunchPackage = packageName
        lastLaunchAtElapsed = elapsed
        launchOverlay(packageName, state)
    }

    private fun launchOverlay(packageName: String, state: LockState) {
        val label = packageManager.applicationLabelOrNull(packageName) ?: packageName
        val intent = OverlayActivity.blockedAppIntent(this, packageName, label, state.selectedChallengeType)
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to show the lock overlay for $packageName", e)
        }
    }

    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!isDeviceInUse()) continue
                val snapshot = withContext(Dispatchers.Default) { foregroundTracker.snapshot() }
                val foreground = when (snapshot) {
                    ForegroundSnapshot.Unavailable -> lastForegroundPackage
                    is ForegroundSnapshot.Known -> snapshot.packageName
                } ?: continue
                evaluate(foreground)
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private fun isDeviceInUse(): Boolean =
        powerManager?.isInteractive != false && keyguardManager?.isKeyguardLocked != true

    private fun refreshPackageSets() {
        protectedPackages = ProtectedPackages.resolve(this)
        transientPackages = ProtectedPackages.transientPackages(this)
    }

    companion object {
        private const val TAG = "FocusA11yService"
        private const val WATCHDOG_INTERVAL_MS = 3_000L
        private const val LAUNCH_DEBOUNCE_MS = 750L

        /** True while the system has this service bound (it runs in the app's process). */
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
