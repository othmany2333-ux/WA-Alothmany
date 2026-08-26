package com.alothmany.wa.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alothmany.wa.R
import com.alothmany.wa.core.model.WhatsAppSourceType
import com.alothmany.wa.core.navigation.Destination
import com.alothmany.wa.core.ui.components.*
import com.alothmany.wa.core.ui.theme.*

private data class SourceCardData(val type: WhatsAppSourceType, val title: Int, val icon: ImageVector, val color: Color)

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()
    val sources = listOf(
        SourceCardData(WhatsAppSourceType.MAIN, R.string.wa_main, Icons.Rounded.Chat, Green400),
        SourceCardData(WhatsAppSourceType.BUSINESS, R.string.wa_business, Icons.Rounded.BusinessCenter, Teal400),
        SourceCardData(WhatsAppSourceType.DUAL, R.string.wa_dual, Icons.Rounded.ContentCopy, Purple400),
        SourceCardData(WhatsAppSourceType.WORK, R.string.wa_work, Icons.Rounded.Work, Blue400),
        SourceCardData(WhatsAppSourceType.SECURE, R.string.wa_secure, Icons.Rounded.Lock, Gold400),
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = Teal400.copy(alpha = .13f), border = BorderStroke(1.dp, Teal400.copy(.4f))) {
                Icon(Icons.Rounded.Bolt, null, tint = Gold400, modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.app_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.system_running)) },
                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, tint = Green400, modifier = Modifier.size(16.dp)) },
            )
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatusPill(stringResource(R.string.shizuku), stringResource(R.string.not_configured), Icons.Rounded.Terminal, Gold400)
            StatusPill(stringResource(R.string.accessibility), stringResource(R.string.not_configured), Icons.Rounded.AccessibilityNew, Blue400)
            StatusPill(stringResource(R.string.overlay), stringResource(R.string.not_configured), Icons.Rounded.Layers, Purple400)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.whatsapp_sources), state.sources.toString(), Icons.Rounded.Apps, Gold400, Modifier.weight(1f))
            StatCard(stringResource(R.string.groups), state.groups.toString(), Icons.Rounded.Groups, Green400, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.communities), state.communities.toString(), Icons.Rounded.Hub, Purple400, Modifier.weight(1f))
            StatCard(stringResource(R.string.links), state.links.toString(), Icons.Rounded.Link, Blue400, Modifier.weight(1f))
        }

        SectionTitle(stringResource(R.string.select_whatsapp_source))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sources.forEach { source ->
                val selected = state.preferences.selectedSource == source.type
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setSource(source.type) },
                    label = { Text(stringResource(source.title)) },
                    leadingIcon = { Icon(source.icon, null, tint = if (selected) source.color else MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }

        GlassCard(accent = Cyan400) {
            SectionTitle(stringResource(R.string.smart_speed_settings))
            Text(stringResource(R.string.navigation_speed), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = state.preferences.navigationSpeed,
                    onValueChange = viewModel::setSpeed,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.speed_percent, (state.preferences.navigationSpeed * 100).toInt()), color = Cyan400, modifier = Modifier.width(52.dp))
            }
            Text(stringResource(R.string.wait_time), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = state.preferences.waitSeconds,
                    onValueChange = viewModel::setWait,
                    valueRange = 0.5f..10f,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.seconds_value, state.preferences.waitSeconds), color = Cyan400, modifier = Modifier.width(60.dp))
            }
            SettingSwitchRow(stringResource(R.string.super_turbo), state.preferences.superTurbo, viewModel::setTurbo)
            SettingSwitchRow(stringResource(R.string.skip_non_essential), state.preferences.skipNonEssential, viewModel::setSkip)
            SettingSwitchRow(stringResource(R.string.smart_link_read), state.preferences.smartLinkRead, viewModel::setSmartRead)
            Text(stringResource(R.string.balanced_speed_tip), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }

        SectionTitle(stringResource(R.string.automation))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureTile(stringResource(R.string.sync), stringResource(R.string.coming_next_phase), Icons.Rounded.Sync, Teal400, { onNavigate(Destination.Sync.route) }, Modifier.weight(1f))
            FeatureTile(stringResource(R.string.join), stringResource(R.string.coming_next_phase), Icons.Rounded.GroupAdd, Green400, { onNavigate(Destination.Join.route) }, Modifier.weight(1f))
            FeatureTile(stringResource(R.string.check), stringResource(R.string.coming_next_phase), Icons.Rounded.Policy, Gold400, { onNavigate(Destination.Check.route) }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureTile(stringResource(R.string.extract), stringResource(R.string.coming_next_phase), Icons.Rounded.Link, Blue400, { onNavigate(Destination.Extract.route) }, Modifier.weight(1f))
            FeatureTile(stringResource(R.string.publish), stringResource(R.string.coming_next_phase), Icons.Rounded.Campaign, Purple400, { onNavigate(Destination.Publish.route) }, Modifier.weight(1f))
            FeatureTile(stringResource(R.string.delete), stringResource(R.string.coming_next_phase), Icons.Rounded.DeleteSweep, Red400, { onNavigate(Destination.Delete.route) }, Modifier.weight(1f))
        }

        GlassCard(accent = Green400) {
            SectionTitle(stringResource(R.string.execution_control))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNavigate(Destination.Sync.route) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Teal400, contentColor = Night900)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.start_execution))
                }
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Pause, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.pause_execution))
                }
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Red400)) {
                    Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.stop_execution))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Info, null, tint = Cyan400)
                Text(stringResource(R.string.foundation_message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
