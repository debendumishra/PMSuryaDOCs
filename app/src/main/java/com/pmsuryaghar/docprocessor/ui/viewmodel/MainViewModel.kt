package com.pmsuryaghar.docprocessor.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmsuryaghar.docprocessor.data.util.GeminiResponseExtractor
import com.pmsuryaghar.docprocessor.data.util.PdfHelper
import com.pmsuryaghar.docprocessor.data.util.IntentManager
import com.pmsuryaghar.docprocessor.data.util.FileUtils
import com.pmsuryaghar.docprocessor.domain.model.*
import com.pmsuryaghar.docprocessor.domain.repository.ProcessingRepository
import com.pmsuryaghar.docprocessor.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

enum class ProcessingState {
    IDLE,
    SCANNING,
    ORGANIZING,
    GENERATING_PDFS,
    LAUNCHING_GEMINI,
    WAITING_GEMINI,
    EXTRACTING_DETAILS,
    FOLDER_REVIEW,
    SAVING_FILES,
    AWAITING_CONFIRMATION,
    COMPLETED,
    ERROR
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val processingRepository: ProcessingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState: StateFlow<ProcessingState> = _processingState

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _scannedUris = MutableStateFlow<List<Uri>>(emptyList())
    val scannedUris: StateFlow<List<Uri>> = _scannedUris

    private val _groupedDocs = MutableStateFlow<List<DocumentGroup>>(emptyList())
    val groupedDocs: StateFlow<List<DocumentGroup>> = _groupedDocs

    private val _generatedFiles = MutableStateFlow<List<File>>(emptyList())
    val generatedFiles: StateFlow<List<File>> = _generatedFiles
    
    private val _pagePreviews = MutableStateFlow<List<Uri>>(emptyList())
    val pagePreviews: StateFlow<List<Uri>> = _pagePreviews

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _detectedName = MutableStateFlow("")
    val detectedName: StateFlow<String> = _detectedName

    private val _detectedMobile = MutableStateFlow("")
    val detectedMobile: StateFlow<String> = _detectedMobile

    private val _detectedConsumerNo = MutableStateFlow("")
    val detectedConsumerNo: StateFlow<String> = _detectedConsumerNo

    private val _pendingExtractedInfo = MutableStateFlow<com.pmsuryaghar.docprocessor.data.util.GeminiResponseExtractor.ExtractedInfo?>(null)
    val pendingExtractedInfo: StateFlow<com.pmsuryaghar.docprocessor.data.util.GeminiResponseExtractor.ExtractedInfo?> = _pendingExtractedInfo

