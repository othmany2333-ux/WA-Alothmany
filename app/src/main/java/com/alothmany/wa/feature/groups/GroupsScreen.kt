package com.alothmany.wa.feature.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.R
import com.alothmany.wa.core.ui.components.*
import com.alothmany.wa.core.ui.theme.*

@Composable
fun GroupsScreen(viewModel: GroupsViewModel = hiltViewModel()) {
    val groups by viewModel.groups.collectAsState()
    val selected by viewModel.selected.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = remember(groups, query) { groups.filter { it.displayName.contains(query, ignoreCase = true) } }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.group_management), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_groups)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { viewModel.selectAll(filtered) }, label = { Text(stringResource(R.string.select_all)) }, leadingIcon = { Icon(Icons.Rounded.SelectAll, null) })
            AssistChip(onClick = viewModel::clear, label = { Text(stringResource(R.string.clear_selection)) }, leadingIcon = { Icon(Icons.Rounded.Deselect, null) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { viewModel.selectArchived(filtered) }, label = { Text(stringResource(R.string.select_archived)) }, leadingIcon = { Icon(Icons.Rounded.Archive, null) })
            AssistChip(onClick = { viewModel.selectCommunities(filtered) }, label = { Text(stringResource(R.string.select_communities)) }, leadingIcon = { Icon(Icons.Rounded.Hub, null) })
        }
        Text(stringResource(R.string.selected_count, selected.size), color = Cyan400)

        if (filtered.isEmpty()) {
            EmptyState(Icons.Rounded.Groups, stringResource(R.string.no_groups), Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { group ->
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = group.id in selected, onCheckedChange = { viewModel.toggle(group.id) })
                            Column(Modifier.weight(1f)) {
                                Text(group.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(group.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (group.archived) AssistChip(onClick = {}, label = { Text(stringResource(R.string.archived)) })
                            if (group.isCommunity) Icon(Icons.Rounded.Hub, null, tint = Purple400)
                        }
                    }
                }
            }
        }
    }
}
