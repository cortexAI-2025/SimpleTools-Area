package com.simpletools.area.feature.measurement

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.area.R
import com.simpletools.area.core.geometry.AreaUnit
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.UnitFormat
import com.simpletools.area.core.location.RecorderStatus
import com.simpletools.area.core.location.RejectReason
import com.simpletools.area.core.maps.CameraRequest
import com.simpletools.area.core.maps.MeasureMap
import com.simpletools.area.ui.AppTopBar
import com.simpletools.area.ui.DisclaimerText
import com.simpletools.area.ui.LOCATION_PERMISSIONS
import com.simpletools.area.ui.NameNotesDialog
import com.simpletools.area.ui.areaUnitLabel
import com.simpletools.area.ui.openAppSettings
import com.simpletools.area.ui.openLocationSettings
import com.simpletools.area.ui.polygonErrorText
import com.simpletools.area.ui.theme.isDark
import kotlinx.coroutines.launch

private val Good = Color(0xFF2E7D32)
private val Medium = Color(0xFFEF6C00)
private val Bad = Color(0xFFC62828)

@Composable
fun GpsMeasureScreen(vm: GpsMeasureViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val s by vm.state.collectAsState()
    val event by vm.event.collectAsState()
    val result by vm.result.collectAsState()
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var showSave by remember { mutableStateOf(false) }
    var cameraId by remember { mutableStateOf(0) }
    var camera by remember { mutableStateOf<CameraRequest?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        // La notification du service est facultative ; seule la localisation conditionne le démarrage.
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true) vm.start()
        else vm.start() // rejoue la vérification → événement PermissionDenied
    }
    fun requestAndStart() {
        if (vm.hasPermission()) vm.start()
        else {
            val perms = if (Build.VERSION.SDK_INT >= 33) LOCATION_PERMISSIONS + Manifest.permission.POST_NOTIFICATIONS else LOCATION_PERMISSIONS
            launcher.launch(perms)
        }
    }

    // Suivre le dernier point
    LaunchedEffect(s.lastFix) {
        s.lastFix?.let { camera = CameraRequest(++cameraId, target = it, zoom = 18.0) }
    }

    Scaffold(topBar = { AppTopBar(stringResource(if (vm.openTrack) R.string.area_gps_track else R.string.gps_title), onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MeasureMap(
                    modifier = Modifier.fillMaxSize(),
                    points = s.points, closed = !vm.openTrack && s.status == RecorderStatus.FINISHED, selectedIndex = null, editable = false,
                    userLocation = s.lastFix, dark = isDark(settings.theme), satelliteUrl = null,
                    cameraRequest = camera, initialCenter = s.lastFix ?: com.simpletools.area.core.geometry.GeoPoint(46.6, 2.4),
                    initialZoom = if (s.lastFix != null) 18.0 else 5.0,
                )
                AccuracyBadge(s.lastAccuracyM, settings.gpsMaxAccuracyM, Modifier.align(Alignment.TopStart).padding(12.dp))
            }
            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(stringResource(R.string.distance_walked), UnitFormat.formatLength(s.distanceM, UnitFormat.autoLengthUnit(s.distanceM), locale), Modifier.weight(1f))
                        Stat(stringResource(R.string.points), s.points.size.toString(), Modifier.weight(0.6f))
                        if (!vm.openTrack) Stat(
                            stringResource(R.string.area),
                            s.areaM2?.let { UnitFormat.formatArea(it, AreaUnitChoice.AUTO, locale) } ?: "—",
                            Modifier.weight(1f),
                        )
                    }
                    s.areaM2?.let {
                        Text(
                            stringResource(R.string.equivalent) + " " + UnitFormat.formatArea(it, AreaUnit.SQUARE_METER, locale) + " · " +
                                UnitFormat.formatArea(it, AreaUnit.HECTARE, locale) + "   " + stringResource(R.string.perimeter) + " " +
                                UnitFormat.formatLength(s.perimeterM ?: 0.0, UnitFormat.autoLengthUnit(s.perimeterM ?: 0.0), locale),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    StatusLine(s.status, s.lastReject, s.providerEnabled, s.lastAccuracyM, s.points.size, vm.openTrack)
                    if (s.lastError != null) Text(stringResource(R.string.err_self_intersecting), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Controls(
                        status = s.status, enoughPoints = s.points.size >= if (vm.openTrack) 2 else 3,
                        onStart = ::requestAndStart, onPause = vm::pause, onResume = vm::resume,
                        onFinish = vm::finish, onDiscard = vm::discard, onSave = { showSave = true },
                        canSave = result != null,
                    )
                    DisclaimerText()
                }
            }
        }
    }

    when (event) {
        GpsUiEvent.PermissionDenied -> AlertDialogSimple(
            stringResource(R.string.perm_denied_title), stringResource(R.string.perm_denied_body),
            stringResource(R.string.open_settings), { vm.consumeEvent(); openAppSettings(context) }, { vm.consumeEvent() },
        )
        GpsUiEvent.GpsDisabled -> AlertDialogSimple(
            stringResource(R.string.gps_off_title), stringResource(R.string.gps_off_body),
            stringResource(R.string.open_location_settings), { vm.consumeEvent(); openLocationSettings(context) }, { vm.consumeEvent() },
        )
        is GpsUiEvent.Invalid -> AlertDialogSimple(
            stringResource(R.string.measure_invalid), if (vm.openTrack) stringResource(R.string.area_gps_track_minimum) else polygonErrorText((event as GpsUiEvent.Invalid).error),
            stringResource(R.string.new_measure), { vm.consumeEvent(); vm.discard() }, { vm.consumeEvent() },
        )
        GpsUiEvent.None -> {}
    }

    if (showSave && result != null) {
        val name = stringResource(R.string.default_gps_name) + " " +
            java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale).format(java.util.Date())
        NameNotesDialog(
            title = stringResource(R.string.save_measure), initialName = name, initialNotes = "",
            confirmLabel = stringResource(R.string.save), onDismiss = { showSave = false },
            onConfirm = { n, notes ->
                showSave = false
                scope.launch { vm.save(n, notes, settings.areaUnit)?.let(onSaved) }
            },
        )
    }
}

