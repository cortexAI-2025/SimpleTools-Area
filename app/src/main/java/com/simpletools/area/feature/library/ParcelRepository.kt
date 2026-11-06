package com.simpletools.area.feature.library

import com.simpletools.area.core.database.ParcelDao
import com.simpletools.area.core.database.ParcelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Accès aux parcelles enregistrées (pattern Repository). */
class ParcelRepository(private val dao: ParcelDao, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeAll(): Flow<List<ParcelEntity>> = dao.observeAll()

    fun search(query: String): Flow<List<ParcelEntity>> =
        dao.observeAll().map { list ->
            val q = query.trim()
            if (q.isEmpty()) list
            else list.filter { it.name.contains(q, ignoreCase = true) || it.notes.contains(q, ignoreCase = true) }
        }

    fun observe(id: Long): Flow<ParcelEntity?> = dao.observe(id)

    suspend fun get(id: Long): ParcelEntity? = dao.get(id)

    suspend fun save(parcel: ParcelEntity): Long {
        val now = clock()
        return dao.insert(parcel.copy(id = 0, createdAt = now, updatedAt = now))
    }

    suspend fun update(parcel: ParcelEntity) = dao.update(parcel.copy(updatedAt = clock()))

    suspend fun rename(id: Long, name: String, notes: String? = null) {
        val p = dao.get(id) ?: return
        dao.update(p.copy(name = name.trim().ifEmpty { p.name }, notes = notes ?: p.notes, updatedAt = clock()))
    }

    suspend fun duplicate(id: Long, copySuffix: String): Long? {
        val p = dao.get(id) ?: return null
        val now = clock()
        return dao.insert(p.copy(id = 0, name = p.name + copySuffix, createdAt = now, updatedAt = now))
    }

    suspend fun delete(id: Long) {
        dao.get(id)?.let { dao.delete(it) }
    }
}
