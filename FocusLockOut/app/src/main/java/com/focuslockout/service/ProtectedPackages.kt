package com.focuslockout.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.focuslockout.util.queryActivitiesCompat

/** Packages the lock must never interfere with, no matter what is on the block list. */
object ProtectedPackages {

    private val ALWAYS_ALLOWED = setOf(
        "android",
        "com.android.systemui",
        "com.android.intentresolver",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
    )

    /** System chrome, launchers, the dialer (emergency calls) and this app itself. */
    fun resolve(context: Context): Set<String> {
        val result = ALWAYS_ALLOWED.toMutableSet()
        result += context.packageName
        result += homePackages(context.packageManager)
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { result += it }
        return result
    }

    /**
     * Windows that appear *on top of* the real foreground app (keyboards, the notification shade).
     * Their window events must not be mistaken for the user switching apps.
     */
    fun transientPackages(context: Context): Set<String> {
        val imes = context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            .orEmpty()
        return imes.toSet() + "com.android.systemui"
    }

    private fun homePackages(pm: PackageManager): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryActivitiesCompat(home, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }
}
