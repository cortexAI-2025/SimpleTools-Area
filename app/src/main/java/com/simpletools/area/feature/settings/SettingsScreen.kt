package com.simpletools.area.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.area.BuildConfig
import com.simpletools.area.R
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.LengthUnitChoice
import com.simpletools.area.core.maps.MapStyles
import com.simpletools.area.core.settings.AppLanguage
import com.simpletools.area.core.settings.AppSettings
import com.simpletools.area.core.settings.SettingsStore
import com.simpletools.area.core.settings.ThemeMode
import com.simpletools.area.ui.AppTopBar
import com.simpletools.area.ui.AreaUnitSelector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    fun update(t: (AppSettings) -> AppSettings) { viewModelScope.launch { store.update(t) } }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    Scaffold(topBar = { AppTopBar(stringResource(R.string.settings), onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section(R.string.set_language) {
                Radio(R.string.lang_fr, s.language == AppLanguage.FRENCH) { vm.update { it.copy(language = AppLanguage.FRENCH) } }
                Radio(R.string.lang_en, s.language == AppLanguage.ENGLISH) { vm.update { it.copy(language = AppLanguage.ENGLISH) } }
                Radio(R.string.lang_system, s.language == AppLanguage.SYSTEM) { vm.update { it.copy(language = AppLanguage.SYSTEM) } }
            }
            Section(R.string.set_theme) {
                Radio(R.string.theme_system, s.theme == ThemeMode.SYSTEM) { vm.update { it.copy(theme = ThemeMode.SYSTEM) } }
                Radio(R.string.theme_light, s.theme == ThemeMode.LIGHT) { vm.update { it.copy(theme = ThemeMode.LIGHT) } }
                Radio(R.string.theme_dark, s.theme == ThemeMode.DARK) { vm.update { it.copy(theme = ThemeMode.DARK) } }
            }
            Section(R.string.set_units) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.area))
                    AreaUnitSelector(s.areaUnit, onSelect = { c -> vm.update { st -> st.copy(areaUnit = c) } })
                }
                Text(stringResource(R.string.length), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Radio(R.string.unit_auto, s.lengthUnit == LengthUnitChoice.AUTO) { vm.update { it.copy(lengthUnit = LengthUnitChoice.AUTO) } }
                Radio(R.string.unit_m, s.lengthUnit == LengthUnitChoice.METER) { vm.update { it.copy(lengthUnit = LengthUnitChoice.METER) } }
                Radio(R.string.unit_km, s.lengthUnit == LengthUnitChoice.KILOMETER) { vm.update { it.copy(lengthUnit = LengthUnitChoice.KILOMETER) } }
            }
            Section(R.string.set_gps) {
                Text(stringResource(R.string.set_gps_accuracy, s.gpsMaxAccuracyM.roundToInt()))
                Slider(value = s.gpsMaxAccuracyM.toFloat(), onValueChange = { v -> vm.update { it.copy(gpsMaxAccuracyM = v.roundToInt().toDouble()) } }, valueRange = 3f..50f)
                Text(stringResource(R.string.set_gps_distance, s.gpsMinDistanceM.roundToInt()))
                Slider(value = s.gpsMinDistanceM.toFloat(), onValueChange = { v -> vm.update { it.copy(gpsMinDistanceM = v.roundToInt().toDouble()) } }, valueRange = 1f..20f)
                Text(stringResource(R.string.set_gps_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section(R.string.set_satellite) {
                var url by remember(s.satelliteTileUrl) { mutableStateOf(s.satelliteTileUrl) }
                val ok = url.isBlank() || MapStyles.isValidTileTemplate(url)
                OutlinedTextField(
                    value = url, onValueChange = { url = it; if (it.isBlank() || MapStyles.isValidTileTemplate(it)) vm.update { st -> st.copy(satelliteTileUrl = it.trim()) } },
                    label = { Text(stringResource(R.string.set_satellite_url)) }, singleLine = true, isError = !ok,
                    supportingText = { Text(stringResource(if (ok) R.string.set_satellite_help else R.string.set_satellite_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Section(R.string.set_privacy) {
                Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyMedium)
            }
            Section(R.string.set_about) {
                Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.app_subtitle))
                Text(stringResource(R.string.about_publisher), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_osm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Radio(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp))
        Text(stringResource(label))
    }
}
