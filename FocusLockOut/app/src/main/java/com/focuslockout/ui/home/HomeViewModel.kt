package com.focuslockout.ui.home

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslockout.data.ChallengeType
import com.focuslockout.data.LockState
import com.focuslockout.focusRepository
import com.focuslockout.permissions.PermissionChecker
import com.focuslockout.permissions.PermissionStatus
import com.focuslockout.service.LockSessionService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoaded: Boolean = false,
    val lockState: LockState = LockState(),
    val now: Long = 0L,
    val permissions: PermissionStatus = PermissionStatus(),
    val hasAccelerometer: Boolean = true,
) {
    val isLockEngaged: Boolean get() = lockState.isEngaged(now)
    val remainingMillis: Long get() = lockState.remainingMillis(now)
    val blockedCount: Int get() = lockState.blockedPackages.size
    val canArm: Boolean get() = permissions.allRequiredGranted && blockedCount > 0
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.focusRepository
    private val permissionStatus = MutableStateFlow(PermissionChecker.currentStatus(application))
    private val hasAccelerometer = application.getSystemService(SensorManager::class.java)
        ?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    /** Ticks every second only while a session is active; idle screens don't need a clock. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val clock: Flow<Long> = repository.lockState
        .map { it.isSessionActive }
        .distinctUntilChanged()
        .flatMapLatest { active -> if (active) ticker() else flowOf(System.currentTimeMillis()) }

    val uiState: StateFlow<HomeUiState> =
        combine(repository.lockState, clock, permissionStatus) { lock, now, permissions ->
            HomeUiState(
                isLoaded = true,
                lockState = lock,
                now = now,
                permissions = permissions,
                hasAccelerometer = hasAccelerometer,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(hasAccelerometer = hasAccelerometer),
        )

    init {
        // Ends the session on time while the app is open (the foreground service does the same
        // in the background; the repository makes the double-end harmless).
        viewModelScope.launch {
            repository.lockState.collectLatest { lock ->
                if (!lock.isSessionActive) return@collectLatest
                var remaining = lock.sessionEndTime - System.currentTimeMillis()
                while (remaining > 0) {
                    delay(remaining)
                    remaining = lock.sessionEndTime - System.currentTimeMillis()
                }
                repository.endSessionIfExpired()
            }
        }
    }

    fun refreshPermissions() {
        permissionStatus.value = PermissionChecker.currentStatus(getApplication<Application>())
    }

    fun setDuration(minutes: Int) {
        viewModelScope.launch { repository.setDurationMinutes(minutes) }
    }

    fun setChallenge(type: ChallengeType) {
        viewModelScope.launch { repository.setChallengeType(type) }
    }

    fun armLock(durationMinutes: Int) {
        val state = uiState.value
        if (!state.canArm || state.isLockEngaged) return
        viewModelScope.launch {
            val armed = repository.armSession(durationMinutes, state.lockState.selectedChallengeType)
            LockSessionService.start(getApplication<Application>(), armed.sessionEndTime)
        }
    }

    private fun ticker(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }
}
