package com.focuslockout.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslockout.R
import com.focuslockout.ui.components.FocusCard
import com.focuslockout.ui.components.SecondaryActionButton
import com.focuslockout.ui.titleRes
import com.focuslockout.ui.theme.neonGlow
import com.focuslockout.util.TimeFormat
import java.util.Date

@Composable
fun DashboardScreen(
    state: HomeUiState,
    onEmergencyUnlock: () -> Unit,
    onAddApps: () -> Unit,
    onRefreshPermissions: () -> Unit,
) {
    val lock = state.lockState
    val context = LocalContext.current
    val endsAt = remember(lock.sessionEndTime) {
        DateFormat.getTimeFormat(context).format(Date(lock.sessionEndTime))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.headlineMedium.neonGlow(),
        )

        Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { lock.remainingFraction(state.now) },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 10.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = TimeFormat.countdown(state.remainingMillis),
                    style = MaterialTheme.typography.displayMedium.neonGlow(),
                )
                Text(
                    text = stringResource(R.string.dashboard_remaining),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.permissions.allRequiredGranted) {
            Text(
                text = stringResource(R.string.dashboard_enforcement_offline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            PermissionBanner(status = state.permissions, onRefresh = onRefreshPermissions)
        }

        FocusCard {
            InfoRow(
                label = stringResource(R.string.dashboard_blocked_apps),
                value = state.blockedCount.toString(),
            )
            InfoRow(
                label = stringResource(R.string.dashboard_penalty),
                value = stringResource(lock.selectedChallengeType.titleRes),
            )
            InfoRow(label = stringResource(R.string.dashboard_ends_at), value = endsAt)
        }

        SecondaryActionButton(text = stringResource(R.string.dashboard_add_apps), onClick = onAddApps)

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryActionButton(
                text = stringResource(R.string.dashboard_emergency_unlock),
                onClick = onEmergencyUnlock,
                danger = true,
            )
            Text(
                text = stringResource(
                    R.string.dashboard_emergency_caption,
                    stringResource(lock.selectedChallengeType.titleRes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
