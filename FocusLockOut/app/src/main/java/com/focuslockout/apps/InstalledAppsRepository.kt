package com.focuslockout.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.focuslockout.service.ProtectedPackages
import com.focuslockout.util.queryActivitiesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Preinstalled and never updated: hidden by default (updated ones such as YouTube are shown). */
    val isSystem: Boolean,
)

/** Reads launchable apps from [PackageManager] for the block list. */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val protectedPackages = ProtectedPackages.resolve(appContext)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryActivitiesCompat(launcherIntent)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (appInfo.packageName in protectedPackages) return@mapNotNull null
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    isSystem = appInfo.isUntouchedSystemApp(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** App icon rendered at a fixed size so a long list never holds full-resolution bitmaps. */
    suspend fun loadIcon(packageName: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun ApplicationInfo.isUntouchedSystemApp(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
}
