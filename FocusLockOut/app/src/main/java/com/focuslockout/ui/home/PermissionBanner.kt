package com.focuslockout.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.focuslockout.R
import com.focuslockout.permissions.PermissionChecker
import com.focuslockout.permissions.PermissionStatus
import com.focuslockout.ui.components.FocusCard

/**
 * Lists every special access the lock needs, each with a button that jumps straight to the right
 * Settings page. Accessibility gets a prominent disclosure first, as Play policy requires.
 */
@Composable
fun PermissionBanner(status: PermissionStatus, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showAccessibilityDisclosure by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRefresh() }

    val accent = if (status.allRequiredGranted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
    FocusCard(modifier = modifier, borderColor = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (status.allRequiredGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    if (status.allRequiredGranted) R.string.permissions_title_optional else R.string.permissions_title,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = stringResource(R.string.permissions_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionRow(
            title = stringResource(R.string.permission_accessibility_title),
            description = stringResource(R.string.permission_accessibility_description),
            granted = status.accessibilityEnabled,
            onGrant = { showAccessibilityDisclosure = true },
        )
        if (status.accessibilityEnabled && !status.accessibilityConnected) {
            Text(
                text = stringResource(R.string.permission_accessibility_not_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        PermissionRow(
            title = stringResource(R.string.permission_overlay_title),
            description = stringResource(R.string.permission_overlay_description),
            granted = status.overlayGranted,
            onGrant = { PermissionChecker.openSettings(context, PermissionChecker.overlaySettingsIntent(context)) },
        )
        PermissionRow(
            title = stringResource(R.string.permission_usage_title),
            description = stringResource(R.string.permission_usage_description),
            granted = status.usageAccessGranted,
            onGrant = { PermissionChecker.openSettings(context, PermissionChecker.usageAccessSettingsIntent()) },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                title = stringResource(R.string.permission_notifications_title),
                description = stringResource(R.string.permission_notifications_description),
                granted = status.notificationsGranted,
                onGrant = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        if (!status.accessibilityEnabled) {
            TextButton(
                onClick = { PermissionChecker.openSettings(context, PermissionChecker.appDetailsIntent(context)) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = stringResource(R.string.permission_restricted_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
            text = { Text(stringResource(R.string.accessibility_disclosure_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccessibilityDisclosure = false
                        PermissionChecker.openSettings(context, PermissionChecker.accessibilitySettingsIntent())
                    },
                ) { Text(stringResource(R.string.accessibility_disclosure_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) {
                    Text(stringResource(R.string.action_not_now))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = stringResource(
                if (granted) R.string.cd_permission_granted else R.string.cd_permission_missing,
            ),
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.action_grant), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
