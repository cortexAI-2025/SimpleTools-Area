package com.simpletools.area.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simpletools.area.R
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.AreaUnit
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.UnitFormat
import com.simpletools.area.feature.export.ShareHelper
import com.simpletools.area.ui.AppTopBar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val repo: ParcelRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val items: StateFlow<List<ParcelEntity>?> = query.flatMapLatest { repo.search(it) as Flow<List<ParcelEntity>?> }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun LibraryScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val items by vm.items.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.library_title), onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = q, onValueChange = { q = it; vm.query.value = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            val list = items
            if (list != null && list.isEmpty()) {
                Text(
                    stringResource(if (q.isBlank()) R.string.library_empty else R.string.library_no_result),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(list ?: emptyList(), key = { it.id }) { p -> ParcelRow(p) { onOpen(p.id) } }
            }
        }
    }
}

@Composable
private fun ParcelRow(p: ParcelEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val choice = runCatching { AreaUnitChoice.valueOf(p.displayUnit) }.getOrDefault(AreaUnitChoice.AUTO)
    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale).format(java.util.Date(p.createdAt))
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${ShareHelper.typeName(context, p.type)} · $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            val main = if (p.type == MeasureType.DISTANCE) UnitFormat.formatLength(p.perimeterM, UnitFormat.autoLengthUnit(p.perimeterM), locale)
            else UnitFormat.formatArea(p.areaM2, choice, locale)
            Text(main, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
    }
}
