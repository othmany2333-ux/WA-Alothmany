package com.alothmany.wa.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.R
import com.alothmany.wa.core.model.*
import com.alothmany.wa.core.ui.AppLocaleController
import com.alothmany.wa.core.ui.components.*
import com.alothmany.wa.core.ui.theme.*
import com.alothmany.wa.system.integration.CapabilityStatus

@Composable
fun SettingsScreen(
    onOpenLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsState()
    val system by viewModel.systemState.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = viewModel::refreshSystem) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_system))
            }
        }

        GlassCard(accent = Cyan400) {
            SectionTitle(stringResource(R.string.general))
            Text(stringResource(R.string.language))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val values = listOf(
                    AppLanguage.SYSTEM to R.string.language_system,
                    AppLanguage.ARABIC to R.string.language_arabic,
                    AppLanguage.ENGLISH to R.string.language_english,
                )
                values.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = prefs.language == value,
                        onClick = {
                            viewModel.language(value)
                            AppLocaleController.apply(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, values.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val values = listOf(
                    AppTheme.SYSTEM to R.string.theme_system,
                    AppTheme.DARK to R.string.theme_dark,
                    AppTheme.LIGHT to R.string.theme_light,
                )
                values.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = prefs.theme == value,
                        onClick = { viewModel.theme(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, values.size),
                    ) { Text(stringResource(label)) }
                }
            }
        }

        GlassCard(accent = Gold400) {
            SectionTitle(stringResource(R.string.performance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val values = listOf(
                    PerformanceMode.TURBO to R.string.turbo,
                    PerformanceMode.BALANCED to R.string.balanced,
                    PerformanceMode.SAFE to R.string.safe,
                )
                values.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = prefs.performanceMode == value,
                        onClick = { viewModel.performance(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, values.size),
                    ) { Text(stringResource(label)) }
                }
            }
        }

        GlassCard(accent = Green400) {
            SectionTitle(stringResource(R.string.automation))
            SettingSwitchRow(stringResource(R.string.auto_resume), prefs.autoResume, viewModel::autoResume)
            SettingSwitchRow(stringResource(R.string.notifications), prefs.notifications, viewModel::notifications)
            SettingSwitchRow(stringResource(R.string.sync_archived), prefs.syncArchived, viewModel::syncArchived)
            SettingSwitchRow(stringResource(R.string.sync_communities), prefs.syncCommunities, viewModel::syncCommunities)
            SettingSwitchRow(stringResource(R.string.save_progress), prefs.saveProgress, viewModel::saveProgress)
        }

        GlassCard(accent = Purple400) {
            SectionTitle(stringResource(R.string.permission_center))

            PermissionAction(
                title = stringResource(R.string.shizuku),
                status = capabilityText(system.shizuku.status),
                icon = Icons.Rounded.Terminal,
                color = capabilityColor(system.shizuku.status),
                actionText = stringResource(
                    if (system.shizuku.permissionGranted) R.string.recheck
                    else R.string.grant_permission
                ),
                onAction = viewModel::configureShizuku,
            )

            PermissionAction(
                title = stringResource(R.string.accessibility),
                status = stringResource(
                    if (system.accessibility.enabled) R.string.enabled
                    else R.string.permission_required
                ),
                icon = Icons.Rounded.AccessibilityNew,
                color = if (system.accessibility.enabled) Green400 else Blue400,
                actionText = stringResource(
                    if (system.accessibility.enabled) R.string.manage_permission
                    else R.string.grant_permission
                ),
                onAction = viewModel::configureAccessibility,
            )

            PermissionAction(
                title = stringResource(R.string.overlay),
                status = stringResource(
                    when {
                        system.overlayRunning -> R.string.running
                        system.overlayPermissionGranted -> R.string.ready
                        else -> R.string.permission_required
                    }
                ),
                icon = Icons.Rounded.Layers,
                color = if (system.overlayRunning) Green400 else Purple400,
                actionText = stringResource(
                    when {
                        system.overlayRunning -> R.string.stop_overlay
                        system.overlayPermissionGranted -> R.string.start_overlay
                        else -> R.string.grant_permission
                    }
                ),
                onAction = viewModel::toggleOverlay,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.whatsapp_sources), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.detected_sources_value, system.sources.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = viewModel::probeSources, enabled = !system.probing) {
                    Icon(Icons.Rounded.Search, null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (system.probing) R.string.probing_system
                            else R.string.probe_sources
                        )
                    )
                }
            }

            if (system.sources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                system.sources.forEach { source ->
                    Text(
                        "• ${source.displayName}  [user ${source.userId}]",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenLogs, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.ReceiptLong, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_logs))
            }
            OutlinedButton(onClick = onOpenDiagnostics, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.HealthAndSafety, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_diagnostics))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PermissionAction(
    title: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            StatusPill(title, status, icon, color)
        }
        OutlinedButton(onClick = onAction) { Text(actionText) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun capabilityText(status: CapabilityStatus): String = stringResource(
    when (status) {
        CapabilityStatus.READY -> R.string.ready
        CapabilityStatus.NEEDS_PERMISSION -> R.string.permission_required
        CapabilityStatus.OFFLINE -> R.string.offline
        CapabilityStatus.LIMITED -> R.string.limited
        CapabilityStatus.UNAVAILABLE -> R.string.unavailable
        CapabilityStatus.ERROR -> R.string.status_error
    }
)

private fun capabilityColor(status: CapabilityStatus): Color = when (status) {
    CapabilityStatus.READY -> Green400
    CapabilityStatus.NEEDS_PERMISSION -> Gold400
    CapabilityStatus.OFFLINE -> Red400
    CapabilityStatus.LIMITED -> Blue400
    CapabilityStatus.UNAVAILABLE -> Red400
    CapabilityStatus.ERROR -> Red400
}
