package com.simpletools.area.android

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simpletools.area.core.database.AppDatabase
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.feature.library.ParcelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseAndRepositoryTest {
    private lateinit var context: Context
    private val dbName = "restore-test.db"
    private var db: AppDatabase? = null

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build().also { db = it }

    @Before fun setUp() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(dbName) }
    @After fun tearDown() { db?.close(); context.deleteDatabase(dbName) }

    @Test fun saveCloseReopenRestore_exactValues() = runBlocking {
        var clock = 1_700_000_000_000L
        val repo = ParcelRepository(open().parcelDao()) { clock }
        val id = repo.save(TestData.parcel(notes = "Bord de rivière"))
        assertTrue(id > 0)
        // « fermeture de l'application » : on ferme la base puis on la rouvre depuis le fichier
        db!!.close()
        assertTrue(File(context.getDatabasePath(dbName).path).exists())
        val restored = ParcelRepository(open().parcelDao()) { clock }.get(id)!!
        assertEquals("Champ test", restored.name)
        assertEquals(1_700_000_000_000L, restored.createdAt)
        assertEquals(MeasureType.MAP, restored.type)
        assertEquals(TestData.square, restored.points) // coordonnées restaurées à l'identique (double exact)
        assertEquals(10000.0895549414, restored.areaM2, 0.0)
        assertEquals(400.0017910985802, restored.perimeterM, 0.0)
        assertEquals("HECTARE", restored.displayUnit)
        assertEquals("Bord de rivière", restored.notes)
    }

    @Test fun renameDuplicateDeleteSearch() = runBlocking {
        var clock = 1000L
        val repo = ParcelRepository(open().parcelDao()) { clock++ }
        val a = repo.save(TestData.parcel("Verger"))
        val b = repo.save(TestData.parcel("Potager", notes = "près du puits"))
        assertEquals(listOf("Potager", "Verger"), repo.observeAll().first().map { it.name }) // plus récent d'abord

        repo.rename(a, "  Grand verger ")
        assertEquals("Grand verger", repo.get(a)!!.name)
        repo.rename(a, "   ") // nom vide ignoré
        assertEquals("Grand verger", repo.get(a)!!.name)

        assertEquals(listOf("Grand verger"), repo.search("VERGER").first().map { it.name })
        assertEquals(listOf("Potager"), repo.search("puits").first().map { it.name }) // recherche dans les notes
        assertTrue(repo.search("zzz").first().isEmpty())

        val copy = repo.duplicate(b, " (copie)")!!
        assertNotEquals(b, copy)
        val c = repo.get(copy)!!
        assertEquals("Potager (copie)", c.name)
        assertEquals(repo.get(b)!!.points, c.points)

        repo.delete(a)
        assertNull(repo.get(a))
        assertEquals(2, repo.observeAll().first().size)
        assertNull(repo.duplicate(9999, "x"))
    }

    @Test fun updateKeepsIdAndCreationDate() = runBlocking {
        var clock = 10L
        val repo = ParcelRepository(open().parcelDao()) { clock }
        val id = repo.save(TestData.parcel())
        clock = 99L
        repo.update(repo.get(id)!!.copy(notes = "modifié", areaM2 = 5.0))
        val u = repo.get(id)!!
        assertEquals(10L, u.createdAt); assertEquals(99L, u.updatedAt); assertEquals("modifié", u.notes); assertEquals(5.0, u.areaM2, 0.0)
    }
}
