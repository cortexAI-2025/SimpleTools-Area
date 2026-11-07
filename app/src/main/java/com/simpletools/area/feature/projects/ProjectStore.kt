package com.simpletools.area.feature.projects

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simpletools.area.core.geometry.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID

private val Context.projectData by preferencesDataStore(name = "area_projects")
private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

internal class ProjectStore(context: Context, private val hasPro: () -> Boolean) {
    private val data = context.applicationContext.projectData
    fun canExport() = hasPro()

    val projects = data.data.map { preferences ->
        preferences.asMap().values.map { ProjectBackup.decode(it as String, validate = false) }.sortedBy { it.name.lowercase() }
    }

    suspend fun create(name: String, parcels: List<ProjectParcel> = emptyList()): String {
        val project = AreaProject(UUID.randomUUID().toString(), name, parcels)
        update(project)
        return project.id
    }

    suspend fun update(project: AreaProject) {
        check(hasPro())
        val json = ProjectBackup.encode(project)
        data.edit {
            check(hasPro())
            it[stringPreferencesKey(project.id)] = json
        }
    }

    suspend fun edit(id: String, transform: (AreaProject) -> AreaProject) {
        check(hasPro())
        data.edit {
            check(hasPro())
            val key = stringPreferencesKey(id)
            val current = ProjectBackup.decode(checkNotNull(it[key]), validate = false)
            it[key] = ProjectBackup.encode(transform(current))
        }
    }

    // A user may always delete their data, including after a refund.
    suspend fun delete(id: String) { data.edit { it.remove(stringPreferencesKey(id)) } }

    suspend fun restore(input: InputStream): String = withContext(Dispatchers.IO) {
        check(hasPro())
        val project = ProjectBackup.read(input)
        create(project.name, project.parcels)
    }
}

internal object ProjectBackup {
    fun encode(project: AreaProject): String {
        require(project.name.isNotBlank() && project.name.length <= 200 && project.parcels.size <= 1_000)
        return JSONObject()
            .put("schema", 1)
            .put("id", project.id)
            .put("name", project.name)
            .put("parcels", JSONArray(project.parcels.map { parcel ->
                JSONObject().put("name", parcel.name).put("category", parcel.category)
                    .put("boundary", encodeRing(parcel.boundary))
                    .put("exclusions", JSONArray(parcel.exclusions.map(::encodeRing)))
            })).toString().also { require(it.toByteArray().size <= MAX_BACKUP_BYTES) }
    }

    private fun encodeRing(points: List<GeoPoint>) = JSONArray(points.map { JSONArray(listOf(it.lat, it.lon)) })

    fun read(input: InputStream): AreaProject {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            require(out.size() + n <= MAX_BACKUP_BYTES)
            out.write(buffer, 0, n)
        }
        return decode(out.toString(Charsets.UTF_8.name()), validate = true)
    }

    fun decode(json: String, validate: Boolean = true): AreaProject {
        val data = JSONObject(json)
        require(data.get("schema") == 1)
        val name = data.getString("name")
        require(name.isNotBlank() && name.length <= 200)
        val parcels = data.getJSONArray("parcels")
        require(parcels.length() <= 1_000)
        return AreaProject(data.getString("id"), name, List(parcels.length()) { i ->
            val parcel = parcels.getJSONObject(i)
            val boundary = decodeRing(parcel.getJSONArray("boundary"))
            val exclusions = parcel.getJSONArray("exclusions")
            require(exclusions.length() <= 32)
            val holes = List(exclusions.length()) { decodeRing(exclusions.getJSONArray(it)) }
            val parcelName = parcel.getString("name")
            val category = parcel.getString("category")
            if (validate) projectParcel(parcelName, category, boundary, holes)
            else ProjectParcel(parcelName, category, boundary, holes)
        })
    }

    private fun decodeRing(array: JSONArray): List<GeoPoint> {
        require(array.length() in 3..5_000)
        return List(array.length()) {
            val point = array.getJSONArray(it)
            require(point.length() == 2 && point.get(0) is Number && point.get(1) is Number)
            GeoPoint(point.getDouble(0), point.getDouble(1)).also { p -> require(p.isValid) }
        }
    }
}
