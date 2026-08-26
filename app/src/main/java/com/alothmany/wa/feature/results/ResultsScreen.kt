package com.alothmany.wa.feature.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.R
import com.alothmany.wa.core.ui.components.*

@Composable
fun ResultsScreen(viewModel: ResultsViewModel = hiltViewModel()) {
    val links by viewModel.links.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.results), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        if (links.isEmpty()) {
            EmptyState(Icons.Rounded.QueryStats, stringResource(R.string.no_results), Modifier.weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(links, key = { it.id }) { link ->
                    GlassCard {
                        Text(link.url, style = MaterialTheme.typography.titleMedium)
                        Text(link.category, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("×${link.occurrenceCount}")
                    }
                }
            }
        }
    }
}
