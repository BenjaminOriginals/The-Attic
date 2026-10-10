package com.focuslockout.overlay

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslockout.R
import com.focuslockout.data.ChallengeType
import com.focuslockout.data.LockPolicy
import com.focuslockout.data.LockState
import com.focuslockout.overlay.challenge.MathChallenge
import com.focuslockout.overlay.challenge.ShakeChallenge
import com.focuslockout.overlay.challenge.TypingChallenge
import com.focuslockout.ui.components.CenteredScrollColumn
import com.focuslockout.ui.components.FocusCard
import com.focuslockout.ui.components.PrimaryActionButton
import com.focuslockout.ui.components.SecondaryActionButton
import com.focuslockout.ui.theme.neonGlow
import com.focuslockout.ui.titleRes
import com.focuslockout.util.TimeFormat
import kotlinx.coroutines.delay

@Composable
fun OverlayRoute(
    viewModel: OverlayViewModel,
    onReturnToFocus: () -> Unit,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val request = state.request ?: return
    val blockedMode = request.mode == OverlayMode.BLOCKED_APP

    // Nothing left to guard (session over, or this app already has a grace period): get out of the way.
    val nothingToGuard = lockState?.let { lock -> !isStillGuarded(lock, request, now) } == true
    val autoClose = nothingToGuard && state.phase != OverlayPhase.PASSED
    LaunchedEffect(autoClose) { if (autoClose) onClose() }

    // Back never reveals a blocked app: it leaves through the home screen instead.
    BackHandler {
        when {
            state.phase == OverlayPhase.PASSED -> if (state.unlockCommitted) onClose()
            blockedMode -> onReturnToFocus()
            else -> onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        when (state.phase) {
            OverlayPhase.LOCKED -> LockedContent(
                appLabel = request.appLabel ?: stringResource(R.string.overlay_unknown_app),
                challenge = state.challenge,
                remainingMillis = lockState?.remainingMillis(now) ?: 0L,
                onReturnToFocus = onReturnToFocus,
                onStartChallenge = viewModel::startChallenge,
            )
            OverlayPhase.CHALLENGE -> ChallengeContent(
                state = state,
                blockedMode = blockedMode,
                onGiveUp = if (blockedMode) onReturnToFocus else onClose,
                viewModel = viewModel,
            )
            OverlayPhase.PASSED -> PassedContent(
                state = state,
                blockedMode = blockedMode,
                appLabel = request.appLabel ?: stringResource(R.string.overlay_unknown_app),
                onShare = onShare,
                onContinue = onClose,
            )
        }
    }
}

private fun isStillGuarded(lock: LockState, request: OverlayRequest, now: Long): Boolean {
    if (!lock.isEngaged(now)) return false
    val packageName = request.packageName
    return request.mode == OverlayMode.EMERGENCY_UNLOCK || packageName == null || lock.shouldBlock(packageName, now)
}

@Composable
private fun LockedContent(
    appLabel: String,
    challenge: ChallengeType,
    remainingMillis: Long,
    onReturnToFocus: () -> Unit,
    onStartChallenge: () -> Unit,
) {
    CenteredScrollColumn {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = stringResource(R.string.overlay_locked_title, appLabel),
            style = MaterialTheme.typography.headlineMedium.neonGlow(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.overlay_locked_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = TimeFormat.countdown(remainingMillis),
                style = MaterialTheme.typography.displaySmall.neonGlow(),
            )
            Text(
                text = stringResource(R.string.overlay_lock_lifts_in),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryActionButton(text = stringResource(R.string.overlay_back_to_focus), onClick = onReturnToFocus)
        SecondaryActionButton(
            text = stringResource(R.string.overlay_break_lock, stringResource(challenge.titleRes)),
            onClick = onStartChallenge,
            danger = true,
        )
        Text(
            text = stringResource(R.string.overlay_grace_hint, LockPolicy.GRACE_PERIOD_MINUTES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChallengeContent(
    state: OverlayUiState,
    blockedMode: Boolean,
    onGiveUp: () -> Unit,
    viewModel: OverlayViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(state.challenge.titleRes),
                    style = MaterialTheme.typography.headlineSmall.neonGlow(),
                )
                val subtitle = state.request?.appLabel?.takeIf { blockedMode }
                    ?.let { stringResource(R.string.overlay_unlocking_app, it) }
                    ?: stringResource(R.string.overlay_unlocking_session)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onGiveUp) {
                Text(stringResource(R.string.overlay_give_up), color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.shakeFallback) {
            Text(
                text = stringResource(R.string.overlay_shake_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when (state.challenge) {
            ChallengeType.TYPING -> TypingChallenge(state = state.typing, onInputChange = viewModel::onTypingInput)
            ChallengeType.SHAKE -> ShakeChallenge(
                count = state.shakeCount,
                target = LockPolicy.SHAKE_TARGET,
                onShake = viewModel::onShake,
            )
            ChallengeType.MATH -> MathChallenge(
                state = state.math,
                target = LockPolicy.MATH_STREAK_TARGET,
                onInputChange = viewModel::onMathInput,
                onSubmit = viewModel::submitMathAnswer,
            )
        }
    }
}

@Composable
private fun PassedContent(
    state: OverlayUiState,
    blockedMode: Boolean,
    appLabel: String,
    onShare: (String) -> Unit,
    onContinue: () -> Unit,
) {
    CenteredScrollColumn {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(88.dp),
        )
        Text(
            text = stringResource(if (blockedMode) R.string.passed_title_blocked else R.string.passed_title_emergency),
            style = MaterialTheme.typography.headlineMedium.neonGlow(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (blockedMode) {
                stringResource(R.string.passed_body_blocked, LockPolicy.GRACE_PERIOD_MINUTES, appLabel)
            } else {
                stringResource(R.string.passed_body_emergency)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (state.shameText.isNotEmpty()) {
            FocusCard(borderColor = MaterialTheme.colorScheme.error) {
                Text(
                    text = stringResource(R.string.shame_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "“${state.shameText}”",
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                )
                Button(
                    onClick = { onShare(state.shameText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.shame_share), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        PrimaryActionButton(
            text = if (blockedMode) {
                stringResource(R.string.passed_continue_to_app, appLabel)
            } else {
                stringResource(R.string.passed_back_to_app)
            },
            onClick = onContinue,
            enabled = state.unlockCommitted,
        )
    }
}
