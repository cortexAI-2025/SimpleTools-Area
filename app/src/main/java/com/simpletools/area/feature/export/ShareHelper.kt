package com.simpletools.area.feature.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.simpletools.area.R
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import java.io.File
import java.util.Locale

enum class ExportFormat(val extension: String, val mime: String) {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv"),
    GEOJSON("geojson", "application/geo+json"),
}

/** Écrit l'export dans le cache puis lance le partage Android natif (FileProvider, aucun accès stockage large). */
object ShareHelper {
    fun labels(context: Context, parcel: ParcelEntity) = PdfLabels(
        title = context.getString(R.string.app_name),
        date = context.getString(R.string.pdf_date),
        type = context.getString(R.string.pdf_type),
        typeName = typeName(context, parcel.type),
        area = context.getString(R.string.area),
        perimeter = context.getString(R.string.perimeter),
        length = context.getString(R.string.length),
        notes = context.getString(R.string.notes),
        coordinates = context.getString(R.string.pdf_coordinates),
        pointsCount = context.getString(R.string.pdf_points),
        disclaimer = context.getString(R.string.disclaimer),
        sketchCaption = context.getString(R.string.pdf_sketch_caption),
        page = context.getString(R.string.pdf_page),
    )

    fun typeName(context: Context, t: MeasureType) = context.getString(
        when (t) {
            MeasureType.MAP -> R.string.type_map
            MeasureType.GPS -> R.string.type_gps
            MeasureType.DISTANCE -> R.string.type_distance
        },
    )

    fun export(context: Context, parcel: ParcelEntity, format: ExportFormat, locale: Locale): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = parcel.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "parcelle" }
        val file = File(dir, "$safe.${format.extension}")
        when (format) {
            ExportFormat.PDF -> file.outputStream().use { PdfReport.write(parcel, labels(context, parcel), locale, it) }
            ExportFormat.CSV -> file.writeText(Exporters.toCsv(parcel), Charsets.UTF_8)
            ExportFormat.GEOJSON -> file.writeText(Exporters.toGeoJson(parcel), Charsets.UTF_8)
        }
        return file
    }

    fun share(context: Context, parcel: ParcelEntity, format: ExportFormat, locale: Locale) {
        val file = export(context, parcel, format, locale)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, parcel.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