@Composable
private fun AlertDialogSimple(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
private fun AccuracyBadge(acc: Float?, maxOk: Double, modifier: Modifier) {
    val (color, text) = when {
        acc == null -> Bad to stringResource(R.string.gps_waiting)
        acc <= maxOk * 0.5 -> Good to stringResource(R.string.gps_accuracy_good, acc.toInt())
        acc <= maxOk -> Medium to stringResource(R.string.gps_accuracy_ok, acc.toInt())
        else -> Bad to stringResource(R.string.gps_accuracy_poor, acc.toInt())
    }
    Row(
        modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatusLine(status: RecorderStatus, reject: RejectReason?, providerOn: Boolean, acc: Float?, n: Int, openTrack: Boolean) {
    val msg = when {
        !providerOn -> stringResource(R.string.gps_signal_lost)
        status == RecorderStatus.IDLE -> stringResource(if (openTrack) R.string.area_gps_track_idle else R.string.gps_hint_idle)
        status == RecorderStatus.PAUSED -> stringResource(R.string.gps_paused)
        status == RecorderStatus.FINISHED -> stringResource(if (openTrack) R.string.area_gps_track_finished else R.string.gps_finished)
        reject == RejectReason.POOR_ACCURACY || reject == RejectReason.NO_ACCURACY -> stringResource(R.string.gps_reject_accuracy)
        reject == RejectReason.OUTLIER_JUMP -> stringResource(R.string.gps_reject_jump)
        n == 0 -> stringResource(if (openTrack) R.string.area_gps_track_walk else R.string.gps_walk_hint)
        else -> stringResource(R.string.gps_recording)
    }
    Text(msg, style = MaterialTheme.typography.bodySmall, color = if (!providerOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Controls(
    status: RecorderStatus, enoughPoints: Boolean, canSave: Boolean,
    onStart: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit,
) {
    when (status) {
        RecorderStatus.IDLE -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PlayArrow, null); Text(stringResource(R.string.start), Modifier.padding(start = 8.dp))
        }
        RecorderStatus.RECORDING, RecorderStatus.PAUSED -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == RecorderStatus.RECORDING) OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Pause, null, Modifier.size(18.dp)); Text(stringResource(R.string.pause), Modifier.padding(start = 6.dp), maxLines = 1)
            } else OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Text(stringResource(R.string.resume), Modifier.padding(start = 6.dp), maxLines = 1)
            }
            Button(onClick = onFinish, enabled = enoughPoints, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Text(stringResource(R.string.finish), Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
        RecorderStatus.FINISHED -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.new_measure), maxLines = 1) }
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Text(stringResource(R.string.save), Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
    }
}
