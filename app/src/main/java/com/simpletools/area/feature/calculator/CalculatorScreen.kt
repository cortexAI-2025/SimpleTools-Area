package com.simpletools.area.feature.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.area.R
import com.simpletools.area.core.geometry.AreaUnit
import com.simpletools.area.core.geometry.LengthUnit
import com.simpletools.area.core.geometry.PlanePoint
import com.simpletools.area.core.geometry.ShapeCalculator
import com.simpletools.area.core.geometry.ShapeError
import com.simpletools.area.core.geometry.ShapeResult
import com.simpletools.area.core.geometry.UnitFormat
import com.simpletools.area.core.geometry.regularPolygon
import com.simpletools.area.ui.AppTopBar
import com.simpletools.area.ui.DisclaimerText
import com.simpletools.area.ui.decimalKeyboard
import java.util.Locale

enum class Shape(val label: Int, val fields: List<Int>) {
    RECTANGLE(R.string.shape_rectangle, listOf(R.string.f_length, R.string.f_width)),
    SQUARE(R.string.shape_square, listOf(R.string.f_side)),
    TRIANGLE(R.string.shape_triangle, listOf(R.string.f_base, R.string.f_height)),
    TRIANGLE_SIDES(R.string.shape_triangle_sides, listOf(R.string.f_side_a, R.string.f_side_b, R.string.f_side_c)),
    CIRCLE(R.string.shape_circle, listOf(R.string.f_radius)),
    TRAPEZOID(R.string.shape_trapezoid, listOf(R.string.f_base_a, R.string.f_base_b, R.string.f_height)),
    REGULAR(R.string.shape_regular, listOf(R.string.f_sides_count, R.string.f_side)),
    POLYGON(R.string.shape_polygon, emptyList()),
}

/** Lecture tolérante : "12,5" ou "12.5". Renvoie null si invalide. */
fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Une ligne par sommet : "x y" ou "x;y" (virgule décimale acceptée). */
fun parsePlanePoints(text: String): List<PlanePoint>? {
    val pts = ArrayList<PlanePoint>()
    for (line in text.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
        val parts = line.split(Regex("[;\\s]+")).filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val x = parseNumber(parts[0]) ?: return null
        val y = parseNumber(parts[1]) ?: return null
        pts.add(PlanePoint(x, y))
    }
    return pts
}

