package com.pmsuryaghar.docprocessor.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.pdf.PdfWriter
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PdfGenerator {

    /**
     * Converts a list of image Uris into a single PDF file and applies compression.
     */
    fun generatePdfFromImages(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File,
        maxSizeMb: Float
    ) {
        val document = Document(PageSize.A4, 10f, 10f, 10f, 10f)
        var writer: PdfWriter? = null
        try {
            writer = PdfWriter.getInstance(document, FileOutputStream(outputFile))
            document.open()

            for (uri in imageUris) {
                val imageBytes = getCompressedImageBytes(context, uri, maxSizeMb)
                if (imageBytes != null) {
                    val image = Image.getInstance(imageBytes)
                    val pageWidth = document.pageSize.width - 20f
                    val pageHeight = document.pageSize.height - 20f
                    image.scaleToFit(pageWidth, pageHeight)
                    image.setAlignment(Image.MIDDLE)
                    document.add(image)
                    document.newPage()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error generating PDF from images")
            throw e
        } finally {
            try {
                document.close()
            } catch (e: Exception) {}
            try {
                writer?.close()
            } catch (e: Exception) {}
        }
    }

    /**
     * Compresses an existing PDF file.
     * Extracts pages as bitmaps, downscales, and packages into a new compressed PDF.
     */
    fun compressPdfFile(
        context: Context,
        inputFile: File,
        outputFile: File,
        maxSizeMb: Float
    ) {
        val fileSizeInBytes = inputFile.length()
        if (fileSizeInBytes <= maxSizeMb * 1024 * 1024) {
            // Already below limit, copy directly
            inputFile.copyTo(outputFile, overwrite = true)
            return
        }

        Timber.d("PDF is larger than limit (${fileSizeInBytes / (1024 * 1024)} MB > $maxSizeMb MB). Compressing...")
        
        val document = Document(PageSize.A4, 10f, 10f, 10f, 10f)
        var writer: PdfWriter? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        
        try {
            writer = PdfWriter.getInstance(document, FileOutputStream(outputFile))
            document.open()
            
            pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // Render page to bitmap. Use 1.5x resolution for good OCR readability in Gemini
                val width = (page.width * 1.5).toInt()
                val height = (page.height * 1.5).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                // Compress bitmap
                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 65, bos)
                val compressedBytes = bos.toByteArray()
                
                bitmap.recycle()
                page.close()
                
                val image = Image.getInstance(compressedBytes)
                val pageWidth = document.pageSize.width - 20f
                val pageHeight = document.pageSize.height - 20f
                image.scaleToFit(pageWidth, pageHeight)
                image.setAlignment(Image.MIDDLE)
                document.add(image)
                document.newPage()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error compressing PDF")
            throw e
        } finally {
            try {
                renderer?.close()
            } catch (e: Exception) {}
            try {
                pfd?.close()
            } catch (e: Exception) {}
            try {
                document.close()
            } catch (e: Exception) {}
            try {
                writer?.close()
            } catch (e: Exception) {}
        }
    }

    private fun getCompressedImageBytes(context: Context, uri: Uri, maxSizeMb: Float): ByteArray? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Target max dimension of 1600px for readable documents (e.g. Aadhaar text)
            val maxDimension = 1600
            var scale = 1
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                val widthScale = options.outWidth / maxDimension
                val heightScale = options.outHeight / maxDimension
                scale = Math.max(widthScale, heightScale)
            }

            inputStream = context.contentResolver.openInputStream(uri)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions) ?: return null
            inputStream?.close()

            val bos = ByteArrayOutputStream()
            // Adjust quality: lower threshold means more aggressive compression
            val quality = if (maxSizeMb <= 1.0f) 55 else 70
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            val bytes = bos.toByteArray()
            bitmap.recycle()
            return bytes
        } catch (e: Exception) {
            Timber.e(e, "Error compressing image from URI: $uri")
            return null
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {}
        }
    }
}
