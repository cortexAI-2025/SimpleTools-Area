package com.simpletools.area.feature.projects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.simpletools.area.R
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.UnitFormat
import com.simpletools.area.feature.export.Exporters
import com.simpletools.area.feature.export.ShareHelper
import com.simpletools.area.feature.library.ParcelRepository
import com.simpletools.area.ui.AppTopBar
import com.simpletools.area.ui.DisclaimerText
import com.simpletools.area.ui.NameNotesDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectsScreen(store: ProjectStore, repo: ParcelRepository, active: Boolean, onBack: () -> Unit) {
    val projects by store.projects.collectAsState(initial = emptyList())
    val measurements by remember(repo) { repo.observeAll() }.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val project = projects.find { it.id == selectedId }
    var showName by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<((ParcelEntity) -> Unit)?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var compare by remember(selectedId) { mutableStateOf(setOf<Int>()) }
    var busy by remember { mutableStateOf(false) }
    var coefficient by rememberSaveable(selectedId) { mutableStateOf("") }
    var parts by rememberSaveable(selectedId) { mutableStateOf("") }

    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.area_project_error)) }
            finally { busy = false }
        }
    }

    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) action {
            selectedId = withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openInputStream(uri)).use { store.restore(it) }
            }
        }
    }

    fun export(project: AreaProject, backup: Boolean) = action {
        if (!store.canExport()) return@action
        val mime = if (backup) "application/json" else "application/vnd.google-earth.kml+xml"
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            File.createTempFile("area-project-", if (backup) ".json" else ".kml", dir).apply {
                writeText(if (backup) ProjectBackup.encode(project) else projectKml(project))
            }
        }
        if (store.canExport()) ShareHelper.shareFile(context, file, mime, project.name)
    }

    fun formatArea(value: Double) = UnitFormat.formatArea(value, UnitFormat.autoAreaUnit(value), locale)
    fun formatLength(value: Double) = UnitFormat.formatLength(value, UnitFormat.autoLengthUnit(value), locale)

    Scaffold(
        topBar = { AppTopBar(project?.name ?: stringResource(R.string.area_projects), if (project == null) onBack else { { selectedId = null } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!active) item { Text(stringResource(R.string.area_projects_locked)) }
            if (project == null || !active) {
                if (active) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showName = true }, enabled = !busy) { Text(stringResource(R.string.area_project_new)) }
                        TextButton(onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, enabled = !busy) {
                            Text(stringResource(R.string.area_project_import))
                        }
                    }
                }
                items(projects, key = { it.id }) { entry ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedId = entry.id }, enabled = active) { Text(entry.name) }
                        TextButton(onClick = { deleteId = entry.id }, enabled = !busy) { Text(stringResource(R.string.delete)) }
                    }
                }
            } else {
                item {
                    Text(stringResource(R.string.area_cultivable, formatArea(project.areaM2)), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.area_total_perimeter, formatLength(project.perimeterM)))
                    Text(stringResource(R.string.area_fence, formatLength(project.parcels.sumOf { it.fenceM })))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { showName = true }, label = { Text(stringResource(R.string.rename)) })
                        AssistChip(onClick = {
                            action { selectedId = store.create((project.name + context.getString(R.string.copy_suffix)).take(200), project.parcels) }
                        }, label = { Text(stringResource(R.string.duplicate)) })
                        AssistChip(onClick = { export(project, true) }, label = { Text(stringResource(R.string.area_project_backup)) })
                        AssistChip(onClick = { export(project, false) }, enabled = project.parcels.isNotEmpty(), label = { Text("KML") })
                        AssistChip(onClick = {
                            picker = { source ->
                                action {
                                    val parcel = withContext(Dispatchers.Default) { projectParcel(source.name.take(200), "", source.points, emptyList()) }
                                    store.edit(project.id) { it.copy(parcels = it.parcels + parcel) }
                                }
                            }
                        }, label = { Text(stringResource(R.string.area_project_add_parcel)) })
                    }
                }
                items(project.parcels.size) { index ->
                    val parcel = project.parcels[index]
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        HorizontalDivider()
                        Text(parcel.name, style = MaterialTheme.typography.titleMedium)
                        if (parcel.category.isNotBlank()) Text(parcel.category)
                        Text("${formatArea(parcel.netAreaM2)} · ${formatLength(parcel.perimeterM)}")
                        if (parcel.exclusions.isNotEmpty()) Text(stringResource(R.string.area_exclusion_count, parcel.exclusions.size))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(onClick = { editing = index }, label = { Text(stringResource(R.string.area_edit)) })
                            AssistChip(onClick = {
                                picker = { source ->
                                    action {
                                        store.edit(project.id) { latest ->
                                            latest.editParcel(index) { current ->
                                                projectParcel(current.name, current.category, current.boundary, current.exclusions + listOf(source.points))
                                            }
                                        }
                                    }
                                }
                            }, label = { Text(stringResource(R.string.area_exclude)) })
                            if (parcel.exclusions.isNotEmpty()) AssistChip(onClick = {
                                action { store.edit(project.id) { it.editParcel(index) { p -> p.copy(exclusions = p.exclusions.dropLast(1)) } } }
                            }, label = { Text(stringResource(R.string.area_undo_exclusion)) })
                            Checkbox(checked = index in compare, enabled = index in compare || compare.size < 2,
                                onCheckedChange = { compare = if (it) compare + index else compare - index })
                        }
                    }
                }
                item {
                    val compared = compare.mapNotNull(project.parcels::getOrNull)
                    Text(stringResource(R.string.area_compare))
                    if (compared.size == 2) {
                        Text(stringResource(R.string.area_comparison, compared[0].name, compared[1].name,
                            formatArea(abs(compared[0].netAreaM2 - compared[1].netAreaM2)),
                            formatLength(abs(compared[0].perimeterM - compared[1].perimeterM))))
                    }
                    project.parcels.filter { it.category.isNotBlank() }.groupBy { it.category }.toSortedMap().forEach { (category, parcels) ->
                        Text(stringResource(R.string.area_category_total, category, formatArea(parcels.sumOf { it.netAreaM2 })))
                    }
                    OutlinedTextField(coefficient, { coefficient = it }, label = { Text(stringResource(R.string.area_coefficient)) }, singleLine = true)
                    coefficient.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && (it * project.areaM2).isFinite() }?.let {
                        val amount = java.text.NumberFormat.getNumberInstance(locale).format(it * project.areaM2)
                        Text(stringResource(R.string.area_quantity, amount))
                    }
                    OutlinedTextField(parts, { parts = it }, label = { Text(stringResource(R.string.area_division_count)) }, singleLine = true)
                    parts.toIntOrNull()?.takeIf { it >= 2 }?.let {
                        Text(stringResource(R.string.area_division_result, formatArea(project.areaM2 / it)))
                    }
                    DisclaimerText()
                }
            }
        }
    }

    if (showName && active) NameNotesDialog(
        title = stringResource(if (project == null) R.string.area_project_new else R.string.rename),
        initialName = project?.name.orEmpty(), initialNotes = "", confirmLabel = stringResource(R.string.save),
        onDismiss = { showName = false },
        onConfirm = { name, _ ->
            showName = false
            action {
                if (project == null) selectedId = store.create(name)
                else store.edit(project.id) { it.copy(name = name) }
            }
        },
    )
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, projects.find { it.id == id }?.name.orEmpty())) },
            confirmButton = { TextButton(onClick = { deleteId = null; action { store.delete(id) } }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    picker?.takeIf { active }?.let { choose ->
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(stringResource(R.string.area_pick_measure)) },
            text = {
                LazyColumn {
                    val candidates = measurements.filter { it.type != MeasureType.DISTANCE && it.points.size >= 3 }
                    if (candidates.isEmpty()) item { Text(stringResource(R.string.area_no_saved_polygons)) }
                    items(candidates, key = { it.id }) { parcel ->
                        TextButton(onClick = { picker = null; choose(parcel) }) { Text(parcel.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    editing?.takeIf { active }?.let { index ->
        val parcel = project?.parcels?.getOrNull(index)
        if (project != null && parcel != null) {
            var name by remember(index) { mutableStateOf(parcel.name) }
            var category by remember(index) { mutableStateOf(parcel.category) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(stringResource(R.string.area_edit_parcel)) },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it.take(200) }, label = { Text(stringResource(R.string.parcel_name)) }, singleLine = true)
                        OutlinedTextField(category, { category = it.take(100) }, label = { Text(stringResource(R.string.area_category)) }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        editing = null
                        action { store.edit(project.id) { it.editParcel(index) { p -> p.copy(name = name.trim(), category = category.trim()) } } }
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

private fun AreaProject.editParcel(index: Int, update: (ProjectParcel) -> ProjectParcel) =
    copy(parcels = parcels.toMutableList().apply { this[index] = update(this[index]) })

internal fun projectKml(project: AreaProject): String = Exporters.kmlDocument(project.parcels.joinToString("") { parcel ->
    val boundary = Exporters.kmlRing(Exporters.closedCcwRing(parcel.boundary))
    val holes = parcel.exclusions.joinToString("") {
        "<innerBoundaryIs>${Exporters.kmlRing(Exporters.closedCcwRing(it).reversed())}</innerBoundaryIs>"
    }
    "<Placemark><name>${Exporters.xmlText(parcel.name)}</name><Polygon><outerBoundaryIs>$boundary</outerBoundaryIs>$holes</Polygon></Placemark>"
})