fun compute(shape: Shape, v: List<String>, polygonText: String, unit: LengthUnit): ShapeResult {
    fun n(i: Int) = parseNumber(v.getOrElse(i) { "" })
    return when (shape) {
        Shape.POLYGON -> parsePlanePoints(polygonText)?.let { ShapeCalculator.polygon(it, unit) }
            ?: ShapeResult.Error(ShapeError.NOT_A_NUMBER)
        Shape.REGULAR -> {
            val cnt = n(0)?.takeIf { it == Math.floor(it) && it in 3.0..1000.0 }?.toInt()
            val side = n(1)
            if (cnt == null || side == null) ShapeResult.Error(ShapeError.NOT_A_NUMBER) else ShapeCalculator.regularPolygon(cnt, side, unit)
        }
        else -> {
            val nums = shape.fields.indices.map { n(it) }
            if (nums.any { it == null }) ShapeResult.Error(ShapeError.NOT_A_NUMBER)
            else {
                val d = nums.map { it!! }
                when (shape) {
                    Shape.RECTANGLE -> ShapeCalculator.rectangle(d[0], d[1], unit)
                    Shape.SQUARE -> ShapeCalculator.square(d[0], unit)
                    Shape.TRIANGLE -> ShapeCalculator.triangleBaseHeight(d[0], d[1], unit)
                    Shape.TRIANGLE_SIDES -> ShapeCalculator.triangleSides(d[0], d[1], d[2], unit)
                    Shape.CIRCLE -> ShapeCalculator.circle(d[0], unit)
                    Shape.TRAPEZOID -> ShapeCalculator.trapezoid(d[0], d[1], d[2], unit)
                    else -> ShapeResult.Error(ShapeError.NOT_A_NUMBER)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalculatorScreen(onBack: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var shapeIdx by rememberSaveable { mutableStateOf(0) }
    var unitIdx by rememberSaveable { mutableStateOf(0) }
    var v0 by rememberSaveable { mutableStateOf("") }
    var v1 by rememberSaveable { mutableStateOf("") }
    var v2 by rememberSaveable { mutableStateOf("") }
    var poly by rememberSaveable { mutableStateOf("0 0\n10 0\n10 5\n0 5") }
    val shape = Shape.entries[shapeIdx]
    val units = listOf(LengthUnit.METER, LengthUnit.CENTIMETER, LengthUnit.KILOMETER, LengthUnit.FOOT)
    val unit = units[unitIdx]
    val values = listOf(v0, v1, v2)
    val anyInput = if (shape == Shape.POLYGON) poly.isNotBlank() else values.take(shape.fields.size).any { it.isNotBlank() }
    val result = if (anyInput) compute(shape, values, poly, unit) else null

    Scaffold(topBar = { AppTopBar(stringResource(R.string.calc_title), onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.calc_shape), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Shape.entries.forEachIndexed { i, s ->
                    FilterChip(selected = i == shapeIdx, onClick = { shapeIdx = i }, label = { Text(stringResource(s.label), maxLines = 1) })
                }
            }
            Text(stringResource(R.string.calc_input_unit), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEachIndexed { i, u -> FilterChip(selected = i == unitIdx, onClick = { unitIdx = i }, label = { Text(u.symbol) }) }
            }
            if (shape == Shape.POLYGON) {
                OutlinedTextField(
                    value = poly, onValueChange = { poly = it.take(5000) }, minLines = 5, maxLines = 10,
                    label = { Text(stringResource(R.string.calc_polygon_hint, unit.symbol)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                shape.fields.forEachIndexed { i, f ->
                    val value = values[i]
                    OutlinedTextField(
                        value = value,
                        onValueChange = { t -> val c = t.take(20); when (i) { 0 -> v0 = c; 1 -> v1 = c; else -> v2 = c } },
                        label = { Text(stringResource(f) + if (shape == Shape.REGULAR && i == 0) "" else " (${unit.symbol})") },
                        singleLine = true, keyboardOptions = decimalKeyboard,
                        isError = value.isNotBlank() && parseNumber(value) == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ResultCard(result, locale)
            DisclaimerText()
        }
    }
}

@Composable
private fun ResultCard(result: ShapeResult?, locale: Locale) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (result) {
                null -> Text(stringResource(R.string.calc_enter_values), color = MaterialTheme.colorScheme.onPrimaryContainer)
                is ShapeResult.Error -> Text(
                    stringResource(
                        when (result.error) {
                            ShapeError.NOT_A_NUMBER -> R.string.calc_err_number
                            ShapeError.NON_POSITIVE -> R.string.calc_err_positive
                            ShapeError.TRIANGLE_INEQUALITY -> R.string.calc_err_triangle
                            ShapeError.TOO_FEW_POINTS -> R.string.err_too_few_points
                            ShapeError.SELF_INTERSECTING -> R.string.err_self_intersecting
                            ShapeError.POLYGON_INVALID -> R.string.err_zero_area
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                is ShapeResult.Ok -> {
                    Text(stringResource(R.string.area), style = MaterialTheme.typography.labelMedium)
                    Text(UnitFormat.formatArea(result.areaM2, UnitFormat.autoAreaUnit(result.areaM2), locale), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.equivalent) + " " + listOf(AreaUnit.SQUARE_METER, AreaUnit.HECTARE, AreaUnit.ACRE, AreaUnit.SQUARE_FOOT).joinToString(" · ") { UnitFormat.formatArea(result.areaM2, it, locale) }, style = MaterialTheme.typography.bodySmall)
                    result.perimeterM?.let {
                        Text(stringResource(R.string.perimeter) + " " + UnitFormat.formatLength(it, UnitFormat.autoLengthUnit(it), locale), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
