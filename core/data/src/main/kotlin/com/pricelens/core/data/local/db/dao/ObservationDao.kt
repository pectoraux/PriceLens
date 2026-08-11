package com.pricelens.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pricelens.core.data.local.db.entity.ObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {
    @Query("SELECT * FROM observations")
    fun getAllObservations(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE id = :id")
    suspend fun getObservationById(id: String): ObservationEntity?

    @Query("SELECT * FROM observations WHERE id = :id")
    fun observeObservationById(id: String): Flow<ObservationEntity?>

    @Query("SELECT * FROM observations WHERE syncStatus = 'PENDING'")
    suspend fun getPendingObservations(): List<ObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: ObservationEntity): Unit

    @Query("DELETE FROM observations WHERE id = :id")
    suspend fun deleteObservation(id: String): Unit
}
