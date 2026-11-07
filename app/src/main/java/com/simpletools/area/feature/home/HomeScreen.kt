package com.simpletools.area.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.area.R
import com.simpletools.area.ui.DisclaimerText

@Composable
fun HomeScreen(
    onMap: () -> Unit,
    onGps: () -> Unit,
    onCalculator: () -> Unit,
    onDistance: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onPro: (() -> Unit)? = null,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF0D47A1), Color(0xFF1976D2))), RoundedCornerShape(24.dp),
                ).padding(20.dp),
            ) {
                Column(Modifier.fillMaxWidth(0.82f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6E4FF))
                    Text(
                        stringResource(R.string.home_tagline),
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFD6E4FF),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings), tint = Color.White)
                }
            }
            HomeTile(Icons.Filled.Map, R.string.home_map, R.string.home_map_desc, onMap)
            HomeTile(Icons.Filled.DirectionsWalk, R.string.home_gps, R.string.home_gps_desc, onGps)
            HomeTile(Icons.Filled.Calculate, R.string.home_calc, R.string.home_calc_desc, onCalculator)
            HomeTile(Icons.Filled.Straighten, R.string.home_distance, R.string.home_distance_desc, onDistance)
            HomeTile(Icons.Filled.Folder, R.string.home_library, R.string.home_library_desc, onLibrary)
            onPro?.let { HomeTile(Icons.Filled.Folder, R.string.area_pro, R.string.area_pro_home, it) }
            DisclaimerText(Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun HomeTile(icon: ImageVector, title: Int, desc: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
