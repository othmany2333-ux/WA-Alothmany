package com.alothmany.wa.feature.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alothmany.wa.R
import com.alothmany.wa.core.ui.components.GlassCard
import com.alothmany.wa.core.ui.theme.Cyan400

@Composable
fun FeaturePlaceholderScreen(@StringRes title: Int, icon: ImageVector, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(20.dp))
        GlassCard(accent = Cyan400, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null, tint = Cyan400, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.feature_not_active), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.coming_next_phase), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
