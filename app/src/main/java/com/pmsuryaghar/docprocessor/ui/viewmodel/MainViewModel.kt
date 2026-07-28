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

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    // Folder review fields
    private val _detectedName = MutableStateFlow("")
    val detectedName: StateFlow<String> = _detectedName

    private val _detectedMobile = MutableStateFlow("")
    val detectedMobile: StateFlow<String> = _detectedMobile

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
                    setError("Current document batch has already been processed. Please tap the Reset button on the dashboard to start a new batch.")
                    return@launch
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
                        appSettings.lastProcessingTimestamp
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
                
                IntentManager.launchAiAgent(context, agent, appSettings.defaultGeminiPrompt, listOf(combinedFile))
                
                // Go to waiting state
                _processingState.value = ProcessingState.WAITING_GEMINI
                _statusText.value = "Waiting for $agent response..."
            } catch (e: Exception) {
                Timber.e(e, "Error during processing")
                setError("Processing failed: ${e.localizedMessage}")
            }
        }
    }

    fun onGeminiResponseReceived(responseText: String) {
        if (responseText.isEmpty()) return
        
        viewModelScope.launch {
            _processingState.value = ProcessingState.EXTRACTING_DETAILS
            _statusText.value = "Extracting Customer Details..."
            
            _rawGeminiResponse.value = responseText
            val extracted = GeminiResponseExtractor.extract(responseText)
            
            _detectedName.value = extracted.customerName.ifEmpty { "UNKNOWN_CUSTOMER" }
            _detectedMobile.value = extracted.mobileNumber.ifEmpty { "0000000000" }
            _proposedFolderName.value = "${_detectedName.value}_${_detectedMobile.value}"
            
            // Populate negative remarks and missing documents for dashboard display
            _negativeRemarks.value = extracted.negativeRemarks
            _missingDocuments.value = extracted.missingDocuments
            
            _processingState.value = ProcessingState.FOLDER_REVIEW
            _statusText.value = "Review proposed customer output folder"
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
                val extractedInfo = GeminiResponseExtractor.extract(_rawGeminiResponse.value)
                val documentMapping = extractedInfo.documentMapping

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

                _statusText.value = "Opening WhatsApp..."
                IntentManager.shareZipToWhatsApp(
                    context = context,
                    zipFile = zipFile,
                    customerName = _detectedName.value,
                    destinationNumber = appSettings.destinationWhatsAppNumber
                )

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

    fun renameCleanupFile(context: Context, uri: Uri, newName: String) {
        viewModelScope.launch {
            try {
                val fileDoc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                fileDoc?.renameTo(newName)
                loadCleanupFiles(context)
                refreshExistingFiles(context)
            } catch (e: Exception) {
                Timber.e(e, "Error renaming cleanup file")
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

    private val _cleanupWhatsappDocsFiles = MutableStateFlow<List<Triple<String, Uri, Boolean>>>(emptyList())
    val cleanupWhatsappDocsFiles: StateFlow<List<Triple<String, Uri, Boolean>>> = _cleanupWhatsappDocsFiles.asStateFlow()

    private val _cleanupWhatsappImagesFiles = MutableStateFlow<List<Triple<String, Uri, Boolean>>>(emptyList())
    val cleanupWhatsappImagesFiles: StateFlow<List<Triple<String, Uri, Boolean>>> = _cleanupWhatsappImagesFiles.asStateFlow()

    private val _cleanupSourceFiles = MutableStateFlow<List<Triple<String, Uri, Boolean>>>(emptyList())
    val cleanupSourceFiles: StateFlow<List<Triple<String, Uri, Boolean>>> = _cleanupSourceFiles.asStateFlow()

    private val _cleanupDestFiles = MutableStateFlow<List<Triple<String, Uri, Boolean>>>(emptyList())
    val cleanupDestFiles: StateFlow<List<Triple<String, Uri, Boolean>>> = _cleanupDestFiles.asStateFlow()

    fun loadCleanupFiles(context: Context) {
        viewModelScope.launch {
            // Always read fresh settings from repository to avoid stale _settings.value crash
            val currentSettings = try {
                settingsRepository.getSettings().first()
            } catch (e: Exception) {
                Timber.e(e, "Error reading settings in loadCleanupFiles")
                return@launch
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
                            .map { Triple(it.name!!, it.uri, it.isDirectory) }
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
                            .map { Triple(it.name!!, it.uri, it.isDirectory) }
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

            // 3. WhatsApp Documents Folder - show ONLY today's + yesterday's files
            val configuredDocsUriStr = currentSettings.whatsappDocsFolderUri
            if (configuredDocsUriStr.isNotEmpty()) {
                try {
                    val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(configuredDocsUriStr))
                    if (rootDoc != null && rootDoc.isDirectory) {
                        _cleanupWhatsappDocsFiles.value = rootDoc.listFiles()
                            .filter { it.name != null && !it.isDirectory && it.lastModified() >= twoDaysAgoMs }
                            .sortedByDescending { it.lastModified() }
                            .map { Triple(it.name!!, it.uri, false) }
                    } else {
                        _cleanupWhatsappDocsFiles.value = emptyList()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error reading configured WA Docs folder")
                    _cleanupWhatsappDocsFiles.value = emptyList()
                }
            } else {
                // Fallback: scan actual system WhatsApp Documents path, filter last 2 days
                val actualDocs = FileUtils.scanActualWhatsappFolder(context, isDocumentFolder = true)
                _cleanupWhatsappDocsFiles.value = actualDocs
                    .filter { it.lastModified >= twoDaysAgoMs }
                    .map { Triple(it.name, it.uri, false) }
            }

            // 4. WhatsApp Images Folder - show ONLY today's + yesterday's files
            val configuredImagesUriStr = currentSettings.whatsappImagesFolderUri
            if (configuredImagesUriStr.isNotEmpty()) {
                try {
                    val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(configuredImagesUriStr))
                    if (rootDoc != null && rootDoc.isDirectory) {
                        _cleanupWhatsappImagesFiles.value = rootDoc.listFiles()
                            .filter { it.name != null && !it.isDirectory && it.lastModified() >= twoDaysAgoMs }
                            .sortedByDescending { it.lastModified() }
                            .map { Triple(it.name!!, it.uri, false) }
                    } else {
                        _cleanupWhatsappImagesFiles.value = emptyList()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error reading configured WA Images folder")
                    _cleanupWhatsappImagesFiles.value = emptyList()
                }
            } else {
                // Fallback: scan actual system WhatsApp Images path, filter last 2 days
                val actualImages = FileUtils.scanActualWhatsappFolder(context, isDocumentFolder = false)
                _cleanupWhatsappImagesFiles.value = actualImages
                    .filter { it.lastModified >= twoDaysAgoMs }
                    .map { Triple(it.name, it.uri, false) }
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

    fun deleteCleanupFiles(context: Context, uris: List<Uri>, isSource: Boolean) {
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
            }
        }
    }

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    /**
     * Copies today's and yesterday's files from the actual WhatsApp app folders into the
     * configured WA Documents and WA Images folders. This gives users access to recent
     * WhatsApp-received files from within the app.
     */
    fun syncWhatsappFiles(context: Context, isDocumentFolder: Boolean) {
        viewModelScope.launch {
            val currentSettings = try {
                settingsRepository.getSettings().first()
            } catch (e: Exception) {
                _syncStatus.value = "Failed to read settings"
                return@launch
            }

            val targetUriStr = if (isDocumentFolder) currentSettings.whatsappDocsFolderUri
                               else currentSettings.whatsappImagesFolderUri
            if (targetUriStr.isEmpty()) {
                _syncStatus.value = "Configure ${if (isDocumentFolder) "WA Documents" else "WA Images"} folder in Settings first"
                return@launch
            }

            _syncStatus.value = "Syncing recent WhatsApp files..."
            try {
                val twoDaysAgoMs = System.currentTimeMillis() - (2L * 24L * 60L * 60L * 1000L)
                val waFiles = withContext(Dispatchers.IO) {
                    FileUtils.scanActualWhatsappFolder(context, isDocumentFolder)
                        .filter { it.lastModified >= twoDaysAgoMs }
                }

                if (waFiles.isEmpty()) {
                    _syncStatus.value = "No recent files found in WhatsApp folder (last 2 days)"
                    loadCleanupFiles(context)
                    return@launch
                }

                val targetUri = Uri.parse(targetUriStr)
                var copied = 0
                var skipped = 0

                withContext(Dispatchers.IO) {
                    val targetDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, targetUri)
                    for (waFile in waFiles) {
                        try {
                            // Skip if file already exists in target folder
                            if (targetDoc?.findFile(waFile.name) != null) {
                                skipped++
                                continue
                            }
                            val result = FileUtils.copySharedUriToSourceFolder(context, waFile.uri, targetUri)
                            if (result != null) copied++
                        } catch (e: Exception) {
                            Timber.e(e, "Error copying WA file: ${waFile.name}")
                        }
                    }
                }

                _syncStatus.value = "Sync done: $copied copied, $skipped already present"
                loadCleanupFiles(context)
            } catch (e: Exception) {
                Timber.e(e, "Error during WA sync")
                _syncStatus.value = "Sync failed: ${e.localizedMessage}"
            }
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

    fun loadFolderContents(context: Context, folderUri: Uri): List<Triple<String, Uri, Boolean>> {
        return try {
            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                ?: androidx.documentfile.provider.DocumentFile.fromSingleUri(context, folderUri)
            if (rootDoc != null && rootDoc.isDirectory) {
                rootDoc.listFiles()
                    .filter { it.name != null }
                    .map { Triple(it.name!!, it.uri, it.isDirectory) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading subfolder contents")
            emptyList()
        }
    }
}