    fun setAppUnlocked() {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(isAppUnlocked = true)
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    // Folder review fields

    private val _rawGeminiResponse = MutableStateFlow("")
    val rawGeminiResponse: StateFlow<String> = _rawGeminiResponse

    private val _proposedFolderName = MutableStateFlow("")
    val proposedFolderName: StateFlow<String> = _proposedFolderName

    private val _selectedOutputLocationUri = MutableStateFlow("")
    val selectedOutputLocationUri: StateFlow<String> = _selectedOutputLocationUri

    private val _existingFiles = MutableStateFlow<List<Pair<String, Uri>>>(emptyList())
    val existingFiles: StateFlow<List<Pair<String, Uri>>> = _existingFiles

    private val _sourceFolderFileCount = MutableStateFlow(0)
    val sourceFolderFileCount: StateFlow<Int> = _sourceFolderFileCount.asStateFlow()

    // History flows
    val historyList: StateFlow<List<ProcessingHistory>> = processingRepository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastSuccessfulRun: StateFlow<Long?> = combine(
        historyList,
        settingsRepository.getSettings()
    ) { history, settings ->
        if (settings.lastProcessingTimestamp > 0L) {
            settings.lastProcessingTimestamp
        } else {
            history.firstOrNull { it.status == "COMPLETED" }?.processingDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { appSettings ->
                _settings.value = appSettings
                _selectedOutputLocationUri.value = appSettings.defaultOutputFolderUri
            }
        }
    }

    private val _isBatchProcessed = MutableStateFlow(false)
    val isBatchProcessed: StateFlow<Boolean> = _isBatchProcessed.asStateFlow()

    private val _negativeRemarks = MutableStateFlow<List<String>>(emptyList())
    val negativeRemarks: StateFlow<List<String>> = _negativeRemarks.asStateFlow()

    private val _missingDocuments = MutableStateFlow<List<String>>(emptyList())
    val missingDocuments: StateFlow<List<String>> = _missingDocuments.asStateFlow()

    fun startProcessing(context: Context) {
        viewModelScope.launch {
            try {
                if (_isBatchProcessed.value) {
                    resetState(context)
                }

                val appSettings = _settings.value
                if (appSettings.defaultOutputFolderUri.isEmpty()) {
                    setError("Base output folder is not configured. Please go to Settings.")
                    return@launch
                }

                val preloadedUris = _scannedUris.value
                val scanned = if (preloadedUris.isNotEmpty()) {
                    preloadedUris
                } else {
                    if (appSettings.sourceFolderUri.isEmpty()) {
                        setError("No documents are loaded. Please share files to this app from WhatsApp, or configure the WhatsApp folder in Settings.")
                        return@launch
                    }
                    _processingState.value = ProcessingState.SCANNING
                    _statusText.value = "Reading WhatsApp Files..."
                    val scannedFromFolder = processingRepository.scanNewDocuments(
                        appSettings.sourceFolderUri,
                        0L // Ignore last processing timestamp to send all files available in source folder
                    )
                    if (scannedFromFolder.isEmpty()) {
                        setError("No new documents found in the configured WhatsApp folder since the last processing run.")
                        return@launch
                    }
                    _scannedUris.value = scannedFromFolder
                    scannedFromFolder
                }

                // 2. Organize / classify documents
                _processingState.value = ProcessingState.ORGANIZING
                _statusText.value = "Organizing Documents..."
                val groups = processingRepository.groupDocuments(scanned)
                _groupedDocs.value = groups

                // 3. Generate and compress PDFs
                _processingState.value = ProcessingState.GENERATING_PDFS
                _statusText.value = "Creating PDFs & Compressing..."
                val generated = processingRepository.generateCompressedPdfs(groups, appSettings.maxPdfSizeMb)
                _generatedFiles.value = generated

                // Combine all generated files into a single multi-page PDF for Gemini sharing
                _statusText.value = "Consolidating all documents..."
                val combinedFile = File(context.cacheDir, "All_Documents.pdf")
                PdfHelper.mergePdfFiles(generated, combinedFile)

                // 4. Launch AI Agent Intent (Gemini or ChatGPT)
                val agent = appSettings.selectedAiAgent
                _processingState.value = ProcessingState.LAUNCHING_GEMINI
                _statusText.value = "Preparing $agent Request..."
                
                val pageCount = PdfHelper.getPageCount(combinedFile)
                val finalPrompt = if (pageCount > 0) {
                    "${appSettings.defaultGeminiPrompt}\n\nThe uploaded file has $pageCount pages."
                } else {
                    appSettings.defaultGeminiPrompt
                }
                IntentManager.launchAiAgent(context, agent, finalPrompt, listOf(combinedFile))
                
                // Go to waiting state
                _processingState.value = ProcessingState.WAITING_GEMINI
                _statusText.value = "Waiting for $agent response..."
            } catch (e: Exception) {
                Timber.e(e, "Error during processing")
                setError("Processing failed: ${e.localizedMessage}")
            }
        }
    }

    fun onGeminiResponseReceived(context: Context, responseText: String) {
        if (responseText.isEmpty()) return
        
        viewModelScope.launch {
            _processingState.value = ProcessingState.EXTRACTING_DETAILS
            _statusText.value = "Extracting customer details..."

            _rawGeminiResponse.value = responseText
            val extracted = com.pmsuryaghar.docprocessor.data.util.GeminiResponseExtractor.extract(responseText)
            
            _detectedName.value = extracted.customerName.ifEmpty { "UNKNOWN_CUSTOMER" }
            _detectedMobile.value = extracted.mobileNumber.ifEmpty { "0000000000" }
            _detectedConsumerNo.value = extracted.electricityConsumerNo
            
            _proposedFolderName.value = "${_detectedName.value}_${_detectedMobile.value}"
            
            _pendingExtractedInfo.value = extracted
            
            // Extract page previews from All_Documents.pdf
            _statusText.value = "Generating page previews..."
            val combinedFile = File(context.cacheDir, "All_Documents.pdf")
            if (combinedFile.exists()) {
                val previewUris = com.pmsuryaghar.docprocessor.data.util.PdfHelper.renderAllPagesToImages(combinedFile, context.cacheDir)
                _pagePreviews.value = previewUris
            } else {
                _pagePreviews.value = emptyList()
            }
            
            _processingState.value = ProcessingState.AWAITING_CONFIRMATION
            _statusText.value = "Awaiting Document Mapping Confirmation..."
        }
    }

    fun confirmAndProcessDocuments(confirmedMapping: Map<Int, String>) {
        val extracted = _pendingExtractedInfo.value ?: return
        
        viewModelScope.launch {
            // Update the pending info with confirmed mapping
            _pendingExtractedInfo.value = extracted.copy(documentMapping = confirmedMapping)
            
            // Move to FOLDER_REVIEW state where user confirms name/mobile/folder
            _processingState.value = ProcessingState.FOLDER_REVIEW
            _statusText.value = "Review proposed customer output folder"
            
            // Note: we'll use pendingExtractedInfo.value in saveAndShare instead of re-parsing
        }
    }

    fun updateFolderDetails(name: String, mobile: String, folderName: String) {
        _detectedName.value = name
        _detectedMobile.value = mobile
        _proposedFolderName.value = folderName
    }

    fun updateOutputLocation(uri: String) {
        _selectedOutputLocationUri.value = uri
        viewModelScope.launch {
            settingsRepository.updateSettings(_settings.value.copy(defaultOutputFolderUri = uri))
        }
    }

    fun saveAndShare(context: Context) {
        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.SAVING_FILES
                _statusText.value = "Creating Customer Folder..."
                
                val appSettings = _settings.value
                val baseOutput = _selectedOutputLocationUri.value
                
                val combinedFile = File(context.cacheDir, "All_Documents.pdf")
                val documentMapping = _pendingExtractedInfo.value?.documentMapping ?: GeminiResponseExtractor.extract(_rawGeminiResponse.value).documentMapping

                var pdfFiles = _generatedFiles.value
                if (pdfFiles.isEmpty()) {
                    val fallbackFiles = mutableListOf<File>()
                    context.cacheDir.listFiles()?.forEach { file ->
                        val name = file.name
                        if (file.isFile && !name.startsWith("Verification_Report") && 
                            !name.equals("Gemini_Response.txt") && !name.equals("Verification_Response.txt") &&
                            !name.endsWith(".zip") && !name.equals("All_Documents.pdf") && !name.startsWith("temp_src_")) {
                            val ext = file.extension.lowercase()
                            if (ext == "pdf" || ext == "jpg" || ext == "jpeg" || ext == "png") {
                                fallbackFiles.add(file)
                            }
                        }
                    }
                    pdfFiles = fallbackFiles
                }

                // If combined PDF exists and Gemini mapping is found, perform splitting
                if (combinedFile.exists() && documentMapping.isNotEmpty()) {
                    try {
                        _statusText.value = "Splitting and grouping pages..."
                        val splitMap = PdfHelper.splitPdfFile(combinedFile, documentMapping, context.cacheDir)
                        if (splitMap.isNotEmpty()) {
                            pdfFiles = splitMap.values.toList()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error splitting combined PDF, falling back to original files")
                    }
                }

                _statusText.value = "Saving files and reports..."
                val (folderPath, filesSaved) = processingRepository.saveProcessedDocuments(
                    customerName = _detectedName.value,
                    mobileNumber = _detectedMobile.value,
                    pdfFiles = pdfFiles,
                    geminiResponse = _rawGeminiResponse.value,
                    baseOutputFolderUri = baseOutput,
                    generatePdfReport = appSettings.generatePdfReport
                )

                if (filesSaved.isEmpty()) {
                    throw Exception("Failed to write any files to the selected output directory. Please verify that the output location is writeable and has appropriate permissions.")
                }

                _statusText.value = "Creating ZIP archive..."
                val zipFile = processingRepository.createZip(
                    _detectedName.value,
                    _detectedMobile.value,
                    filesSaved
                )

                // Save ZIP into SAF customer directory too
                val zipSafUri = FileUtils.writeLocalFileToSafTree(
                    context,
                    Uri.parse(baseOutput),
                    _proposedFolderName.value,
                    zipFile.name,
                    "application/zip",
                    zipFile
                )

                _statusText.value = "Saving history..."
                val timestamp = System.currentTimeMillis()
                val history = ProcessingHistory(
                    customerName = _detectedName.value,
                    mobileNumber = _detectedMobile.value,
                    folderPath = folderPath,
                    zipPath = zipSafUri?.toString() ?: zipFile.absolutePath,
                    processingDate = timestamp,
                    status = "COMPLETED",
                    lastProcessingTimestamp = timestamp
                )
                processingRepository.insertHistory(history)

                // Update settings last successful processing timestamp
                settingsRepository.updateLastProcessingTimestamp(timestamp)

                // WhatsApp sharing has been intentionally disabled as requested
                // IntentManager.shareZipToWhatsApp(...)

                _processingState.value = ProcessingState.COMPLETED
                _statusText.value = "Completed!"
                _isBatchProcessed.value = true
            } catch (e: Exception) {
                Timber.e(e, "Error saving files")
                setError("Failed to save and share documents: ${e.localizedMessage}")
            }
        }
    }

