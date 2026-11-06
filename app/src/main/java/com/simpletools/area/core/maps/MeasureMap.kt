package com.simpletools.area.core.maps

import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simpletools.area.core.geometry.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.hypot

/** Demande de déplacement de caméra (l'identifiant change à chaque demande). */
data class CameraRequest(val id: Int, val target: GeoPoint? = null, val zoom: Double? = null, val fitPoints: List<GeoPoint>? = null)

private const val FILL_SRC = "shape-fill-src"
private const val LINE_SRC = "shape-line-src"
private const val VERT_SRC = "vertices-src"
private const val SEL_SRC = "selected-src"
private const val USER_SRC = "user-src"

private fun GeoPoint.toLatLng() = LatLng(lat, lon)
private fun GeoPoint.toPoint() = Point.fromLngLat(lon, lat)

/**
 * Carte interactive MapLibre : polygone bleu transparent, sommets déplaçables, position GPS.
 * - toucher la carte → [onTap]
 * - toucher un sommet → sélection ; le faire glisser → déplacement ([onDragStart], [onDragMove])
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MeasureMap(
    modifier: Modifier,
    points: List<GeoPoint>,
    closed: Boolean,
    selectedIndex: Int?,
    editable: Boolean,
    userLocation: GeoPoint?,
    dark: Boolean,
    satelliteUrl: String?,
    cameraRequest: CameraRequest?,
    initialCenter: GeoPoint,
    initialZoom: Double,
    onTap: (GeoPoint) -> Unit = {},
    onSelect: (Int?) -> Unit = {},
    onDragStart: (Int) -> Unit = {},
    onDragMove: (Int, GeoPoint) -> Unit = { _, _ -> },
    onCameraIdle: (GeoPoint, Double) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val hitRadiusPx = with(density) { 30.dp.toPx() }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context)
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleVersion by remember { mutableStateOf(0) }

    val pointsState = rememberUpdatedState(points)
    val editableState = rememberUpdatedState(editable)
    val onTapS = rememberUpdatedState(onTap)
    val onSelectS = rememberUpdatedState(onSelect)
    val onDragStartS = rememberUpdatedState(onDragStart)
    val onDragMoveS = rememberUpdatedState(onDragMove)
    val onIdleS = rememberUpdatedState(onCameraIdle)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        mapView.onCreate(null)
        lifecycle.addObserver(observer)
        // Le lifecycle est peut‑être déjà démarré : rattraper les événements manqués.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // Initialisation de la carte + gestion tactile des sommets
    DisposableEffect(mapView) {
        var dragIdx = -1
        var downX = 0f; var downY = 0f; var moved = false
        mapView.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder().target(initialCenter.toLatLng()).zoom(initialZoom).build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.addOnMapClickListener { ll ->
                if (editableState.value) onTapS.value(GeoPoint(ll.latitude, ll.longitude))
                true
            }
            m.addOnCameraIdleListener {
                val t = m.cameraPosition.target
                if (t != null) onIdleS.value(GeoPoint(t.latitude, t.longitude), m.cameraPosition.zoom)
            }
        }
        mapView.setOnTouchListener { v, ev ->
            val m = map ?: return@setOnTouchListener false
            if (!editableState.value) return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val pts = pointsState.value
                    var best = -1; var bestD = hitRadiusPx
                    pts.forEachIndexed { i, p ->
                        val s = m.projection.toScreenLocation(p.toLatLng())
                        val d = hypot((s.x - ev.x).toDouble(), (s.y - ev.y).toDouble()).toFloat()
                        if (d <= bestD) { best = i; bestD = d }
                    }
                    if (best >= 0) {
                        dragIdx = best; downX = ev.x; downY = ev.y; moved = false
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    } else false
                }
                MotionEvent.ACTION_MOVE -> if (dragIdx >= 0) {
                    if (!moved && hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) > 12) {
                        moved = true
                        onSelectS.value(dragIdx)
                        onDragStartS.value(dragIdx)
                    }
                    if (moved) {
                        val ll = m.projection.fromScreenLocation(PointF(ev.x, ev.y))
                        onDragMoveS.value(dragIdx, GeoPoint(ll.latitude, ll.longitude))
                    }
                    true
                } else false
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragIdx >= 0) {
                    if (!moved && ev.actionMasked == MotionEvent.ACTION_UP) {
                        onSelectS.value(dragIdx)
                    }
                    dragIdx = -1
                    true
                } else false
                else -> false
            }
        }
        onDispose { mapView.setOnTouchListener(null); map = null }
    }

    // Style (standard / satellite / sombre) puis couches de dessin
    LaunchedEffect(map, dark, satelliteUrl) {
        val m = map ?: return@LaunchedEffect
        m.setStyle(Style.Builder().fromJson(MapStyles.styleJson(dark, satelliteUrl))) { style ->
            installLayers(style)
            styleVersion++
        }
    }

    // Mise à jour des données dessinées
    LaunchedEffect(map, styleVersion, points, closed, selectedIndex, userLocation) {
        val style = map?.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        updateSources(style, points, closed, selectedIndex, userLocation)
    }

    // Déplacements de caméra demandés
    LaunchedEffect(cameraRequest?.id, map) {
        val m = map ?: return@LaunchedEffect
        val r = cameraRequest ?: return@LaunchedEffect
        when {
            r.fitPoints != null && r.fitPoints.size >= 2 -> {
                val b = LatLngBounds.Builder()
                r.fitPoints.forEach { b.include(it.toLatLng()) }
                runCatching { m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120), 600) }
            }
            r.target != null -> m.animateCamera(
                CameraUpdateFactory.newLatLngZoom(r.target.toLatLng(), r.zoom ?: maxOf(m.cameraPosition.zoom, 17.0)),
                600,
            )
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val BLUE = "#1565C0"

private fun installLayers(style: Style) {
    style.addSource(GeoJsonSource(FILL_SRC))
    style.addSource(GeoJsonSource(LINE_SRC))
    style.addSource(GeoJsonSource(VERT_SRC))
    style.addSource(GeoJsonSource(SEL_SRC))
    style.addSource(GeoJsonSource(USER_SRC))
    style.addLayer(FillLayer("shape-fill", FILL_SRC).withProperties(fillColor(BLUE), fillOpacity(0.28f)))
    style.addLayer(
        LineLayer("shape-line", LINE_SRC).withProperties(
            lineColor(BLUE), lineWidth(3f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer("vertices", VERT_SRC).withProperties(
            circleRadius(9f), circleColor("#FFFFFF"), circleStrokeColor(BLUE), circleStrokeWidth(3.5f),
        ),
    )
    style.addLayer(
        CircleLayer("selected", SEL_SRC).withProperties(
            circleRadius(12f), circleColor("#FF8F00"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3.5f),
        ),
    )
    style.addLayer(
        CircleLayer("user", USER_SRC).withProperties(
            circleRadius(8f), circleColor("#2979FF"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
        ),
    )
}

private fun updateSources(style: Style, pts: List<GeoPoint>, closed: Boolean, sel: Int?, user: GeoPoint?) {
    val poly = closed && pts.size >= 3
    val fill = if (poly) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(Polygon.fromLngLats(listOf((pts + pts.first()).map { it.toPoint() }))),
        )
    } else FeatureCollection.fromFeatures(emptyList<Feature>())
    val line = if (pts.size >= 2) {
        val path = if (poly) pts + pts.first() else pts
        FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(path.map { it.toPoint() })))
    } else FeatureCollection.fromFeatures(emptyList<Feature>())
    style.getSourceAs<GeoJsonSource>(FILL_SRC)?.setGeoJson(fill)
    style.getSourceAs<GeoJsonSource>(LINE_SRC)?.setGeoJson(line)
    style.getSourceAs<GeoJsonSource>(VERT_SRC)?.setGeoJson(
        FeatureCollection.fromFeatures(pts.map { Feature.fromGeometry(it.toPoint()) }),
    )
    style.getSourceAs<GeoJsonSource>(SEL_SRC)?.setGeoJson(
        FeatureCollection.fromFeatures(listOfNotNull(sel?.let { pts.getOrNull(it) }?.let { Feature.fromGeometry(it.toPoint()) })),
    )
    style.getSourceAs<GeoJsonSource>(USER_SRC)?.setGeoJson(
        FeatureCollection.fromFeatures(listOfNotNull(user?.let { Feature.fromGeometry(it.toPoint()) })),
    )
}
