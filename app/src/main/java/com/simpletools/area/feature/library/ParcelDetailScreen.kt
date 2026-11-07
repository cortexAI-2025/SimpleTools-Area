package com.simpletools.area.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.area.R
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.AreaUnit
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.UnitFormat
import com.simpletools.area.core.maps.CameraRequest
import com.simpletools.area.core.maps.MeasureMap
import com.simpletools.area.core.settings.AppSettings
import com.simpletools.area.core.settings.SettingsStore
import com.simpletools.area.feature.export.ExportFormat
import com.simpletools.area.feature.export.ShareHelper
import com.simpletools.area.ui.AppTopBar
import com.simpletools.area.ui.AreaUnitSelector
import com.simpletools.area.ui.DisclaimerText
import com.simpletools.area.ui.LabeledValue
import com.simpletools.area.ui.NameNotesDialog
import com.simpletools.area.ui.theme.isDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParcelDetailViewModel(private val id: Long, private val repo: ParcelRepository, settings: SettingsStore) : ViewModel() {
    val parcel: StateFlow<ParcelEntity?> = repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    fun rename(name: String, notes: String) = viewModelScope.launch { repo.rename(id, name, notes) }
    fun setUnit(u: AreaUnitChoice) = viewModelScope.launch { parcel.value?.let { repo.update(it.copy(displayUnit = u.name)) } }
    suspend fun duplicate(suffix: String) = repo.duplicate(id, suffix)
    suspend fun delete() = repo.delete(id)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParcelDetailScreen(vm: ParcelDetailViewModel, onBack: () -> Unit, onEditGeometry: (Long, Boolean) -> Unit, onOpen: (Long) -> Unit, hasPro: () -> Boolean = { false }, onPro: () -> Unit = {}) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val p by vm.parcel.collectAsState()
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    val parcel = p

    Scaffold(
        topBar = { AppTopBar(parcel?.name ?: "", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (parcel == null) { Box(Modifier.fillMaxSize().padding(pad)) {}; return@Scaffold }
        val choice = runCatching { AreaUnitChoice.valueOf(parcel.displayUnit) }.getOrDefault(AreaUnitChoice.AUTO)
        val isDistance = parcel.type == MeasureType.DISTANCE
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            MeasureMap(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                points = parcel.points, closed = !isDistance, selectedIndex = null, editable = false,
                userLocation = null, dark = isDark(settings.theme), satelliteUrl = null,
                cameraRequest = CameraRequest(1, fitPoints = parcel.points, target = parcel.points.firstOrNull()),
                initialCenter = parcel.points.firstOrNull() ?: com.simpletools.area.core.geometry.GeoPoint(46.6, 2.4),
                initialZoom = 16.0,
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isDistance) {
                            Column {
                                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.area), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(UnitFormat.formatArea(parcel.areaM2, choice, locale), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    AreaUnitSelector(choice, vm::setUnit)
                                }
                            }
                            LabeledValue(stringResource(R.string.equivalent), UnitFormat.formatArea(parcel.areaM2, AreaUnit.SQUARE_METER, locale) + " · " + UnitFormat.formatArea(parcel.areaM2, AreaUnit.HECTARE, locale))
                            LabeledValue(stringResource(R.string.perimeter), UnitFormat.formatLength(parcel.perimeterM, UnitFormat.autoLengthUnit(parcel.perimeterM), locale))
                        } else {
                            LabeledValue(stringResource(R.string.total_distance), UnitFormat.formatLength(parcel.perimeterM, UnitFormat.autoLengthUnit(parcel.perimeterM), locale))
                        }
                        LabeledValue(stringResource(R.string.pdf_type), ShareHelper.typeName(context, parcel.type))
                        LabeledValue(stringResource(R.string.points), parcel.points.size.toString())
                        LabeledValue(stringResource(R.string.pdf_date), java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale).format(java.util.Date(parcel.createdAt)))
                        if (parcel.notes.isNotBlank()) {
                            Text(stringResource(R.string.notes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(parcel.notes)
                        }
                    }
                }
                AssistChip(onClick = {
                    val result = buildString {
                        appendLine(parcel.name)
                        if (!isDistance) appendLine(UnitFormat.formatArea(parcel.areaM2, choice, locale))
                        append(UnitFormat.formatLength(parcel.perimeterM, UnitFormat.autoLengthUnit(parcel.perimeterM), locale))
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, result)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share)))
                }, label = { Text(stringResource(R.string.area_share_text)) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = { showRename = true }, label = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) })
                    AssistChip(onClick = { onEditGeometry(parcel.id, isDistance) }, label = { Text(stringResource(R.string.edit_geometry)) }, leadingIcon = { Icon(Icons.Filled.EditLocationAlt, null, Modifier.size(18.dp)) })
                    AssistChip(onClick = {
                        scope.launch { vm.duplicate(context.getString(R.string.copy_suffix))?.let { onOpen(it) } }
                    }, label = { Text(stringResource(R.string.duplicate)) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)) })
                    Box {
                        AssistChip(onClick = { showExport = true }, label = { Text(stringResource(R.string.export)) }, leadingIcon = { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)) })
                        DropdownMenu(expanded = showExport, onDismissRequest = { showExport = false }) {
                            ExportFormat.entries.filter { it != ExportFormat.GPX || isDistance }.forEach { f ->
                                DropdownMenuItem(text = { Text(if (f.pro) "${f.name} · PRO" else f.name) }, onClick = {
                                    showExport = false
                                    if (f.pro && !hasPro()) onPro()
                                    else scope.launch {
                                        try {
                                            val file = withContext(Dispatchers.IO) { ShareHelper.export(context, parcel, f, locale) }
                                            if (f.pro && !hasPro()) onPro()
                                            else ShareHelper.shareFile(context, file, f.mime, parcel.name)
                                        } catch (failure: java.io.IOException) {
                                            snackbar.showSnackbar(context.getString(R.string.export_failed))
                                        } catch (failure: IllegalArgumentException) {
                                            snackbar.showSnackbar(context.getString(R.string.export_failed))
                                        }
                                    }
                                })
                            }
                        }
                    }
                    AssistChip(onClick = { showDelete = true }, label = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) })
                }
                DisclaimerText()
            }
        }
        if (showRename) NameNotesDialog(
            title = stringResource(R.string.rename), initialName = parcel.name, initialNotes = parcel.notes,
            confirmLabel = stringResource(R.string.save), onDismiss = { showRename = false },
            onConfirm = { n, notes -> showRename = false; vm.rename(n, notes) },
        )
        if (showDelete) AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, parcel.name)) },
            confirmButton = { TextButton(onClick = { showDelete = false; scope.launch { vm.delete(); onBack() } }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
