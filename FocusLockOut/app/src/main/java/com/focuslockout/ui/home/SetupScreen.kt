package com.focuslockout.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslockout.R
import com.focuslockout.data.ChallengeType
import com.focuslockout.data.LockPolicy
import com.focuslockout.ui.components.CardShape
import com.focuslockout.ui.components.FocusCard
import com.focuslockout.ui.components.PrimaryActionButton
import com.focuslockout.ui.components.SecondaryActionButton
import com.focuslockout.ui.components.SectionLabel
import com.focuslockout.ui.components.quantityStringResource
import com.focuslockout.ui.descriptionRes
import com.focuslockout.ui.theme.TextDisabled
import com.focuslockout.ui.theme.neonGlow
import com.focuslockout.ui.titleRes
import kotlin.math.roundToInt

@Composable
fun SetupScreen(
    state: HomeUiState,
    onChooseApps: () -> Unit,
    onDurationCommitted: (Int) -> Unit,
    onChallengeSelected: (ChallengeType) -> Unit,
    onArm: (Int) -> Unit,
    onRefreshPermissions: () -> Unit,
) {
    val lock = state.lockState
    var minutes by remember(lock.durationMinutes) { mutableIntStateOf(lock.durationMinutes) }
    var confirmArm by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.app_wordmark),
                style = MaterialTheme.typography.headlineLarge.neonGlow(),
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.permissions.anyMissing) {
            PermissionBanner(status = state.permissions, onRefresh = onRefreshPermissions)
        }

        FocusCard {
            SectionLabel(stringResource(R.string.setup_block_list_label))
            Text(
                text = if (state.blockedCount == 0) {
                    stringResource(R.string.setup_block_list_empty)
                } else {
                    quantityStringResource(R.plurals.setup_block_list_count, state.blockedCount, state.blockedCount)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            SecondaryActionButton(text = stringResource(R.string.setup_choose_apps), onClick = onChooseApps)
        }

        FocusCard {
            SectionLabel(stringResource(R.string.setup_duration_label))
            Text(
                text = stringResource(R.string.setup_duration_value, minutes),
                style = MaterialTheme.typography.displaySmall.neonGlow(),
            )
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = it.roundToInt() },
                onValueChangeFinished = { onDurationCommitted(minutes) },
                valueRange = LockPolicy.MIN_DURATION_MINUTES.toFloat()..LockPolicy.MAX_DURATION_MINUTES.toFloat(),
                steps = LockPolicy.DURATION_SLIDER_STEPS,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTickColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.setup_duration_value, LockPolicy.MIN_DURATION_MINUTES),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.setup_duration_value, LockPolicy.MAX_DURATION_MINUTES),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.setup_challenge_label))
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChallengeType.entries.forEach { type ->
                    val available = type != ChallengeType.SHAKE || state.hasAccelerometer
                    ChallengeOption(
                        type = type,
                        selected = lock.selectedChallengeType == type,
                        enabled = available,
                        onClick = { onChallengeSelected(type) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryActionButton(
                text = stringResource(R.string.setup_arm_lock),
                onClick = { confirmArm = true },
                enabled = state.canArm,
            )
            val blocker = when {
                !state.permissions.allRequiredGranted -> stringResource(R.string.setup_arm_needs_permissions)
                state.blockedCount == 0 -> stringResource(R.string.setup_arm_needs_apps)
                else -> null
            }
            if (blocker != null) {
                Text(
                    text = blocker,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (confirmArm) {
        AlertDialog(
            onDismissRequest = { confirmArm = false },
            title = { Text(stringResource(R.string.arm_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arm_confirm_body,
                        minutes,
                        stringResource(lock.selectedChallengeType.titleRes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmArm = false
                        onArm(minutes)
                    },
                ) { Text(stringResource(R.string.arm_confirm_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun ChallengeOption(
    type: ChallengeType,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, CardShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.primary,
                unselectedColor = colors.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(type.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.onSurface else TextDisabled,
            )
            Text(
                text = stringResource(if (enabled) type.descriptionRes else R.string.challenge_shake_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) colors.onSurfaceVariant else TextDisabled,
            )
        }
    }
}
