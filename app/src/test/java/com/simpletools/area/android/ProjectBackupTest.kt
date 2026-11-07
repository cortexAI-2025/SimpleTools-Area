package com.simpletools.area.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.feature.projects.AreaProject
import com.simpletools.area.feature.projects.ProjectBackup
import com.simpletools.area.feature.projects.ProjectStore
import com.simpletools.area.feature.projects.projectParcel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProjectBackupTest {
    private val parcel = projectParcel("Champ", "Culture", listOf(
        GeoPoint(48.0, 2.0), GeoPoint(48.0, 2.001), GeoPoint(48.001, 2.0),
    ), emptyList())

    @Test fun roundTripAndInvalidCoordinates() {
        val original = AreaProject("example", "Ferme", listOf(parcel))
        val json = ProjectBackup.encode(original)
        val reopened = ProjectBackup.read(json.byteInputStream())
        assertEquals(original, reopened)
        assertEquals(original.areaM2, reopened.areaM2, 1e-8)
        assertThrows(IllegalArgumentException::class.java) {
            ProjectBackup.read(json.replace("[48", "[98").byteInputStream())
        }
    }

    @Test fun storeRestoresIndependentCopyAndGuardsPro() = runBlocking {
        var active = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ProjectStore(context) { active }
        assertTrue(runCatching { store.create("Locked") }.exceptionOrNull() is IllegalStateException)
        active = true
        val id = store.create("Ferme", listOf(parcel))
        val original = store.projects.first().single { it.id == id }
        val restoredId = store.restore(ProjectBackup.encode(original).byteInputStream())
        assertNotEquals(id, restoredId)
        store.edit(restoredId) { it.copy(name = "Copy") }
        val projects = ProjectStore(context) { active }.projects.first().associateBy { it.id }
        assertEquals("Ferme", projects[id]?.name)
        assertEquals("Copy", projects[restoredId]?.name)
        assertEquals(projects[id]?.areaM2, projects[restoredId]?.areaM2)
        active = false
        assertTrue(runCatching { store.edit(id) { it.copy(name = "Locked") } }.exceptionOrNull() is IllegalStateException)
        store.delete(id)
        store.delete(restoredId)
    }
}
