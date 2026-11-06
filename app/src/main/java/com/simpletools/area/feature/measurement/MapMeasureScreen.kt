package com.simpletools.area.feature.measurement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpletools.area.R
import com.simpletools.area.core.geometry.AreaUnit
import com.simpletools.area.core.geometry.UnitFormat
import com.simpletools.area.core.maps.MeasureMap
import com.simpletools.area.core.maps.MapStyles
import com.simpletools.area.ui.AppTopBar
import com.simpletools.area.ui.AreaUnitSelector
import com.simpletools.area.ui.DisclaimerText
import com.simpletools.area.ui.LOCATION_PERMISSIONS
import com.simpletools.area.ui.NameNotesDialog
import com.simpletools.area.ui.polygonErrorText
import com.simpletools.area.ui.theme.isDark
import kotlinx.coroutines.launch

@Composable
fun MapMeasureScreen(vm: MapMeasureViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val points by vm.points.collectAsState()
    val selected by vm.selected.collectAsState()
    val metrics by vm.metrics.collectAsState()
    val unit by vm.areaUnit.collectAsState()
    val canUndo by vm.undoAvailable.collectAsState()
    val canRedo by vm.redoAvailable.collectAsState()
    val user by vm.userLocation.collectAsState()
    val camera by vm.cameraRequest.collectAsState()
    val settings by vm.settings.collectAsState()
    val satellite by vm.satellite.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSave by rememberSaveable { mutableStateOf(false) }
    val isArea = vm.mode == MeasureMode.AREA

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) { vm.startLocation(); vm.recenterOnUser() }
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.location_denied_manual_ok)) }
    }
    DisposableEffect(Unit) {
        vm.startLocation()
        onDispose { vm.stopLocation() }
    }

    val satUrl = settings.satelliteTileUrl.takeIf { MapStyles.isValidTileTemplate(it) }
    val title = stringResource(if (isArea) R.string.map_title else R.string.distance_title)
    val messageNeedMore = stringResource(if (isArea) R.string.need_three_points else R.string.need_two_points)

    Scaffold(
        topBar = { AppTopBar(title, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MeasureMap(
                    modifier = Modifier.fillMaxSize(),
                    points = points, closed = isArea, selectedIndex = selected, editable = true,
                    userLocation = user, dark = isDark(settings.theme),
                    satelliteUrl = if (satellite) satUrl else null,
                    cameraRequest = camera, initialCenter = vm.lastCenter, initialZoom = vm.lastZoom,
                    onTap = vm::addPoint, onSelect = vm::select, onDragStart = vm::beginDrag,
                    onDragMove = vm::moveTo, onCameraIdle = vm::onCameraIdle,
                )
                // Réticule central pour « Ajouter un point au centre »
                Box(Modifier.align(Alignment.Center).size(26.dp)) {
                    Box(Modifier.align(Alignment.Center).size(width = 26.dp, height = 2.dp).background(MaterialTheme.colorScheme.primary))
                    Box(Modifier.align(Alignment.Center).size(width = 2.dp, height = 26.dp).background(MaterialTheme.colorScheme.primary))
                }
                Column(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapFab(Icons.Filled.MyLocation, stringResource(R.string.recenter_gps)) {
                        if (vm.hasLocationPermission()) { vm.startLocation(); vm.recenterOnUser() }
                        else permissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                    if (points.size >= 2) MapFab(Icons.Filled.FitScreen, stringResource(R.string.zoom_shape)) { vm.zoomToShape() }
                    if (satUrl != null) MapFab(Icons.Filled.Layers, stringResource(R.string.toggle_satellite)) { vm.toggleSatellite() }
                }
                Text(
                    stringResource(R.string.osm_attribution),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                )
            }

            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricsBlock(metrics, unit, vm::setUnit, messageNeedMore, locale)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ToolButton(Icons.Filled.Add, stringResource(R.string.add), true, Modifier.weight(1f)) { vm.addAtCenter() }
                        ToolButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), canUndo, Modifier.weight(1f)) { vm.undo() }
                        ToolButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo), canRedo, Modifier.weight(1f)) { vm.redo() }
                        ToolButton(Icons.Filled.Delete, stringResource(R.string.delete_point), selected != null, Modifier.weight(1f)) { vm.deleteSelected() }
                    }
                    val canFinish = when (metrics) {
                        is Metrics.Area -> true
                        is Metrics.Length -> points.size >= 2
                        else -> false
                    }
                    Button(onClick = { showSave = true }, enabled = canFinish, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.finish), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                    }
                    DisclaimerText()
                }
            }
        }
    }

    if (showSave) {
        val defaultName = stringResource(if (isArea) R.string.default_parcel_name else R.string.default_distance_name) +
            " " + java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale).format(java.util.Date())
        var existingName by remember { mutableStateOf<Pair<String, String>?>(null) }
        LaunchedEffect(Unit) { existingName = vm.existingParcel()?.let { it.name to it.notes } ?: (defaultName to "") }
        existingName?.let { (n, notes) ->
            NameNotesDialog(
                title = stringResource(R.string.save_measure),
                initialName = n, initialNotes = notes,
                confirmLabel = stringResource(R.string.save),
                onDismiss = { showSave = false },
                onConfirm = { name, nts ->
                    showSave = false
                    scope.launch {
                        val id = vm.save(name, nts)
                        if (id != null) onSaved(id)
                        else snackbar.showSnackbar(context.getString(R.string.save_failed))
                    }
                },
            )
        }
    }
}

@Composable
private fun MapFab(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) { Icon(icon, contentDescription = desc) }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, maxLines = 1, color = tint, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MetricsBlock(
    m: Metrics, unit: com.simpletools.area.core.geometry.AreaUnitChoice,
    onUnit: (com.simpletools.area.core.geometry.AreaUnitChoice) -> Unit,
    needMore: String, locale: java.util.Locale,
) {
    when (m) {
        is Metrics.Area -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.area), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(UnitFormat.formatArea(m.areaM2, unit, locale), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                AreaUnitSelector(unit, onUnit)
            }
            Text(
                stringResource(R.string.equivalent) + " " + UnitFormat.formatArea(m.areaM2, AreaUnit.SQUARE_METER, locale) + " · " +
                    UnitFormat.formatArea(m.areaM2, AreaUnit.HECTARE, locale) + "   " +
                    stringResource(R.string.perimeter) + " " + UnitFormat.formatLength(m.perimeterM, UnitFormat.autoLengthUnit(m.perimeterM), locale),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is Metrics.Length -> {
            Text(stringResource(R.string.total_distance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(UnitFormat.formatLength(m.lengthM, UnitFormat.autoLengthUnit(m.lengthM), locale), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        is Metrics.Invalid -> Text(polygonErrorText(m.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        is Metrics.Incomplete, Metrics.Empty -> Text(needMore, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
