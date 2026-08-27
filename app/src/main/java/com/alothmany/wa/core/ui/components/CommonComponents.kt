package com.alothmany.wa.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alothmany.wa.core.ui.theme.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent?.copy(alpha = .55f) ?: MaterialTheme.colorScheme.outline.copy(alpha = .18f)),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun StatusPill(
    title: String,
    status: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val interactiveModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = interactiveModifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .85f),
        border = BorderStroke(1.dp, color.copy(alpha = .35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(status, color = color, fontSize = 11.sp, maxLines = 1)
                if (!actionText.isNullOrBlank()) {
                    Text(
                        actionText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = .14f)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Column {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FeatureTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
        accent = color,
    ) {
        Surface(shape = RoundedCornerShape(15.dp), color = color.copy(alpha = .15f)) {
            Icon(icon, null, tint = color, modifier = Modifier.padding(11.dp).size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Cyan400, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}
