package com.focuslockout.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslockout.overlay.OverlayActivity
import com.focuslockout.service.LockSessionService

@Composable
fun HomeRoute(
    onChooseApps: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Permissions are granted in Settings, so re-check every time the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    // While visible we are allowed to (re)start the session service, e.g. after a reboot.
    LaunchedEffect(state.isLockEngaged, state.lockState.sessionEndTime) {
        if (state.isLockEngaged) LockSessionService.start(context, state.lockState.sessionEndTime)
    }

    if (!state.isLoaded) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    Crossfade(targetState = state.isLockEngaged, label = "home-mode") { engaged ->
        if (engaged) {
            DashboardScreen(
                state = state,
                onEmergencyUnlock = {
                    context.startActivity(
                        OverlayActivity.emergencyIntent(context, state.lockState.selectedChallengeType),
                    )
                },
                onAddApps = onChooseApps,
                onRefreshPermissions = viewModel::refreshPermissions,
            )
        } else {
            SetupScreen(
                state = state,
                onChooseApps = onChooseApps,
                onDurationCommitted = viewModel::setDuration,
                onChallengeSelected = viewModel::setChallenge,
                onArm = viewModel::armLock,
                onRefreshPermissions = viewModel::refreshPermissions,
            )
        }
    }
}
