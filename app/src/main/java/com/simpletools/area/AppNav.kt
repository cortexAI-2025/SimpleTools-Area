package com.simpletools.area

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simpletools.area.core.location.GpsSession
import com.simpletools.area.core.location.GpsTrackingService
import com.simpletools.area.feature.calculator.CalculatorScreen
import com.simpletools.area.feature.home.HomeScreen
import com.simpletools.area.feature.library.LibraryScreen
import com.simpletools.area.feature.library.LibraryViewModel
import com.simpletools.area.feature.library.ParcelDetailScreen
import com.simpletools.area.feature.library.ParcelDetailViewModel
import com.simpletools.area.feature.measurement.GpsMeasureScreen
import com.simpletools.area.feature.measurement.GpsMeasureViewModel
import com.simpletools.area.feature.measurement.MapMeasureScreen
import com.simpletools.area.feature.measurement.MapMeasureViewModel
import com.simpletools.area.feature.measurement.MeasureMode
import com.simpletools.area.feature.settings.SettingsScreen
import com.simpletools.area.feature.settings.SettingsViewModel

private fun Context.app() = applicationContext as AreaApp

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val app = LocalContext.current.app()
    val ctx = LocalContext.current

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onMap = { nav.navigate("measure/AREA/0") },
                onGps = { nav.navigate("gps") },
                onCalculator = { nav.navigate("calculator") },
                onDistance = { nav.navigate("measure/DISTANCE/0") },
                onLibrary = { nav.navigate("library") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable(
            "measure/{mode}/{parcelId}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType }, navArgument("parcelId") { type = NavType.LongType }),
        ) { entry ->
            val mode = MeasureMode.valueOf(entry.arguments?.getString("mode") ?: "AREA")
            val pid = entry.arguments?.getLong("parcelId")?.takeIf { it > 0 }
            val vm: MapMeasureViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    MapMeasureViewModel(createSavedStateHandle(), app.parcels, app.settings, app.locationSource, mode, pid)
                }
            })
            MapMeasureScreen(vm, onBack = { nav.popBackStack() }, onSaved = { id ->
                nav.navigate("detail/$id") { popUpTo("home") }
            })
        }
        composable("gps") {
            val vm: GpsMeasureViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    GpsMeasureViewModel(
                        GpsSession.recorder, app.parcels, app.settings, app.locationSource,
                        startService = { GpsTrackingService.start(ctx.applicationContext) },
                        stopService = { GpsTrackingService.stop(ctx.applicationContext) },
                    )
                }
            })
            GpsMeasureScreen(vm, onBack = { nav.popBackStack() }, onSaved = { id ->
                nav.navigate("detail/$id") { popUpTo("home") }
            })
        }
        composable("calculator") { CalculatorScreen(onBack = { nav.popBackStack() }) }
        composable("library") {
            val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(app.parcels) } })
            LibraryScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("detail/$it") })
        }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm: ParcelDetailViewModel = viewModel(key = "detail$id", factory = viewModelFactory {
                initializer { ParcelDetailViewModel(id, app.parcels, app.settings) }
            })
            ParcelDetailScreen(
                vm,
                onBack = { nav.popBackStack() },
                onEditGeometry = { pid, distance -> nav.navigate("measure/${if (distance) "DISTANCE" else "AREA"}/$pid") },
                onOpen = { nav.navigate("detail/$it") { popUpTo("library") } },
            )
        }
        composable("settings") {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app.settings) } })
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
