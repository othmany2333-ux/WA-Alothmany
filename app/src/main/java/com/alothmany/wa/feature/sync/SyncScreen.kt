package com.alothmany.wa.feature.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.R
import com.alothmany.wa.core.navigation.Destination
import com.alothmany.wa.core.ui.components.GlassCard
import com.alothmany.wa.core.ui.components.SectionTitle
import com.alothmany.wa.core.ui.theme.*
import com.alothmany.wa.feature.sync.model.ContactSyncMode
import com.alothmany.wa.feature.sync.model.GroupSelectionKind
import com.alothmany.wa.feature.sync.model.SyncEngineStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val running = state.runtime.running || state.runtime.status == SyncEngineStatus.PAUSED

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.smart_sync_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.smart_sync_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back))
                }
            },
            actions = {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (running) Green400.copy(alpha = .12f) else Cyan400.copy(alpha = .10f),
                    border = BorderStroke(1.dp, if (running) Green400.copy(.5f) else Cyan400.copy(.4f)),
                ) {
                    Text(
                        if (running) stringResource(R.string.sync_live) else "v0.3",
                        color = if (running) Green400 else Cyan400,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
            },
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.sourceName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Chat, null, tint = Teal400, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.sync_current_source, state.sourceName),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }

            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (state.search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Rounded.Close, null)
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.sync_search_hint)) },
                shape = RoundedCornerShape(18.dp),
            )

            GlassCard(accent = Cyan400) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        SectionTitle(stringResource(R.string.sync_groups_preview))
                        Text(
                            stringResource(R.string.sync_groups_found, state.totalMatching),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    if (state.selectedCount > 0) {
                        AssistChip(
                            onClick = viewModel::clearSelection,
                            label = { Text(stringResource(R.string.selected_count, state.selectedCount)) },
                            leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (state.groups.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                if (state.search.isBlank()) R.string.sync_no_groups_yet
                                else R.string.sync_no_search_results
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.groups.forEachIndexed { index, group ->
                        SyncGroupRow(group = group, onClick = { viewModel.toggleGroup(group.id) })
                        if (index != state.groups.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(.55f))
                    }
                }

                if (state.hasMore) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = viewModel::showMore, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.ExpandMore, null)
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.show_more_groups))
                    }
                } else if (state.visibleLimit > 4 && state.totalMatching > 4) {
                    TextButton(onClick = viewModel::collapsePreview, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.ExpandLess, null)
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.show_less_groups))
                    }
                }
            }

            GlassCard(accent = Purple400) {
                SectionTitle(stringResource(R.string.smart_group_selection))
                FilterRow(
                    left = Triple(R.string.select_all, Icons.Rounded.SelectAll, GroupSelectionKind.ALL),
                    right = Triple(R.string.select_unread, Icons.Rounded.MarkEmailUnread, GroupSelectionKind.UNREAD),
                    onSelect = viewModel::select,
                )
                FilterRow(
                    left = Triple(R.string.select_active, Icons.Rounded.Bolt, GroupSelectionKind.ACTIVE),
                    right = Triple(R.string.select_locked, Icons.Rounded.Lock, GroupSelectionKind.LOCKED),
                    onSelect = viewModel::select,
                )
                FilterRow(
                    left = Triple(R.string.select_deleted, Icons.Rounded.DeleteSweep, GroupSelectionKind.DELETED),
                    right = Triple(R.string.select_communities, Icons.Rounded.Hub, GroupSelectionKind.COMMUNITIES),
                    onSelect = viewModel::select,
                )
            }

            GlassCard(accent = Blue400) {
                SectionTitle(stringResource(R.string.contact_sync_title))
                Text(
                    stringResource(R.string.contact_sync_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                ContactModeRow(
                    state.contactMode,
                    ContactSyncMode.UNSAVED_WHATSAPP_NUMBERS,
                    R.string.contact_unsaved_whatsapp,
                    ContactSyncMode.SAVED_WHATSAPP_NUMBERS,
                    R.string.contact_saved_whatsapp,
                    viewModel::setContactMode,
                )
                ContactModeRow(
                    state.contactMode,
                    ContactSyncMode.WHATSAPP_NUMBERS_AND_CONTACTS,
                    R.string.contact_numbers_and_contacts,
                    ContactSyncMode.CONTACTS_ONLY,
                    R.string.contact_contacts_only,
                    viewModel::setContactMode,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.contact_sync_phase_note),
                    color = Gold400,
                    fontSize = 11.sp,
                )
            }

            GlassCard(accent = Gold400) {
                SectionTitle(stringResource(R.string.link_sync_operations))
                OperationRow(
                    selected = state.selectedCount > 0,
                    firstText = R.string.link_extract,
                    firstIcon = Icons.Rounded.Link,
                    firstColor = Teal400,
                    secondText = R.string.link_publish,
                    secondIcon = Icons.Rounded.Campaign,
                    secondColor = Blue400,
                    onFirst = {
                        viewModel.prepareLinkedOperation()
                        onNavigate(Destination.Extract.route)
                    },
                    onSecond = {
                        viewModel.prepareLinkedOperation()
                        onNavigate(Destination.Publish.route)
                    },
                )
                OperationRow(
                    selected = state.selectedCount > 0,
                    firstText = R.string.link_join,
                    firstIcon = Icons.Rounded.GroupAdd,
                    firstColor = Purple400,
                    secondText = R.string.link_delete,
                    secondIcon = Icons.Rounded.DeleteForever,
                    secondColor = Red400,
                    onFirst = {
                        viewModel.prepareLinkedOperation()
                        onNavigate(Destination.Join.route)
                    },
                    onSecond = {
                        viewModel.prepareLinkedOperation()
                        onNavigate(Destination.Delete.route)
                    },
                )
            }

            if (state.runtime.status != SyncEngineStatus.IDLE) {
                RuntimeCard(state)
            }

            if (!state.canStart && !running) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Gold400.copy(alpha = .08f),
                    border = BorderStroke(1.dp, Gold400.copy(alpha = .35f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = Gold400)
                        Text(
                            stringResource(R.string.sync_requirements_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = viewModel::startSync,
                    enabled = state.canStart && !running,
                    modifier = Modifier.weight(1.35f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal400, contentColor = Night900),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.start_sync), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = viewModel::pauseOrResume,
                    enabled = running,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(
                        if (state.runtime.status == SyncEngineStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        null,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(
                            if (state.runtime.status == SyncEngineStatus.PAUSED) R.string.resume_sync
                            else R.string.pause_execution
                        )
                    )
                }
                OutlinedButton(
                    onClick = viewModel::stopSync,
                    enabled = running,
                    modifier = Modifier.weight(.82f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red400),
                    border = BorderStroke(1.dp, Red400.copy(.55f)),
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.stop_sync))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SyncGroupRow(group: SyncGroupUiItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = group.selected, onCheckedChange = { onClick() })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (group.unread) TinyStatus(stringResource(R.string.unread), Blue400)
                if (group.locked) TinyStatus(stringResource(R.string.locked), Gold400)
                if (group.community) TinyStatus(stringResource(R.string.communities), Purple400)
                if (group.deleted) TinyStatus(stringResource(R.string.deleted), Red400)
                if (group.active && !group.deleted) TinyStatus(stringResource(R.string.active), Green400)
            }
        }
        Icon(
            if (group.community) Icons.Rounded.Hub else Icons.Rounded.Groups,
            null,
            tint = if (group.selected) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TinyStatus(text: String, color: Color) {
    Text(text, color = color, fontSize = 9.sp, maxLines = 1)
}

@Composable
private fun FilterRow(
    left: Triple<Int, ImageVector, GroupSelectionKind>,
    right: Triple<Int, ImageVector, GroupSelectionKind>,
    onSelect: (GroupSelectionKind) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterButton(left.first, left.second, { onSelect(left.third) }, Modifier.weight(1f))
        FilterButton(right.first, right.second, { onSelect(right.third) }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FilterButton(label: Int, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(label), maxLines = 1, fontSize = 11.sp)
    }
}

@Composable
private fun ContactModeRow(
    selected: ContactSyncMode,
    firstMode: ContactSyncMode,
    firstLabel: Int,
    secondMode: ContactSyncMode,
    secondLabel: Int,
    onSelect: (ContactSyncMode) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ContactModeButton(selected == firstMode, firstLabel, { onSelect(firstMode) }, Modifier.weight(1f))
        ContactModeButton(selected == secondMode, secondLabel, { onSelect(secondMode) }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ContactModeButton(selected: Boolean, label: Int, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 58.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Blue400.copy(alpha = .12f) else Color.Transparent,
            contentColor = if (selected) Blue400 else MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, if (selected) Blue400 else MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(stringResource(label), fontSize = 10.sp)
    }
}

@Composable
private fun OperationRow(
    selected: Boolean,
    firstText: Int,
    firstIcon: ImageVector,
    firstColor: Color,
    secondText: Int,
    secondIcon: ImageVector,
    secondColor: Color,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OperationButton(selected, firstText, firstIcon, firstColor, onFirst, Modifier.weight(1f))
        OperationButton(selected, secondText, secondIcon, secondColor, onSecond, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun OperationButton(
    enabled: Boolean,
    text: Int,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.dp, if (enabled) color.copy(.55f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(text), fontSize = 11.sp)
    }
}

@Composable
private fun RuntimeCard(state: SyncUiState) {
    val runtime = state.runtime
    val color = when (runtime.status) {
        SyncEngineStatus.COMPLETED -> Green400
        SyncEngineStatus.ERROR -> Red400
        SyncEngineStatus.PAUSED -> Gold400
        SyncEngineStatus.STOPPED -> Red400
        else -> Cyan400
    }
    GlassCard(accent = color) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.sync_live_status))
                Text(
                    runtime.message ?: runtime.status.name,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                )
                runtime.currentGroupName?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        stringResource(R.string.sync_current_group_value, it),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                when (runtime.status) {
                    SyncEngineStatus.COMPLETED -> Icons.Rounded.CheckCircle
                    SyncEngineStatus.ERROR -> Icons.Rounded.Error
                    SyncEngineStatus.PAUSED -> Icons.Rounded.PauseCircle
                    else -> Icons.Rounded.Sync
                },
                null,
                tint = color,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuntimeStat(stringResource(R.string.sync_discovered), runtime.discoveredCount.toString(), Modifier.weight(1f))
            RuntimeStat(stringResource(R.string.sync_new), runtime.newCount.toString(), Modifier.weight(1f))
            RuntimeStat(stringResource(R.string.sync_screens), runtime.processedScreens.toString(), Modifier.weight(1f))
        }
        if (runtime.status == SyncEngineStatus.VERIFYING_END) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (runtime.consecutiveEndPasses / 2f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.sync_end_verification, runtime.consecutiveEndPasses, 2),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
        runtime.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Red400, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RuntimeStat(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = Cyan400, fontSize = 17.sp)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}
