package com.pmsuryaghar.docprocessor.data.util

import net.lingala.zip4j.ZipFile
import timber.log.Timber
import java.io.File

object ZipCreator {

    /**
     * Compresses multiple files into a single ZIP archive.
     */
    fun createZip(
        filesToZip: List<File>,
        outputZipFile: File
    ) {
        try {
            if (outputZipFile.exists()) {
                outputZipFile.delete()
            }
            
            val zipFile = ZipFile(outputZipFile)
            for (file in filesToZip) {
                if (file.exists()) {
                    if (file.isDirectory) {
                        zipFile.addFolder(file)
                    } else {
                        zipFile.addFile(file)
                    }
                }
            }
            Timber.d("ZIP created successfully at: ${outputZipFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Error creating ZIP file")
            throw e
        }
    }
}
