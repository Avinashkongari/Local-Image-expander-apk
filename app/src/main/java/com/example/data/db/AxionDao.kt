package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AxionDao {
    @Query("SELECT * FROM image_history ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM image_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHistory(item: HistoryEntity): Long

    @Query("DELETE FROM image_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("SELECT * FROM local_models ORDER BY installedAt DESC")
    fun observeLocalModels(): Flow<List<LocalModelEntity>>

    @Query("SELECT * FROM local_models WHERE slotKey = :slotKey")
    suspend fun getModelBySlot(slotKey: String): LocalModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalModel(model: LocalModelEntity)

    @Query("DELETE FROM local_models WHERE slotKey = :slotKey")
    suspend fun deleteLocalModel(slotKey: String)
}
