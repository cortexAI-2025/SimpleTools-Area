package com.simpletools.area.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelDao {
    @Query("SELECT * FROM parcels ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ParcelEntity>>

    @Query("SELECT * FROM parcels WHERE id = :id")
    fun observe(id: Long): Flow<ParcelEntity?>

    @Query("SELECT * FROM parcels WHERE id = :id")
    suspend fun get(id: Long): ParcelEntity?

    @Insert
    suspend fun insert(parcel: ParcelEntity): Long

    @Update
    suspend fun update(parcel: ParcelEntity)

    @Delete
    suspend fun delete(parcel: ParcelEntity)
}
