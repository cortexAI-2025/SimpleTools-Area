package com.simpletools.area

import android.app.Application
import com.simpletools.area.core.billing.AreaBilling
import com.simpletools.area.feature.projects.ProjectStore
import com.simpletools.area.core.database.AppDatabase
import com.simpletools.area.core.location.AndroidLocationSource
import com.simpletools.area.core.location.LocationSource
import com.simpletools.area.core.settings.SettingsRepository
import com.simpletools.area.feature.library.ParcelRepository

/** Conteneur de dépendances minimal (pas de framework d'injection pour une petite application). */
class AreaApp : Application() {
    internal val billing by lazy { AreaBilling(this) }
    internal val projects by lazy { ProjectStore(this, billing::hasPro) }
    val database by lazy { AppDatabase.create(this) }
    val parcels by lazy { ParcelRepository(database.parcelDao()) }
    val settings by lazy { SettingsRepository(this) }
    val locationSource: LocationSource by lazy { AndroidLocationSource(this) }
}
