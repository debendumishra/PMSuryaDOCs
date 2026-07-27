package com.pmsuryaghar.docprocessor.data.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfCopy
import com.itextpdf.text.pdf.PdfReader
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object PdfHelper {

    /**
     * Merges a list of PDF files into a single multi-page PDF document.
     */
    fun mergePdfFiles(files: List<File>, outputFile: File) {
        try {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            val document = Document()
            val copy = PdfCopy(document, FileOutputStream(outputFile))
            document.open()
            
            for (file in files) {
                if (file.exists() && file.length() > 0) {
                    try {
                        val reader = PdfReader(file.absolutePath)
                        val numberOfPages = reader.numberOfPages
                        for (page in 1..numberOfPages) {
                            val importedPage = copy.getImportedPage(reader, page)
                            copy.addPage(importedPage)
                        }
                        reader.close()
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading PDF file: ${file.name} during merge")
                    }
                }
            }
            document.close()
            Timber.d("Merged ${files.size} PDFs successfully into ${outputFile.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to merge PDF files into ${outputFile.name}")
            throw e
        }
    }

    /**
     * Splits a combined PDF file page-by-page into individual files based on document mapping.
     * Photo document types (RooftopPhoto, PassportPhoto, HousePhoto, etc.) are rendered as JPG images.
     */
    fun splitPdfFile(combinedFile: File, pageMapping: Map<Int, String>, outputDirectory: File): Map<String, File> {
        val outputFiles = mutableMapOf<String, File>()
        if (!combinedFile.exists()) return outputFiles

        var reader: PdfReader? = null
        try {
            reader = PdfReader(combinedFile.absolutePath)
            val numPages = reader.numberOfPages

            // Group page numbers by document type name (e.g. "Aadhaar Card" -> listOf(1, 2))
            val typeToPages = mutableMapOf<String, MutableList<Int>>()
            for (page in 1..numPages) {
                val type = pageMapping[page] ?: "Other Document"
                typeToPages.getOrPut(type) { mutableListOf() }.add(page)
            }

            for ((docType, pages) in typeToPages) {
                val lowerType = docType.lowercase()
                val systemName = when {
                    lowerType.contains("aadhaar") || lowerType.contains("aadhar") -> "Aadhaar"
                    lowerType.contains("pan") -> "PAN"
                    lowerType.contains("electricity") || lowerType.contains("consumer") || lowerType.contains("bill") -> "ElectricityBill"
                    lowerType.contains("land") || lowerType.contains("patta") || lowerType.contains("khatiyan") || lowerType.contains("revenue") || lowerType.contains("record") -> "LandRecord"
                    lowerType.contains("passbook") || lowerType.contains("bank") || lowerType.contains("account") -> "Passbook"
                    lowerType.contains("rooftop") || lowerType.contains("roof") -> "RooftopPhoto"
                    lowerType.contains("house") || lowerType.contains("home") -> "HousePhoto"
                    lowerType.contains("passport") -> "PassportPhoto"
                    lowerType.contains("photo") || lowerType.contains("photograph") -> "Photo"
                    lowerType.contains("signature") -> "Signature"
                    lowerType.contains("agreement") || lowerType.contains("deed") -> "Agreement"
                    lowerType.contains("tax") || lowerType.contains("receipt") -> "PropertyTax"
                    lowerType.contains("income") || lowerType.contains("certificate") -> "IncomeCertificate"
                    lowerType.contains("declaration") -> "Declaration"
                    else -> {
                        val words = docType.split(Regex("[\\s_\\-]+")).filter { it.isNotEmpty() }
                        val limitedWords = words.take(2)
                        val nameStr = limitedWords.joinToString("") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                        }.replace(Regex("[^a-zA-Z0-9]"), "")
                        if (nameStr.isEmpty()) "Other" else nameStr
                    }
                }

                val isPhoto = systemName == "RooftopPhoto" || systemName == "PassportPhoto" ||
                        systemName == "HousePhoto" || systemName == "Photo" ||
                        lowerType.contains("photo") || lowerType.contains("photograph")

                if (isPhoto) {
                    val fileName = "$systemName.jpg"
                    val targetFile = File(outputDirectory, fileName)
                    val firstPage = pages.firstOrNull() ?: 1
                    val success = renderPdfPageToImage(combinedFile, firstPage, targetFile)

                    if (success) {
                        outputFiles[systemName] = targetFile
                        Timber.d("Created photo image: $fileName for page: $firstPage")
                    } else {
                        // Fallback to PDF if rendering image fails
                        val pdfFileName = "$systemName.pdf"
                        val pdfTargetFile = File(outputDirectory, pdfFileName)
                        val document = Document()
                        val copy = PdfCopy(document, FileOutputStream(pdfTargetFile))
                        document.open()
                        for (page in pages) {
                            if (page in 1..numPages) {
                                val importedPage = copy.getImportedPage(reader, page)
                                copy.addPage(importedPage)
                            }
                        }
                        document.close()
                        outputFiles[systemName] = pdfTargetFile
                    }
                } else {
                    val fileName = "$systemName.pdf"
                    val targetFile = File(outputDirectory, fileName)
                    val document = Document()
                    val copy = PdfCopy(document, FileOutputStream(targetFile))
                    document.open()

                    for (page in pages) {
                        if (page in 1..numPages) {
                            val importedPage = copy.getImportedPage(reader, page)
                            copy.addPage(importedPage)
                        }
                    }

                    document.close()
                    outputFiles[systemName] = targetFile
                    Timber.d("Created split document: $fileName for pages: $pages")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error splitting combined PDF file")
            throw e
        } finally {
            reader?.close()
        }
        return outputFiles
    }

    /**
     * Renders a specific page of a PDF file to a JPEG image file.
     */
    fun renderPdfPageToImage(pdfFile: File, pageNumber: Int, outputFile: File): Boolean {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        try {
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            val pageIndex = pageNumber - 1
            if (pageIndex in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(pageIndex)
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()
                return true
            }
        } catch (e: Exception) {
            Timber.e(e, "Error rendering PDF page $pageNumber to image file")
        } finally {
            try { pdfRenderer?.close() } catch (e: Exception) {}
            try { fileDescriptor?.close() } catch (e: Exception) {}
        }
        return false
    }
}
