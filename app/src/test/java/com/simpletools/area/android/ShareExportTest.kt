package com.simpletools.area.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.feature.export.ExportFormat
import com.simpletools.area.feature.export.PdfLabels
import com.simpletools.area.feature.export.PdfReport
import com.simpletools.area.feature.export.ShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShareExportTest {
    @Test fun shareHelperWritesAllThreeFormatsToCache() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val p = TestData.parcel("Mon champ / test")
        for (f in ExportFormat.entries) {
            val file = ShareHelper.export(ctx, p, f, Locale.FRANCE)
            assertTrue("${f.name} ${file.length()}", file.exists() && file.length() > 0)
            assertEquals(f.extension, file.extension)
            if (f == ExportFormat.PDF) assertEquals("%PDF-", String(file.readBytes().copyOf(5), Charsets.ISO_8859_1))
        }
        assertTrue(ShareHelper.export(ctx, p, ExportFormat.CSV, Locale.FRANCE).readText().startsWith("index,latitude,longitude"))
    }
}