    fun onFilesShared(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val sourceUriStr = settingsRepository.getSettings().first().sourceFolderUri
            val copiedUris = mutableListOf<Uri>()
            if (sourceUriStr.isNotEmpty()) {
                try {
                    val targetFolderUri = Uri.parse(sourceUriStr)
                    for (uri in uris) {
                        val copiedUri = FileUtils.copySharedUriToSourceFolder(context, uri, targetFolderUri)
                        if (copiedUri != null) {
                            copiedUris.add(copiedUri)
                        } else {
                            copiedUris.add(uri)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during source folder copy pipeline")
                    copiedUris.addAll(uris)
                }
            } else {
                copiedUris.addAll(uris)
            }

            val currentList = _scannedUris.value.toMutableList()
            for (newUri in copiedUris) {
                if (!currentList.contains(newUri)) {
                    currentList.add(newUri)
                }
            }
            _scannedUris.value = currentList
            _processingState.value = ProcessingState.IDLE
        }
    }

    fun setError(message: String) {
        _processingState.value = ProcessingState.ERROR
        _statusText.value = message
    }

    fun resetState(context: Context? = null) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.IDLE
            _statusText.value = ""
            _isBatchProcessed.value = false
            resetStateValues()
            settingsRepository.updateLastProcessingTimestamp(0L)
            if (context != null) {
                updateSourceFolderCount(context)
            }
        }
    }

    fun renameCleanupFile(context: Context, uri: Uri, newName: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                // Ensure extension is retained
                val currentName = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name ?: ""
                val extension = if (currentName.contains(".")) currentName.substringAfterLast(".") else ""
                val finalName = if (extension.isNotEmpty() && !newName.endsWith(".$extension", true)) {
                    "$newName.$extension"
                } else {
                    newName
                }
                
                android.provider.DocumentsContract.renameDocument(context.contentResolver, uri, finalName)
                loadCleanupFiles(context)
                refreshExistingFiles(context)
            } catch (e: Exception) {
                Timber.e(e, "Error renaming cleanup file")
            } finally {
                onComplete()
            }
        }
    }

    fun deletePdfPages(context: Context, uri: Uri, pagesInput: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pagesToDelete = mutableSetOf<Int>()
                pagesInput.split(",").forEach { part ->
                    val p = part.trim()
                    if (p.contains("-")) {
                        val range = p.split("-")
                        if (range.size == 2) {
                            val start = range[0].toIntOrNull()
                            val end = range[1].toIntOrNull()
                            if (start != null && end != null) {
                                for (i in start..end) {
                                    pagesToDelete.add(i - 1) // 0-indexed
                                }
                            }
                        }
                    } else {
                        val pageNum = p.toIntOrNull()
                        if (pageNum != null) {
                            pagesToDelete.add(pageNum - 1)
                        }
                    }
                }

                if (pagesToDelete.isEmpty()) {
                    withContext(Dispatchers.Main) { onComplete() }
                    return@launch
                }

                val document = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                } ?: run {
                    withContext(Dispatchers.Main) { onComplete() }
                    return@launch
                }

                val sortedPages = pagesToDelete.sortedDescending()
                for (pageIndex in sortedPages) {
                    if (pageIndex in 0 until document.numberOfPages) {
                        document.removePage(pageIndex)
                    }
                }

                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    document.save(outputStream)
                }
                document.close()
                
                loadCleanupFiles(context)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting PDF pages")
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun updateSourceFolderCount(context: Context) {
        viewModelScope.launch {
            val sourceUriStr = _settings.value.sourceFolderUri
            if (sourceUriStr.isNotEmpty()) {
                try {
                    val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(sourceUriStr))
                    if (rootDoc != null && rootDoc.isDirectory) {
                        val count = rootDoc.listFiles().count { it.isFile }
                        _sourceFolderFileCount.value = count
                    } else {
                        _sourceFolderFileCount.value = 0
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error counting files in source folder")
                    _sourceFolderFileCount.value = 0
                }
            } else {
                _sourceFolderFileCount.value = 0
            }
        }
    }

    private fun resetStateValues() {
        _scannedUris.value = emptyList()
        _groupedDocs.value = emptyList()
        _generatedFiles.value = emptyList()
        _detectedName.value = ""
        _detectedMobile.value = ""
        _rawGeminiResponse.value = ""
        _proposedFolderName.value = ""
        _negativeRemarks.value = emptyList()
        _missingDocuments.value = emptyList()
    }
    fun refreshExistingFiles(context: Context) {
        val baseUriStr = _selectedOutputLocationUri.value
        val folderName = _proposedFolderName.value
        if (baseUriStr.isEmpty()) {
            _existingFiles.value = emptyList()
            return
        }
        try {
            val filesList = mutableListOf<Pair<String, Uri>>()
            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(baseUriStr))
            
            if (rootDoc != null && rootDoc.isDirectory) {
                // 1. List files directly in the selected folder
                rootDoc.listFiles().forEach { file ->
                    if (file.isFile && file.name != null) {
                        filesList.add(Pair(file.name!!, file.uri))
                    }
                }
                
                // 2. List files in the proposed subfolder if it exists
                if (folderName.isNotEmpty()) {
                    val subfolder = rootDoc.findFile(folderName)
                    if (subfolder != null && subfolder.isDirectory) {
                        subfolder.listFiles().forEach { file ->
                            if (file.isFile && file.name != null) {
                                val pair = Pair("${folderName}/${file.name!!}", file.uri)
                                if (filesList.none { it.second == file.uri }) {
                                    filesList.add(pair)
                                }
                            }
                        }
                    }
                }
            }
            _existingFiles.value = filesList
        } catch (e: Exception) {
            Timber.e(e, "Error reading existing output files")
            _existingFiles.value = emptyList()
        }
    }

    fun deleteExistingFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            try {
                for (uri in uris) {
                    val fileDoc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    fileDoc?.delete()
                }
                refreshExistingFiles(context)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting old output files")
            }
        }
    }

    private val _cleanupSourceFiles = MutableStateFlow<List<FileItemData>>(emptyList())
    val cleanupSourceFiles: StateFlow<List<FileItemData>> = _cleanupSourceFiles.asStateFlow()

    private val _cleanupDestFiles = MutableStateFlow<List<FileItemData>>(emptyList())
    val cleanupDestFiles: StateFlow<List<FileItemData>> = _cleanupDestFiles.asStateFlow()

    private val _cleanupWhatsappMediaFiles = MutableStateFlow<List<FileItemData>>(emptyList())
    val cleanupWhatsappMediaFiles: StateFlow<List<FileItemData>> = _cleanupWhatsappMediaFiles.asStateFlow()

    private val _isCleanupLoading = MutableStateFlow(false)
    val isCleanupLoading: StateFlow<Boolean> = _isCleanupLoading.asStateFlow()

    fun loadCleanupFiles(context: Context) {
        viewModelScope.launch {
            _isCleanupLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    // Always read fresh settings from repository to avoid stale _settings.value crash
                    val currentSettings = try {
                        settingsRepository.getSettings().first()
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading settings in loadCleanupFiles")
                        return@withContext
                    }

                    // Calculate 2-day cutoff (today + yesterday) in milliseconds
                    val twoDaysAgoMs = System.currentTimeMillis() - (2L * 24L * 60L * 60L * 1000L)

                    // 1. Source Folder (SAF tree uri) - show ALL files
                    val sourceUriStr = currentSettings.sourceFolderUri
                    if (sourceUriStr.isNotEmpty()) {
                        try {
                            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(sourceUriStr))
                            if (rootDoc != null && rootDoc.isDirectory) {
                                _cleanupSourceFiles.value = rootDoc.listFiles()
                                    .filter { it.name != null }
                                    .sortedByDescending { it.lastModified() }
                                    .map { FileItemData(it.name!!, it.uri, it.isDirectory, it.length(), it.lastModified()) }
                            } else {
                                _cleanupSourceFiles.value = emptyList()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error reading source cleanup files")
                            _cleanupSourceFiles.value = emptyList()
                        }
                    } else {
                        _cleanupSourceFiles.value = emptyList()
                    }

                    // 2. Destination Folder (SAF tree uri) - show ALL files/sub-folders
                    val destUriStr = _selectedOutputLocationUri.value.ifEmpty {
                        currentSettings.defaultOutputFolderUri
                    }
                    if (destUriStr.isNotEmpty()) {
                        try {
                            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(destUriStr))
                            if (rootDoc != null && rootDoc.isDirectory) {
                                _cleanupDestFiles.value = rootDoc.listFiles()
                                    .filter { it.name != null }
                                    .sortedByDescending { it.lastModified() }
                                    .map { FileItemData(it.name!!, it.uri, it.isDirectory, it.length(), it.lastModified()) }
                            } else {
                                _cleanupDestFiles.value = emptyList()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error reading destination cleanup files")
                            _cleanupDestFiles.value = emptyList()
                        }
                    } else {
                        _cleanupDestFiles.value = emptyList()
                    }

                    // 3. Custom WhatsApp Media Folder - show files sorted datetime descending
                    val configuredMediaUriStr = currentSettings.whatsappMediaFolderUri
                    if (configuredMediaUriStr.isNotEmpty()) {
                        try {
                            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(configuredMediaUriStr))
                            if (rootDoc != null && rootDoc.isDirectory) {
                                _cleanupWhatsappMediaFiles.value = rootDoc.listFiles()
                                    .filter { it.name != null }
                                    .sortedByDescending { it.lastModified() }
                                    .map { FileItemData(it.name!!, it.uri, it.isDirectory, it.length(), it.lastModified()) }
                            } else {
                                _cleanupWhatsappMediaFiles.value = emptyList()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error reading configured WA Media folder")
                            _cleanupWhatsappMediaFiles.value = emptyList()
                        }
                    } else {
                        // Fallback: scan actual system WhatsApp Documents + Images paths, filter last 2 days, sort descending
                        val actualDocs = FileUtils.scanActualWhatsappFolder(context, isDocumentFolder = true)
                        val actualImages = FileUtils.scanActualWhatsappFolder(context, isDocumentFolder = false)
                        val combined = (actualDocs + actualImages)
                            .filter { it.lastModified >= twoDaysAgoMs }
                            .sortedByDescending { it.lastModified }
                            .map { 
                                val size = try { java.io.File(it.uri.path ?: "").length() } catch(e: Exception) { 0L }
                                FileItemData(it.name, it.uri, false, size, it.lastModified) 
                            }
                        _cleanupWhatsappMediaFiles.value = combined
                    }
                }
            } finally {
                _isCleanupLoading.value = false
            }
        }
    }

    fun sendFilesToSourceFolder(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val sourceUriStr = settingsRepository.getSettings().first().sourceFolderUri
            if (sourceUriStr.isEmpty()) {
                setError("Source folder is not configured. Please set the WhatsApp source folder in Settings.")
                return@launch
            }
            try {
                val targetFolderUri = Uri.parse(sourceUriStr)
                val copiedUris = mutableListOf<Uri>()
                for (uri in uris) {
                    val copied = FileUtils.copySharedUriToSourceFolder(context, uri, targetFolderUri)
                    if (copied != null) {
                        copiedUris.add(copied)
                    }
                }
                val currentList = _scannedUris.value.toMutableList()
                for (c in copiedUris) {
                    if (!currentList.contains(c)) {
                        currentList.add(c)
                    }
                }
                _scannedUris.value = currentList
                loadCleanupFiles(context)
                updateSourceFolderCount(context)
                _statusText.value = "Copied ${copiedUris.size} file(s) to WhatsApp Source Folder!"
            } catch (e: Exception) {
                Timber.e(e, "Error sending files to source folder")
                setError("Failed to copy files to source folder: ${e.localizedMessage}")
            }
        }
    }

    fun deleteCleanupFiles(context: Context, uris: List<Uri>, isSource: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                for (uri in uris) {
                    val fileDoc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    fileDoc?.delete()
                }
                loadCleanupFiles(context)
                refreshExistingFiles(context)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting cleanup files")
            } finally {
                onComplete()
            }
        }
    }

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    /**
     * Clears all files in the custom WhatsApp Media folder, then fetches today's and yesterday's
     * files from the actual WhatsApp Documents and WhatsApp Images folders into it.
     */
    fun refreshCustomWhatsappFolder(context: Context) {
        viewModelScope.launch {
            val currentSettings = try {
                settingsRepository.getSettings().first()
            } catch (e: Exception) {
                _syncStatus.value = "Failed to read settings"
                return@launch
            }

            val targetUriStr = currentSettings.whatsappMediaFolderUri
            if (targetUriStr.isEmpty()) {
                _syncStatus.value = "Configure Custom WhatsApp Media folder in Settings first!"
                return@launch
            }

            _syncStatus.value = "Clearing existing files in Custom Folder..."
            val targetUri = Uri.parse(targetUriStr)
            var copied = 0

            withContext(Dispatchers.IO) {
                try {
                    val targetDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, targetUri)
                    if (targetDoc != null && targetDoc.isDirectory) {
                        // 1. Remove all existing files in custom folder
                        targetDoc.listFiles().forEach { file ->
                            try {
                                file.delete()
                            } catch (e: Exception) {
                                Timber.e(e, "Error deleting file ${file.name} during refresh")
                            }
                        }

                        // 2. Fetch today's and yesterday's files from actual WhatsApp Docs & Images
                        val twoDaysAgoMs = System.currentTimeMillis() - (2L * 24L * 60L * 60L * 1000L)
                        val actualDocs = FileUtils.scanActualWhatsappFolder(context, isDocumentFolder = true)
                        val actualImages = FileUtils.scanActualWhatsappFolder(context, isDocumentFolder = false)

                        val recentWaFiles = (actualDocs + actualImages)
                            .filter { it.lastModified >= twoDaysAgoMs }
                            .sortedByDescending { it.lastModified }

                        for (waFile in recentWaFiles) {
                            try {
                                val result = FileUtils.copySharedUriToSourceFolder(context, waFile.uri, targetUri)
                                if (result != null) copied++
                            } catch (e: Exception) {
                                Timber.e(e, "Error copying WA file: ${waFile.name}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during custom folder refresh")
                }
            }

            _syncStatus.value = "Refresh complete! Cleared folder & imported $copied file(s)."
            loadCleanupFiles(context)
        }
    }

    fun clearHistory() {

        viewModelScope.launch {
            try {
                processingRepository.clearAllHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing processing history")
            }
        }
    }

    suspend fun loadFolderContents(context: Context, folderUri: Uri): List<FileItemData> {
        return withContext(Dispatchers.IO) {
            try {
                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                    ?: androidx.documentfile.provider.DocumentFile.fromSingleUri(context, folderUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    rootDoc.listFiles()
                        .filter { it.name != null }
                        .map { FileItemData(it.name!!, it.uri, it.isDirectory, it.length(), it.lastModified()) }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading subfolder contents")
                emptyList()
            }
        }
    }

    private fun getDestinationFolder(context: Context, uri: Uri): androidx.documentfile.provider.DocumentFile? {
        val path = java.net.URLDecoder.decode(uri.toString(), "UTF-8")
        val sourceTree = settings.value.sourceFolderUri
        val destTree = settings.value.defaultOutputFolderUri
        val waTree = settings.value.whatsappMediaFolderUri
        
        val treeToUse = when {
            sourceTree.isNotBlank() && path.contains(Uri.parse(sourceTree).lastPathSegment ?: "///") -> sourceTree
            destTree.isNotBlank() && path.contains(Uri.parse(destTree).lastPathSegment ?: "///") -> destTree
            waTree.isNotBlank() && path.contains(Uri.parse(waTree).lastPathSegment ?: "///") -> waTree
            else -> if (sourceTree.isNotBlank()) sourceTree else destTree
        }
        
        return if (treeToUse.isNotBlank()) {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(treeToUse))
        } else {
            null
        }
    }

    fun splitPdf(context: Context, uri: Uri, option: Int, splitAtPage: String, parentUri: Uri?, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCleanupLoading.value = true
            try {
                val document = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                }
                
                if (document != null) {
                    val splitter = com.tom_roush.pdfbox.multipdf.Splitter()
                    if (option == 1) {
                        val pageNum = splitAtPage.toIntOrNull()
                        if (pageNum != null && pageNum > 0 && pageNum < document.numberOfPages) {
                            splitter.setSplitAtPage(pageNum)
                        } else {
                            document.close()
                            _isCleanupLoading.value = false
                            withContext(Dispatchers.Main) { onComplete() }
                            return@launch
                        }
                    }
                    
                    val splitDocuments = splitter.split(document)
                    val originalDocFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    val parentFolder = if (parentUri != null) {
                        androidx.documentfile.provider.DocumentFile.fromTreeUri(context, parentUri) ?: getDestinationFolder(context, uri)
                    } else {
                        getDestinationFolder(context, uri)
                    }
                    
                    val originalName = originalDocFile?.name?.substringBeforeLast(".") ?: "document"
                    
                    if (parentFolder != null) {
                        splitDocuments.forEachIndexed { index, splitDoc ->
                            val partName = "${originalName}_part${index + 1}.pdf"
                            val newFile = parentFolder.createFile("application/pdf", partName)
                            if (newFile != null) {
                                context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                                    splitDoc.save(outputStream)
                                }
                            }
                            splitDoc.close()
                        }
                    }
                    document.close()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error splitting PDF")
            } finally {
                _isCleanupLoading.value = false
                loadCleanupFiles(context)
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun convertPdfToJpg(context: Context, uri: Uri, parentUri: Uri?, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCleanupLoading.value = true
            try {
                val document = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                }
                if (document != null) {
                    val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(document)
                    val originalDocFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    val parentFolder = if (parentUri != null) {
                        androidx.documentfile.provider.DocumentFile.fromTreeUri(context, parentUri) ?: getDestinationFolder(context, uri)
                    } else {
                        getDestinationFolder(context, uri)
                    }
                    
                    val originalName = originalDocFile?.name?.substringBeforeLast(".") ?: "document"
                    
                    if (parentFolder != null) {
                        for (i in 0 until document.numberOfPages) {
                            val bitmap = renderer.renderImageWithDPI(i, 300f, com.tom_roush.pdfbox.rendering.ImageType.RGB)
                            val partName = "${originalName}_page${i + 1}.jpg"
                            val newFile = parentFolder.createFile("image/jpeg", partName)
                            if (newFile != null) {
                                context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                                }
                            }
                        }
                    }
                    document.close()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error converting PDF to JPG")
            } finally {
                _isCleanupLoading.value = false
                loadCleanupFiles(context)
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun convertImageToPdf(context: Context, uri: Uri, parentUri: Uri?, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCleanupLoading.value = true
            try {
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    
                    if (bitmap != null) {
                        val tempFile = File(context.cacheDir, "temp_img.jpg")
                        tempFile.outputStream().use { os ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, os)
                        }
                        
                        val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFile(tempFile.absolutePath, document)
                        val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(pdImage.width.toFloat(), pdImage.height.toFloat()))
                        document.addPage(page)
                        
                        val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)
                        contentStream.drawImage(pdImage, 0f, 0f)
                        contentStream.close()
                        
                        val originalDocFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                        val parentFolder = if (parentUri != null) {
                            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, parentUri) ?: getDestinationFolder(context, uri)
                        } else {
                            getDestinationFolder(context, uri)
                        }
                        
                        val originalName = originalDocFile?.name?.substringBeforeLast(".") ?: "document"
                        val partName = "${originalName}_converted.pdf"
                        
                        if (parentFolder != null) {
                            val newFile = parentFolder.createFile("application/pdf", partName)
                            if (newFile != null) {
                                context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                                    document.save(outputStream)
                                }
                            }
                        }
                        tempFile.delete()
                    }
                }
                document.close()
            } catch (e: Exception) {
                Timber.e(e, "Error converting Image to PDF")
            } finally {
                _isCleanupLoading.value = false
                loadCleanupFiles(context)
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun createZipFromUris(context: Context, uris: List<Uri>, zipFileName: String, parentFolderUri: Uri?, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCleanupLoading.value = true
            try {
                val finalFileName = if (zipFileName.endsWith(".zip", true)) zipFileName else "$zipFileName.zip"
                
                // 1. Create ZIP in local cache directory
                val tempZip = java.io.File(context.cacheDir, "temp_${System.currentTimeMillis()}.zip")
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(tempZip)).use { zos ->
                    for (uri in uris) {
                        val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                        val entryName = doc?.name ?: "file_${System.currentTimeMillis()}"
                        val entry = java.util.zip.ZipEntry(entryName)
                        zos.putNextEntry(entry)
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            ins.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }

                // 2. Resolve destination SAF folder
                val parentDoc = if (parentFolderUri != null) {
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, parentFolderUri)
                } else if (uris.isNotEmpty()) {
                    getDestinationFolder(context, uris.first())
                } else null

                // 3. Move ZIP to destination
                if (parentDoc != null && parentDoc.isDirectory) {
                    val zipDoc = parentDoc.createFile("application/zip", finalFileName)
                    if (zipDoc != null) {
                        context.contentResolver.openOutputStream(zipDoc.uri)?.use { os ->
                            tempZip.inputStream().use { it.copyTo(os) }
                        }
                    }
                }
                
                // Cleanup temp
                if (tempZip.exists()) {
                    tempZip.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error creating ZIP file")
            } finally {
                _isCleanupLoading.value = false
                loadCleanupFiles(context)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }
}
