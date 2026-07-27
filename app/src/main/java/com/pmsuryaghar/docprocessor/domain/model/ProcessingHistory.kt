package com.pmsuryaghar.docprocessor.domain.model

data class ProcessingHistory(
    val id: Long = 0,
    val customerName: String,
    val mobileNumber: String,
    val folderPath: String,
    val zipPath: String,
    val processingDate: Long,
    val status: String,
    val lastProcessingTimestamp: Long
)
