package com.pmsuryaghar.docprocessor.data.util

import android.content.Context
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PdfFormManager {

    /**
     * Lists all PDF templates available in the assets/pdf_templates directory.
     */
    fun getAvailableTemplates(context: Context): List<String> {
        return try {
            context.assets.list("pdf_templates")
                ?.filter { it.endsWith(".pdf", ignoreCase = true) }
                ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Extracts all unique placeholders (AcroForm field names) from a specific PDF template.
     */
    fun extractPlaceholders(context: Context, templateName: String): List<String> {
        var inputStream: InputStream? = null
        var reader: PdfReader? = null
        return try {
            inputStream = context.assets.open("pdf_templates/$templateName")
            reader = PdfReader(inputStream)
            val fields = reader.acroFields
            fields.fields.keys.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            reader?.close()
            inputStream?.close()
        }
    }

    /**
     * Replaces placeholders in the PDF with user-provided values and outputs a flattened PDF.
     */
    fun fillPdfForm(
        context: Context,
        templateName: String,
        values: Map<String, String>,
        outputFileName: String
    ): File {
        var inputStream: InputStream? = null
        var reader: PdfReader? = null
        var stamper: PdfStamper? = null
        
        try {
            inputStream = context.assets.open("pdf_templates/$templateName")
            reader = PdfReader(inputStream)
            
            val outputFile = File(context.cacheDir, outputFileName)
            val outputStream = FileOutputStream(outputFile)
            
            stamper = PdfStamper(reader, outputStream)
            val form = stamper.acroFields
            
            for ((key, value) in values) {
                form.setField(key, value)
            }
            
            // Flatten the form so it is no longer editable
            stamper.setFormFlattening(true)
            
            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            stamper?.close()
            reader?.close()
            inputStream?.close()
        }
    }
}
