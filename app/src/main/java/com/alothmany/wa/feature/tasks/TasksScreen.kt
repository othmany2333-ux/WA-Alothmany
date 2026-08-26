package com.alothmany.wa.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.R
import com.alothmany.wa.core.ui.components.*
import com.alothmany.wa.core.ui.theme.Cyan400

@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.tasks), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        if (tasks.isEmpty()) {
            EmptyState(Icons.Rounded.TaskAlt, stringResource(R.string.no_tasks), Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.id }) { task ->
                    GlassCard(accent = Cyan400) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium)
                        Text(task.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${task.processed} / ${task.total}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
