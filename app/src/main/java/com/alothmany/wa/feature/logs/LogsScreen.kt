package com.alothmany.wa.feature.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.R
import com.alothmany.wa.core.ui.components.*
import com.alothmany.wa.core.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogsScreen(onBack: () -> Unit, viewModel: LogsViewModel = hiltViewModel()) {
    val logs by viewModel.logs.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.logs), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::clear) { Text(stringResource(R.string.clear_logs)) }
        }
        Spacer(Modifier.height(12.dp))
        if (logs.isEmpty()) {
            EmptyState(Icons.Rounded.ReceiptLong, stringResource(R.string.no_logs), Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.id }) { log ->
                    val color = when (log.level) {
                        "SUCCESS" -> Green400
                        "WARNING" -> Gold400
                        "ERROR" -> Red400
                        else -> Cyan400
                    }
                    GlassCard(accent = color) {
                        Text("${log.level} · ${log.module}", color = color, style = MaterialTheme.typography.labelLarge)
                        Text(log.message)
                        Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
