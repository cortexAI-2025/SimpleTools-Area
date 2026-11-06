package com.simpletools.area.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.LengthUnitChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val tag: String) { FRENCH("fr"), ENGLISH("en"), SYSTEM("") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.FRENCH,
    val areaUnit: AreaUnitChoice = AreaUnitChoice.AUTO,
    val lengthUnit: LengthUnitChoice = LengthUnitChoice.AUTO,
    /** Précision GPS maximale (m) pour accepter un point. */
    val gpsMaxAccuracyM: Double = DEFAULT_MAX_ACCURACY,
    /** Déplacement minimal (m) pour enregistrer un nouveau point. */
    val gpsMinDistanceM: Double = DEFAULT_MIN_DISTANCE,
    /** Modèle d'URL de tuiles satellite fourni par l'utilisateur (vide = désactivé). */
    val satelliteTileUrl: String = "",
) {
    companion object {
        const val DEFAULT_MAX_ACCURACY = 15.0
        const val DEFAULT_MIN_DISTANCE = 3.0
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

class SettingsRepository(private val store: DataStore<Preferences>) : SettingsStore {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    private object K {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val areaUnit = stringPreferencesKey("area_unit")
        val lengthUnit = stringPreferencesKey("length_unit")
        val maxAcc = doublePreferencesKey("gps_max_accuracy")
        val minDist = doublePreferencesKey("gps_min_distance")
        val satUrl = stringPreferencesKey("satellite_url")
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    override val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = p[K.theme].toEnum(d.theme),
            language = p[K.language].toEnum(d.language),
            areaUnit = p[K.areaUnit].toEnum(d.areaUnit),
            lengthUnit = p[K.lengthUnit].toEnum(d.lengthUnit),
            gpsMaxAccuracyM = p[K.maxAcc] ?: d.gpsMaxAccuracyM,
            gpsMinDistanceM = p[K.minDist] ?: d.gpsMinDistanceM,
            satelliteTileUrl = p[K.satUrl] ?: d.satelliteTileUrl,
        )
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val cur = AppSettings(
                theme = p[K.theme].toEnum(ThemeMode.SYSTEM),
                language = p[K.language].toEnum(AppLanguage.FRENCH),
                areaUnit = p[K.areaUnit].toEnum(AreaUnitChoice.AUTO),
                lengthUnit = p[K.lengthUnit].toEnum(LengthUnitChoice.AUTO),
                gpsMaxAccuracyM = p[K.maxAcc] ?: AppSettings.DEFAULT_MAX_ACCURACY,
                gpsMinDistanceM = p[K.minDist] ?: AppSettings.DEFAULT_MIN_DISTANCE,
                satelliteTileUrl = p[K.satUrl] ?: "",
            )
            val n = transform(cur)
            p[K.theme] = n.theme.name
            p[K.language] = n.language.name
            p[K.areaUnit] = n.areaUnit.name
            p[K.lengthUnit] = n.lengthUnit.name
            p[K.maxAcc] = n.gpsMaxAccuracyM
            p[K.minDist] = n.gpsMinDistanceM
            p[K.satUrl] = n.satelliteTileUrl
        }
    }
}
