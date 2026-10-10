package com.focuslockout.util

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/** [PackageManager.queryIntentActivities] without the API 33 flags-object deprecation noise. */
fun PackageManager.queryActivitiesCompat(intent: Intent, flags: Int = 0): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, flags)
    }

/** User-facing label of an installed app, or null when the package is gone or not visible to us. */
fun PackageManager.applicationLabelOrNull(packageName: String): String? = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, 0)
    }
    getApplicationLabel(info).toString()
} catch (e: PackageManager.NameNotFoundException) {
    null
}
