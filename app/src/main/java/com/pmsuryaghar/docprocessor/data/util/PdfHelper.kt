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
     * Normalizes any raw document type string (from Gemini response) to a canonical system name.
     * This is the single source of truth for name → systemName mapping.
     * Used both for page grouping AND for file naming.
     */
    fun normalizeDocType(docType: String): String {
        val lower = docType.lowercase()
        return when {
            lower.contains("aadhaar") || lower.contains("aadhar") || lower.contains("uid") -> "Aadhaar"
            lower.contains("pan") -> "PAN"
            lower.contains("electricity") || lower.contains("consumer") || lower.contains("bill") || lower.contains("tpcodl") -> "ElectricityBill"
            lower.contains("land") || lower.contains("patta") || lower.contains("khatiyan") || lower.contains("khata") || lower.contains("revenue") || lower.contains("record") || lower.contains("bhulekh") -> "LandRecord"
            lower.contains("passbook") || lower.contains("bank") || lower.contains("account") -> "Passbook"
            lower.contains("rooftop") || lower.contains("roof") -> "RooftopPhoto"
            lower.contains("house") || lower.contains("home") || lower.contains("building") -> "HousePhoto"
            lower.contains("passport photo") || (lower.contains("passport") && !lower.contains("book")) -> "PassportPhoto"
            lower.contains("signature") || lower.contains("sign") -> "Signature"
            lower.contains("photo") || lower.contains("photograph") || lower.contains("image") -> "Photo"
            lower.contains("agreement") || lower.contains("deed") -> "Agreement"
            lower.contains("property tax") || (lower.contains("tax") && lower.contains("receipt")) -> "PropertyTax"
            lower.contains("income") && lower.contains("certificate") -> "IncomeCertificate"
            lower.contains("declaration") -> "Declaration"
            lower.contains("other") || lower.contains("unknown") || lower.contains("unidentified") -> "Other"
            else -> {
                val words = docType.split(Regex("[\\s_\\-]+")).filter { it.isNotEmpty() }
                val limitedWords = words.take(2)
                val nameStr = limitedWords.joinToString("") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                }.replace(Regex("[^a-zA-Z0-9]"), "")
                if (nameStr.isEmpty()) "Other" else nameStr
            }
        }
    }

    /**
     * Returns true if the given systemName should be saved as an image (JPG).
     * Passport Photo, Rooftop Photo, House Photo, Signature, and generic Photo types.
     */
    fun isImageType(systemName: String): Boolean {
        return systemName == "RooftopPhoto" || systemName == "PassportPhoto" ||
                systemName == "HousePhoto" || systemName == "Signature" ||
                systemName == "Photo"
    }

    /**
     * Splits a combined PDF file page-by-page into individual files based on document mapping.
     *
     * Key behaviours:
     * 1. Normalizes ALL docType strings to canonical systemName BEFORE grouping pages.
     *    This means "Aadhaar Front" (p1) + "Aadhaar Back" (p2) both → "Aadhaar" → merged into one file.
     * 2. Photo/Signature types are rendered as JPG images.
     *    For these types, a PDF copy is ALSO generated (dual format).
     * 3. All other documents are saved as PDF only.
     */
    fun splitPdfFile(combinedFile: File, pageMapping: Map<Int, String>, outputDirectory: File): Map<String, File> {
        val outputFiles = mutableMapOf<String, File>()
        if (!combinedFile.exists()) return outputFiles

        var reader: PdfReader? = null
        try {
            reader = PdfReader(combinedFile.absolutePath)
            val numPages = reader.numberOfPages

            // Step 1: Normalize raw docType → systemName and group pages by systemName
            // This merges multi-page variants (e.g. "Aadhaar Front" + "Aadhaar Back" → "Aadhaar")
            val systemNameToPages = mutableMapOf<String, MutableList<Int>>()
            for (page in 1..numPages) {
                val rawType = pageMapping[page] ?: "Other Document"
                val systemName = normalizeDocType(rawType)
                systemNameToPages.getOrPut(systemName) { mutableListOf() }.add(page)
            }

            // Step 2: For each systemName, create the output file(s)
            for ((systemName, pages) in systemNameToPages) {
                val sortedPages = pages.sorted()

                if (isImageType(systemName)) {
                    // --- IMAGE TYPE (JPG) ---
                    val jpgFileName = "$systemName.jpg"
                    val jpgFile = File(outputDirectory, jpgFileName)
                    // Render first page as JPG
                    val firstPage = sortedPages.firstOrNull() ?: 1
                    val jpgSuccess = renderPdfPageToImage(combinedFile, firstPage, jpgFile)

                    if (jpgSuccess) {
                        outputFiles[systemName] = jpgFile
                        Timber.d("Created image: $jpgFileName for page: $firstPage")
                    } else {
                        Timber.w("Image render failed for $systemName, will use PDF only")
                    }

                    // Also generate PDF (dual format) for these types
                    val pdfFileName = "$systemName.pdf"
                    val pdfFile = File(outputDirectory, pdfFileName)
                    try {
                        val doc = Document()
                        val copy = PdfCopy(doc, FileOutputStream(pdfFile))
                        doc.open()
                        for (page in sortedPages) {
                            if (page in 1..numPages) {
                                copy.addPage(copy.getImportedPage(reader, page))
                            }
                        }
                        doc.close()
                        // If JPG failed, use PDF as the primary output
                        if (!jpgSuccess) {
                            outputFiles[systemName] = pdfFile
                        }
                        Timber.d("Also created PDF: $pdfFileName for $systemName (dual format)")
                    } catch (e: Exception) {
                        Timber.e(e, "Error creating dual-format PDF for $systemName")
                    }

                } else {
                    // --- PDF TYPE ---
                    val pdfFileName = "$systemName.pdf"
                    val pdfFile = File(outputDirectory, pdfFileName)
                    try {
                        val doc = Document()
                        val copy = PdfCopy(doc, FileOutputStream(pdfFile))
                        doc.open()
                        for (page in sortedPages) {
                            if (page in 1..numPages) {
                                copy.addPage(copy.getImportedPage(reader, page))
                            }
                        }
                        doc.close()
                        outputFiles[systemName] = pdfFile
                        Timber.d("Created PDF: $pdfFileName for pages: $sortedPages")
                    } catch (e: Exception) {
                        Timber.e(e, "Error creating PDF for $systemName")
                    }
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
