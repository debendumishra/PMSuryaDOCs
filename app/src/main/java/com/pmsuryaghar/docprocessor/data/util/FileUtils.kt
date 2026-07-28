package com.pmsuryaghar.docprocessor.data.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber
import java.io.File

object FileUtils {

    data class SourceFile(
        val name: String,
        val uri: Uri,
        val lastModified: Long
    )

    /**
     * Copies content from an Android content URI (e.g. from SAF) to a local File in the app cache.
     */
    fun copyUriToLocalFile(context: Context, sourceUri: Uri, destFile: File) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy URI $sourceUri to local file ${destFile.absolutePath}")
            throw e
        }
    }

    /**
     * Scans a target SAF directory Uri for new files (JPG, JPEG, PNG, PDF) modified after [lastProcessedTimestamp].
     */
    fun scanSourceFolder(context: Context, treeUri: Uri, lastProcessedTimestamp: Long): List<SourceFile> {
        val list = mutableListOf<SourceFile>()
        try {
            val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            val files = rootDir.listFiles()
            
            for (file in files) {
                if (file.isFile && file.name != null) {
                    val name = file.name!!
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "pdf") {
                        val modTime = file.lastModified()
                        // If file is newer than last processed timestamp
                        if (modTime > lastProcessedTimestamp) {
                            list.add(SourceFile(name, file.uri, modTime))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SAF source directory: $treeUri")
        }
        return list.sortedBy { it.lastModified }
    }

    /**
     * Scans system Android storage paths for actual WhatsApp Documents and Images.
     */
    fun scanActualWhatsappFolder(context: Context, isDocumentFolder: Boolean): List<SourceFile> {
        val list = mutableListOf<SourceFile>()
        try {
            val extDir = android.os.Environment.getExternalStorageDirectory()
            val subFolder = if (isDocumentFolder) "WhatsApp Documents" else "WhatsApp Images"

            val candidateFolders = listOf(
                File(extDir, "Android/media/com.whatsapp/WhatsApp/Media/$subFolder"),
                File(extDir, "Android/media/com.whatsapp/Media/$subFolder"),
                File(extDir, "Android/Media/com.whatsapp/Media/$subFolder"),
                File(extDir, "Android/Media/com.whatsapp/WhatsApp/Media/$subFolder"),
                File(extDir, "WhatsApp/Media/$subFolder")
            )

            val existingDirs = candidateFolders.filter { it.exists() && it.isDirectory }

            for (dir in existingDirs) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.startsWith(".")) {
                        if (list.none { it.name == file.name }) {
                            list.add(SourceFile(file.name, Uri.fromFile(file), file.lastModified()))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading actual WhatsApp folder")
        }
        return list.sortedByDescending { it.lastModified }
    }

    /**
     * Writes a local file from app cache to a SAF folder directory tree.
     */
    fun writeLocalFileToSafTree(
        context: Context,
        baseTreeUri: Uri,
        subfolderName: String,
        fileName: String,
        mimeType: String,
        localFile: File
    ): Uri {
        try {
            val rootDir = DocumentFile.fromTreeUri(context, baseTreeUri) 
                ?: throw Exception("Invalid destination folder URI: $baseTreeUri")
            
            if (!rootDir.canWrite()) {
                throw Exception("No write permissions for output directory. Please select a different location (like 'Documents').")
            }
            
            // Resolve or create customer directory (e.g., RAMESH KUMAR_9876543210)
            var subfolder: DocumentFile? = null
            val existingFiles = rootDir.listFiles()
            for (file in existingFiles) {
                if (file.isDirectory && file.name.equals(subfolderName, ignoreCase = true)) {
                    subfolder = file
                    break
                }
            }
            if (subfolder == null) {
                subfolder = rootDir.createDirectory(subfolderName)
            }
            if (subfolder == null || !subfolder.isDirectory) {
                throw Exception("Could not create customer subfolder: $subfolderName. Make sure you select a writeable folder.")
            }
            
            // Delete existing file if present to overwrite
            var docFile = subfolder.findFile(fileName)
            if (docFile != null) {
                docFile.delete()
            }
            
            docFile = subfolder.createFile(mimeType, fileName) 
                ?: throw Exception("Could not create file $fileName in subfolder $subfolderName")
            
            context.contentResolver.openOutputStream(docFile.uri)?.use { output ->
                localFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Failed to open output stream for writing file: $fileName")
            Timber.d("Successfully wrote file to SAF: $fileName inside $subfolderName")
            return docFile.uri
        } catch (e: Exception) {
            Timber.e(e, "Error writing local file $fileName to SAF tree")
            throw e
        }
    }

    fun copySharedUriToSourceFolder(context: Context, sourceUri: Uri, targetFolderUri: Uri): Uri? {
        try {
            val targetDir = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return null
            if (!targetDir.canWrite()) return null
            
            val originalName = DocumentFile.fromSingleUri(context, sourceUri)?.name 
                ?: sourceUri.lastPathSegment 
                ?: "shared_file_${System.currentTimeMillis()}"
                
            val mimeType = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"
            
            var docFile = targetDir.findFile(originalName)
            if (docFile != null) {
                docFile.delete()
            }
            docFile = targetDir.createFile(mimeType, originalName) ?: return null
            
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(docFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            return docFile.uri
        } catch (e: Exception) {
            Timber.e(e, "Error copying shared URI to source folder: ${e.message}")
            return null
        }
    }

    /**
     * Shares single or multiple URIs using Android Intent ACTION_SEND or ACTION_SEND_MULTIPLE.
     */
    fun shareFiles(context: Context, uris: List<Uri>, chooserTitle: String = "Share Selected Files") {
        if (uris.isEmpty()) return
        try {
            val intent = if (uris.size == 1) {
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(uris[0]) ?: "*/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(android.content.Intent.createChooser(intent, chooserTitle))
        } catch (e: Exception) {
            Timber.e(e, "Error sharing files via intent: ${e.message}")
        }
    }
}
