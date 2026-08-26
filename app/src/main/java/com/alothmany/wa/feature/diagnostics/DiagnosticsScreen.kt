package com.alothmany.wa.feature.diagnostics

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alothmany.wa.BuildConfig
import com.alothmany.wa.R
import com.alothmany.wa.core.ui.components.GlassCard
import com.alothmany.wa.core.ui.components.StatusPill
import com.alothmany.wa.core.ui.theme.*

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.diagnostics), style = MaterialTheme.typography.headlineMedium)
        }
        DiagnosticRow(stringResource(R.string.device), "${Build.MANUFACTURER} ${Build.MODEL}")
        DiagnosticRow(stringResource(R.string.android_version), "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        DiagnosticRow(stringResource(R.string.app_version), BuildConfig.VERSION_NAME)
        DiagnosticRow(stringResource(R.string.database_status), stringResource(R.string.healthy))
        DiagnosticRow(stringResource(R.string.core_status), stringResource(R.string.phase_one))
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    GlassCard {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
