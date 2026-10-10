package com.focuslockout.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.focuslockout.service.FocusAccessibilityService

data class PermissionStatus(
    val accessibilityEnabled: Boolean = false,
    /** Enabled in Settings *and* actually bound by the system right now. */
    val accessibilityConnected: Boolean = false,
    val overlayGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
) {
    val allRequiredGranted: Boolean
        get() = accessibilityEnabled && overlayGranted && usageAccessGranted

    val anyMissing: Boolean
        get() = !allRequiredGranted || !notificationsGranted
}

/** Reads the special-access grants the lock depends on and builds the Settings intents to fix them. */
object PermissionChecker {

    fun currentStatus(context: Context) = PermissionStatus(
        accessibilityEnabled = isAccessibilityServiceEnabled(context),
        accessibilityConnected = FocusAccessibilityService.isConnected,
        overlayGranted = Settings.canDrawOverlays(context),
        usageAccessGranted = hasUsageStatsAccess(context),
        notificationsGranted = hasNotificationPermission(context),
    )

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, FocusAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
    }

    fun hasUsageStatsAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        // checkOpNoThrow works on every supported level (API 36 deprecates the unsafe* variant instead).
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** App info page: where Android 13+ hides "Allow restricted settings" for sideloaded apps. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Opens a Settings screen, falling back to the Settings root on builds that lack it. */
    fun openSettings(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
