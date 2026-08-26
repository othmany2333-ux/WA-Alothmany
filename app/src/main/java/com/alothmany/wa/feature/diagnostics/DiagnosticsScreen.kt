package com.alothmany.wa.feature.diagnostics

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.BuildConfig
import com.alothmany.wa.R
import com.alothmany.wa.core.ui.components.GlassCard

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back))
                }
                Text(stringResource(R.string.diagnostics), style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_system))
            }
        }

        DiagnosticRow(stringResource(R.string.device), "${Build.MANUFACTURER} ${Build.MODEL}")
        DiagnosticRow(
            stringResource(R.string.android_version),
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        DiagnosticRow(stringResource(R.string.app_version), BuildConfig.VERSION_NAME)
        DiagnosticRow(stringResource(R.string.database_status), stringResource(R.string.healthy))
        DiagnosticRow(stringResource(R.string.core_status), stringResource(R.string.phase_two))

        SectionHeader(stringResource(R.string.shizuku))
        DiagnosticRow(stringResource(R.string.capability_status), state.shizuku.status.name)
        DiagnosticRow(stringResource(R.string.binder_alive), state.shizuku.binderAlive.toString())
        DiagnosticRow(stringResource(R.string.permission_granted), state.shizuku.permissionGranted.toString())
        DiagnosticRow(
            stringResource(R.string.privileged_service),
            state.shizuku.privilegedServiceConnected.toString(),
        )
        DiagnosticRow(
            stringResource(R.string.shizuku_server),
            "version=${state.shizuku.serverVersion ?: "-"}, uid=${state.shizuku.serverUid ?: "-"}",
        )
        DiagnosticRow(
            stringResource(R.string.privileged_uid),
            state.shizuku.privilegedUid?.toString() ?: "-",
        )

        SectionHeader(stringResource(R.string.accessibility))
        DiagnosticRow(stringResource(R.string.enabled), state.accessibility.enabled.toString())
        DiagnosticRow(
            stringResource(R.string.service_connected),
            state.accessibility.serviceConnected.toString(),
        )
        DiagnosticRow(
            stringResource(R.string.last_ui_event),
            state.accessibility.lastPackage ?: "-",
        )
        DiagnosticRow(
            stringResource(R.string.accessibility_nodes),
            state.accessibility.nodeCount.toString(),
        )

        SectionHeader(stringResource(R.string.overlay))
        DiagnosticRow(
            stringResource(R.string.permission_granted),
            state.overlayPermissionGranted.toString(),
        )
        DiagnosticRow(stringResource(R.string.running), state.overlayRunning.toString())

        SectionHeader(stringResource(R.string.whatsapp_sources))
        DiagnosticRow(
            stringResource(R.string.detected_sources),
            state.sources.size.toString(),
        )
        state.sources.forEach { source ->
            DiagnosticRow(
                source.displayName,
                "${source.packageName} • user=${source.userId} • ${source.profileType}",
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    GlassCard {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
