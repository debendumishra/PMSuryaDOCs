package com.pmsuryaghar.docprocessor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pmsuryaghar.docprocessor.data.local.dao.ProcessingHistoryDao
import com.pmsuryaghar.docprocessor.data.local.entity.ProcessingHistoryEntity

@Database(entities = [ProcessingHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun processingHistoryDao(): ProcessingHistoryDao
}
