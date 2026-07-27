package com.pmsuryaghar.docprocessor.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.pmsuryaghar.docprocessor.data.local.dao.ProcessingHistoryDao
import com.pmsuryaghar.docprocessor.data.local.entity.ProcessingHistoryEntity
import com.pmsuryaghar.docprocessor.data.util.DocumentClassifier
import com.pmsuryaghar.docprocessor.data.util.FileUtils
import com.pmsuryaghar.docprocessor.data.util.PdfGenerator
import com.pmsuryaghar.docprocessor.data.util.ReportGenerator
import com.pmsuryaghar.docprocessor.data.util.ZipCreator
import com.pmsuryaghar.docprocessor.domain.model.DocumentGroup
import com.pmsuryaghar.docprocessor.domain.model.DocumentType
import com.pmsuryaghar.docprocessor.domain.model.ProcessingHistory
import com.pmsuryaghar.docprocessor.domain.repository.ProcessingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class ProcessingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: ProcessingHistoryDao
) : ProcessingRepository {

    override fun getAllHistory(): Flow<List<ProcessingHistory>> {
        return historyDao.getAllHistory().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun insertHistory(history: ProcessingHistory): Long = withContext(Dispatchers.IO) {
        historyDao.insertHistory(history.toEntity())
    }

    override suspend fun getHistoryById(id: Long): ProcessingHistory? = withContext(Dispatchers.IO) {
        historyDao.getHistoryById(id)?.toDomain()
    }

    override suspend fun getLastSuccessfulProcessingTimestamp(): Long? = withContext(Dispatchers.IO) {
        historyDao.getLastSuccessfulProcessingTimestamp()
    }

    override suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        historyDao.clearAllHistory()
        Unit
    }

    override suspend fun scanNewDocuments(
        sourceFolderUri: String,
        lastProcessedTime: Long
    ): List<Uri> = withContext(Dispatchers.IO) {
        if (sourceFolderUri.isEmpty()) return@withContext emptyList()
        val scanned = FileUtils.scanSourceFolder(context, sourceFolderUri.toUri(), lastProcessedTime)
        scanned.map { it.uri }
    }

    override suspend fun groupDocuments(uris: List<Uri>): List<DocumentGroup> = withContext(Dispatchers.IO) {
        val groups = mutableListOf<DocumentGroup>()
        val groupedMap = mutableMapOf<DocumentType, MutableList<Uri>>()
        
        for (uri in uris) {
            val fileName = getFileNameFromUri(uri)
            val type = DocumentClassifier.classify(fileName)
            if (type == DocumentType.OTHER) {
                // Keep OTHER documents separate as individual groups
                groups.add(DocumentGroup(type, listOf(uri)))
            } else {
                groupedMap.getOrPut(type) { mutableListOf() }.add(uri)
            }
        }

        // Add classified groups
        groupedMap.forEach { (type, fileList) ->
            groups.add(DocumentGroup(type, fileList))
        }
        
        groups
    }

    override suspend fun generateCompressedPdfs(
        groups: List<DocumentGroup>,
        maxPdfSizeMb: Float
    ): List<File> = withContext(Dispatchers.IO) {
        val outputFiles = mutableListOf<File>()
        var otherCounter = 1
        
        for (group in groups) {
            val type = group.documentType
            
            // Rooftop Photo, House Photo, Passport Photo should remain images (JPG/PNG) if source is image
            if (type == DocumentType.ROOFTOP_PHOTO || type == DocumentType.HOUSE_PHOTO || type == DocumentType.PASSPORT_PHOTO) {
                val imageUri = group.fileUris.firstOrNull() ?: continue
                val extension = getFileExtensionFromUri(imageUri).lowercase(java.util.Locale.ROOT)
                if (extension == "pdf") {
                    val pdfFile = File(context.cacheDir, "${type.systemName}.pdf")
                    FileUtils.copyUriToLocalFile(context, imageUri, pdfFile)
                    outputFiles.add(pdfFile)
                } else {
                    val suffix = if (extension.isEmpty()) "jpg" else extension
                    val tempJpgFile = File(context.cacheDir, "${type.systemName}.$suffix")
                    FileUtils.copyUriToLocalFile(context, imageUri, tempJpgFile)
                    outputFiles.add(tempJpgFile)
                }
                continue
            }

            // Determine unique filename
            val fileName = when (type) {
                DocumentType.OTHER -> {
                    val firstUri = group.fileUris.firstOrNull()
                    if (firstUri != null) {
                        val origName = getFileNameFromUri(firstUri).substringBeforeLast('.')
                        if (origName.isNotEmpty() && !origName.startsWith("IMG-", ignoreCase = true)) {
                            "$origName.pdf"
                        } else {
                            "Other_$otherCounter.pdf"
                        }
                    } else {
                        "Other_$otherCounter.pdf"
                    }.also { otherCounter++ }
                }
                else -> "${type.systemName}.pdf"
            }

            val pdfFile = File(context.cacheDir, fileName)
            val isPdfOnly = group.fileUris.all { getFileExtensionFromUri(it) == "pdf" }

            if (isPdfOnly && group.fileUris.size == 1) {
                val tempPdfFile = File(context.cacheDir, "temp_src_${pdfFile.name}")
                FileUtils.copyUriToLocalFile(context, group.fileUris.first(), tempPdfFile)
                
                PdfGenerator.compressPdfFile(context, tempPdfFile, pdfFile, maxPdfSizeMb)
                tempPdfFile.delete()
            } else {
                val imageUris = group.fileUris.filter { getFileExtensionFromUri(it) != "pdf" }
                if (imageUris.isNotEmpty()) {
                    PdfGenerator.generatePdfFromImages(context, imageUris, pdfFile, maxPdfSizeMb)
                } else if (group.fileUris.isNotEmpty()) {
                    FileUtils.copyUriToLocalFile(context, group.fileUris.first(), pdfFile)
                }
            }
            outputFiles.add(pdfFile)
        }
        
        outputFiles
    }

    override suspend fun saveProcessedDocuments(
        customerName: String,
        mobileNumber: String,
        pdfFiles: List<File>,
        geminiResponse: String,
        baseOutputFolderUri: String,
        generatePdfReport: Boolean
    ): Pair<String, List<File>> = withContext(Dispatchers.IO) {
        val baseTreeUri = baseOutputFolderUri.toUri()
        val folderName = "${customerName.trim()}_${mobileNumber.trim()}"
        val filesSaved = mutableListOf<File>()

        // 0. Save copy of All_Documents.pdf sent to Gemini / AI Agent (Requirement 1)
        val allDocsFile = File(context.cacheDir, "All_Documents.pdf")
        if (allDocsFile.exists() && allDocsFile.length() > 0) {
            try {
                FileUtils.writeLocalFileToSafTree(context, baseTreeUri, folderName, "All_Documents.pdf", "application/pdf", allDocsFile)
                filesSaved.add(allDocsFile)
            } catch (e: Exception) {
                Timber.e(e, "Error saving All_Documents.pdf to SAF output folder")
            }
        }

        // 1. Save processed documents (PDFs / JPGs) to SAF Tree
        for (file in pdfFiles) {
            val extension = file.extension.lowercase(java.util.Locale.ROOT)
            val mimeType = when (extension) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            FileUtils.writeLocalFileToSafTree(context, baseTreeUri, folderName, file.name, mimeType, file)
            filesSaved.add(file)
        }

        // 2. Save Gemini Raw Response text
        val responseTxtFile = File(context.cacheDir, "Verification_Response.txt").apply {
            writeText(geminiResponse)
        }
        FileUtils.writeLocalFileToSafTree(context, baseTreeUri, folderName, "Verification_Response.txt", "text/plain", responseTxtFile)
        filesSaved.add(responseTxtFile)

        // 3. Generate Word DOCX Report using Apache POI
        val docxFile = File(context.cacheDir, "Verification_Report.docx")
        val docNames = pdfFiles.map { it.name }
        ReportGenerator.generateDocxReport(customerName, mobileNumber, geminiResponse, docNames, docxFile)
        
        FileUtils.writeLocalFileToSafTree(
            context, baseTreeUri, folderName, "Verification_Report.docx", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxFile
        )
        filesSaved.add(docxFile)

        // 4. Generate PDF Report if enabled
        if (generatePdfReport) {
            val reportPdfFile = File(context.cacheDir, "Verification_Report.pdf")
            ReportGenerator.generatePdfReport(customerName, mobileNumber, geminiResponse, docNames, reportPdfFile)
            
            FileUtils.writeLocalFileToSafTree(
                context, baseTreeUri, folderName, "Verification_Report.pdf", 
                "application/pdf", reportPdfFile
            )
            filesSaved.add(reportPdfFile)
        }

        val finalDirUriString = "${baseOutputFolderUri}/$folderName"
        Pair(finalDirUriString, filesSaved)
    }

    override suspend fun createZip(
        customerName: String,
        mobileNumber: String,
        localSavedFiles: List<File>
    ): File = withContext(Dispatchers.IO) {
        val zipFileName = "${customerName.trim()}_${mobileNumber.trim()}.zip"
        val zipFile = File(context.cacheDir, zipFileName)
        
        ZipCreator.createZip(localSavedFiles, zipFile)
        zipFile
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "unknown_file"
    }

    private fun getFileExtensionFromUri(uri: Uri): String {
        val name = getFileNameFromUri(uri)
        return name.substringAfterLast('.', "").lowercase()
    }

    private fun ProcessingHistoryEntity.toDomain() = ProcessingHistory(
        id = id,
        customerName = customerName,
        mobileNumber = mobileNumber,
        folderPath = folderPath,
        zipPath = zipPath,
        processingDate = processingDate,
        status = status,
        lastProcessingTimestamp = lastProcessingTimestamp
    )

    private fun ProcessingHistory.toEntity() = ProcessingHistoryEntity(
        id = id,
        customerName = customerName,
        mobileNumber = mobileNumber,
        folderPath = folderPath,
        zipPath = zipPath,
        processingDate = processingDate,
        status = status,
        lastProcessingTimestamp = lastProcessingTimestamp
    )
}
