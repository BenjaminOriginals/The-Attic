package com.focuslockout.ui.apps

import android.app.Application
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslockout.apps.InstalledApp
import com.focuslockout.apps.InstalledAppsRepository
import com.focuslockout.focusRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppRow(
    val app: InstalledApp,
    val blocked: Boolean,
    /** Blocked during an engaged session: it cannot be removed until the session ends. */
    val locked: Boolean,
)

data class AppSelectionUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val showSystemApps: Boolean = false,
    val sessionEngaged: Boolean = false,
    val blockedCount: Int = 0,
    val rows: List<AppRow> = emptyList(),
)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.focusRepository
    private val appsRepository = InstalledAppsRepository(application)
    private val iconSizePx = (48 * application.resources.displayMetrics.density).toInt()
    private val iconCache = LruCache<String, ImageBitmap>(ICON_CACHE_SIZE)

    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private val showSystemApps = MutableStateFlow(false)

    private val _unblockRefused = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when the user tries to un-block an app mid-session. */
    val unblockRefused: SharedFlow<Unit> = _unblockRefused.asSharedFlow()

    val uiState: StateFlow<AppSelectionUiState> =
        combine(installedApps, repository.lockState, query, showSystemApps) { apps, lock, search, showSystem ->
            val engaged = lock.isEngaged(System.currentTimeMillis())
            val needle = search.trim()
            val rows = apps.orEmpty()
                .asSequence()
                .filter { !it.isSystem || showSystem || it.packageName in lock.blockedPackages }
                .filter {
                    needle.isEmpty() ||
                        it.label.contains(needle, ignoreCase = true) ||
                        it.packageName.contains(needle, ignoreCase = true)
                }
                .map { app ->
                    val blocked = app.packageName in lock.blockedPackages
                    AppRow(app = app, blocked = blocked, locked = blocked && engaged)
                }
                .toList()
            AppSelectionUiState(
                isLoading = apps == null,
                query = search,
                showSystemApps = showSystem,
                sessionEngaged = engaged,
                blockedCount = lock.blockedPackages.size,
                rows = rows,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSelectionUiState())

    init {
        viewModelScope.launch { installedApps.value = appsRepository.loadLaunchableApps() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onShowSystemAppsChange(show: Boolean) {
        showSystemApps.value = show
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            val applied = repository.setPackageBlocked(packageName, blocked)
            if (!applied) _unblockRefused.tryEmit(Unit)
        }
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val icon = appsRepository.loadIcon(packageName, iconSizePx)?.asImageBitmap() ?: return null
        iconCache.put(packageName, icon)
        return icon
    }

    private companion object {
        const val ICON_CACHE_SIZE = 256
    }
}
