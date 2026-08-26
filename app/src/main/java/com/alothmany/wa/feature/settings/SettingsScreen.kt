package com.alothmany.wa.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.R
import com.alothmany.wa.core.model.*
import com.alothmany.wa.core.ui.AppLocaleController
import com.alothmany.wa.core.ui.components.*
import com.alothmany.wa.core.ui.theme.*

@Composable
fun SettingsScreen(
    onOpenLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)

        GlassCard(accent = Cyan400) {
            SectionTitle(stringResource(R.string.general))
            Text(stringResource(R.string.language))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val values = listOf(AppLanguage.SYSTEM to R.string.language_system, AppLanguage.ARABIC to R.string.language_arabic, AppLanguage.ENGLISH to R.string.language_english)
                values.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = prefs.language == value,
                        onClick = { viewModel.language(value); AppLocaleController.apply(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, values.size),
                    ) { Text(stringResource(label)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val values = listOf(AppTheme.SYSTEM to R.string.theme_system, AppTheme.DARK to R.string.theme_dark, AppTheme.LIGHT to R.string.theme_light)
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
                val values = listOf(PerformanceMode.TURBO to R.string.turbo, PerformanceMode.BALANCED to R.string.balanced, PerformanceMode.SAFE to R.string.safe)
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
            SectionTitle(stringResource(R.string.permissions_system))
            StatusPill(stringResource(R.string.shizuku), stringResource(R.string.not_configured), Icons.Rounded.Terminal, Gold400)
            Spacer(Modifier.height(8.dp))
            StatusPill(stringResource(R.string.accessibility), stringResource(R.string.not_configured), Icons.Rounded.AccessibilityNew, Blue400)
            Spacer(Modifier.height(8.dp))
            StatusPill(stringResource(R.string.overlay), stringResource(R.string.not_configured), Icons.Rounded.Layers, Purple400)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenLogs, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.ReceiptLong, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_logs)) }
            OutlinedButton(onClick = onOpenDiagnostics, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.HealthAndSafety, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_diagnostics)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
