package com.pmsuryaghar.docprocessor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pmsuryaghar.docprocessor.data.local.entity.ProcessingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessingHistoryDao {
    @Query("SELECT * FROM processing_history ORDER BY processingDate DESC")
    fun getAllHistory(): Flow<List<ProcessingHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHistory(entity: ProcessingHistoryEntity): Long

    @Query("SELECT * FROM processing_history WHERE id = :id")
    fun getHistoryById(id: Long): ProcessingHistoryEntity?

    @Query("SELECT MAX(lastProcessingTimestamp) FROM processing_history WHERE status = 'COMPLETED'")
    fun getLastSuccessfulProcessingTimestamp(): Long?

    @Query("DELETE FROM processing_history")
    fun clearAllHistory(): Int
}
