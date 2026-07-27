package com.pmsuryaghar.docprocessor.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processing_history")
data class ProcessingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val mobileNumber: String,
    val folderPath: String,
    val zipPath: String,
    val processingDate: Long,
    val status: String,
    val lastProcessingTimestamp: Long
)
