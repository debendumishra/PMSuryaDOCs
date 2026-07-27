package com.pmsuryaghar.docprocessor.domain.repository

import android.net.Uri
import com.pmsuryaghar.docprocessor.domain.model.DocumentGroup
import com.pmsuryaghar.docprocessor.domain.model.ProcessingHistory
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ProcessingRepository {
    fun getAllHistory(): Flow<List<ProcessingHistory>>
    suspend fun insertHistory(history: ProcessingHistory): Long
    suspend fun getHistoryById(id: Long): ProcessingHistory?
    suspend fun getLastSuccessfulProcessingTimestamp(): Long?
    suspend fun clearAllHistory()
    
    // Core processing steps
    suspend fun scanNewDocuments(sourceFolderUri: String, lastProcessedTime: Long): List<Uri>
    suspend fun groupDocuments(uris: List<Uri>): List<DocumentGroup>
    suspend fun generateCompressedPdfs(groups: List<DocumentGroup>, maxPdfSizeMb: Float): List<File>
    
    // Saving and report compilation steps
    suspend fun saveProcessedDocuments(
        customerName: String,
        mobileNumber: String,
        pdfFiles: List<File>,
        geminiResponse: String,
        baseOutputFolderUri: String,
        generatePdfReport: Boolean
    ): Pair<String, List<File>> // Returns folder path/URI and list of files saved
    
    suspend fun createZip(
        customerName: String,
        mobileNumber: String,
        localSavedFiles: List<File>
    ): File // Returns the generated local ZIP File
}
