package com.pmsuryaghar.docprocessor.data.util

import android.content.Context
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import com.itextpdf.text.PageSize
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.text.BaseColor
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportGenerator {

    /**
     * Generates a DOCX verification report using Apache POI
     */
    fun generateDocxReport(
        customerName: String,
        mobileNumber: String,
        geminiResponseText: String,
        documentList: List<String>,
        outputFile: File
    ) {
        val document = XWPFDocument()
        try {
            // Title
            val titleParagraph = document.createParagraph().apply {
                alignment = ParagraphAlignment.CENTER
            }
            val titleRun = titleParagraph.createRun().apply {
                setText("PM SURYA GHAR DOCUMENT VERIFICATION REPORT")
                isBold = true
                fontSize = 16
                fontFamily = "Arial"
                color = "003366" // Dark Blue
            }

            // Spacing
            document.createParagraph()

            // Metadata Table
            val table = document.createTable(4, 2)
            table.width = 5000 // Approximately 100% width

            // Add Headers/Values
            setCellText(table.getRow(0).getCell(0), "Customer Name", true, "E6F0FA")
            setCellText(table.getRow(0).getCell(1), customerName, false, null)

            setCellText(table.getRow(1).getCell(0), "Mobile Number", true, "E6F0FA")
            setCellText(table.getRow(1).getCell(1), mobileNumber, false, null)

            setCellText(table.getRow(2).getCell(0), "Processing Date", true, "E6F0FA")
            val dateStr = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            setCellText(table.getRow(2).getCell(1), dateStr, false, null)

            setCellText(table.getRow(3).getCell(0), "Verification Status", true, "E6F0FA")
            setCellText(table.getRow(3).getCell(1), "VERIFIED & COMPLETED", true, "E6F8E6")

            // Spacing
            document.createParagraph()

            // Document list section
            val listHeader = document.createParagraph()
            listHeader.createRun().apply {
                setText("Processed Files:")
                isBold = true
                fontSize = 12
                fontFamily = "Arial"
            }
            for (docName in documentList) {
                val listParagraph = document.createParagraph()
                listParagraph.createRun().apply {
                    setText("• $docName")
                    fontSize = 10
                    fontFamily = "Arial"
                }
            }

            document.createParagraph()

            // Extracted key-value analysis section
            val extractedInfo = GeminiResponseExtractor.extract(geminiResponseText)
            if (extractedInfo.details.isNotEmpty()) {
                val analysisHeader = document.createParagraph()
                analysisHeader.createRun().apply {
                    setText("Extracted Customer Details (Key-Value Analysis):")
                    isBold = true
                    fontSize = 12
                    fontFamily = "Arial"
                }

                val detailsTable = document.createTable(extractedInfo.details.size + 1, 2)
                detailsTable.width = 5000

                // Headers
                setCellText(detailsTable.getRow(0).getCell(0), "Property", true, "003366")
                detailsTable.getRow(0).getCell(0).paragraphs[0].runs[0].color = "FFFFFF"
                setCellText(detailsTable.getRow(0).getCell(1), "Extracted Value", true, "003366")
                detailsTable.getRow(0).getCell(1).paragraphs[0].runs[0].color = "FFFFFF"

                var rowIndex = 1
                for ((key, value) in extractedInfo.details) {
                    val isEven = rowIndex % 2 == 0
                    val cellBg = if (isEven) "F2F2F2" else null
                    setCellText(detailsTable.getRow(rowIndex).getCell(0), key, true, cellBg)
                    setCellText(detailsTable.getRow(rowIndex).getCell(1), value, false, cellBg)
                    rowIndex++
                }
            }

            document.createParagraph()

            // Detailed transcription section
            val transHeader = document.createParagraph()
            transHeader.createRun().apply {
                setText("Detailed Verification Summary:")
                isBold = true
                fontSize = 12
                fontFamily = "Arial"
            }

            val lines = geminiResponseText.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.count { it == '|' } >= 2) {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().count { it == '|' } >= 2) {
                        tableLines.add(lines[i].trim())
                        i++
                    }
                    
                    val rows = mutableListOf<List<String>>()
                    for (tblLine in tableLines) {
                        val cleanLine = tblLine.replace("|", "").replace("-", "").replace(":", "").replace(" ", "")
                        if (cleanLine.isEmpty()) continue
                        
                        val rawCells = tblLine.split("|")
                        val cells = mutableListOf<String>()
                        val startIndex = if (tblLine.startsWith("|")) 1 else 0
                        val endIndex = if (tblLine.endsWith("|")) rawCells.size - 1 else rawCells.size
                        
                        for (c in startIndex until endIndex) {
                            cells.add(rawCells[c].trim())
                        }
                        if (cells.isNotEmpty()) {
                            rows.add(cells)
                        }
                    }
                    
                    if (rows.isNotEmpty()) {
                        val maxCols = rows.maxOf { it.size }
                        val docxTable = document.createTable(rows.size, maxCols)
                        docxTable.width = 5000
                        
                        for (r in rows.indices) {
                            val rowCells = rows[r]
                            val docxRow = docxTable.getRow(r)
                            for (c in rowCells.indices) {
                                if (c < docxRow.tableCells.size) {
                                    val cellValue = rowCells[c]
                                    val isHeader = r == 0
                                    val bgHex = if (isHeader) "003366" else if (r % 2 == 0) "F2F2F2" else null
                                    setCellText(docxRow.getCell(c), cellValue, isHeader, bgHex)
                                    if (isHeader) {
                                        docxRow.getCell(c).paragraphs[0].runs.firstOrNull()?.color = "FFFFFF"
                                    }
                                }
                            }
                        }
                        document.createParagraph() // spacer
                    }
                } else {
                    if (line.isNotEmpty()) {
                        val transParagraph = document.createParagraph()
                        transParagraph.createRun().apply {
                            setText(line)
                            fontSize = 10
                            fontFamily = "Arial"
                        }
                    } else {
                        document.createParagraph()
                    }
                    i++
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.write(out)
            }
            document.close()
        } catch (e: Exception) {
            Timber.e(e, "Error generating DOCX report")
            throw e
        }
    }

    /**
     * Generates a PDF verification report using iTextG
     */
    fun generatePdfReport(
        customerName: String,
        mobileNumber: String,
        geminiResponseText: String,
        documentList: List<String>,
        outputFile: File
    ) {
        val document = Document(PageSize.A4, 30f, 30f, 30f, 30f)
        var writer: PdfWriter? = null
        try {
            writer = PdfWriter.getInstance(document, FileOutputStream(outputFile))
            document.open()

            // Fonts
            val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, Font.BOLD, BaseColor(0, 51, 102))
            val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, Font.BOLD, BaseColor(0, 51, 102))
            val normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, Font.NORMAL, BaseColor.BLACK)
            val boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Font.BOLD, BaseColor.BLACK)
            val codeFont = FontFactory.getFont(FontFactory.COURIER, 9f, Font.NORMAL, BaseColor.DARK_GRAY)

            // Title
            val title = Paragraph("PM SURYA GHAR DOCUMENT VERIFICATION REPORT", titleFont).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 20f
            }
            document.add(title)

            // Metadata Table
            val metadataTable = PdfPTable(2).apply {
                widthPercentage = 100f
                setSpacingAfter(15f)
            }
            
            addPdfCell(metadataTable, "Customer Name", boldFont, BaseColor(230, 240, 250))
            addPdfCell(metadataTable, customerName, normalFont, null)
            
            addPdfCell(metadataTable, "Mobile Number", boldFont, BaseColor(230, 240, 250))
            addPdfCell(metadataTable, mobileNumber, normalFont, null)
            
            addPdfCell(metadataTable, "Processing Date", boldFont, BaseColor(230, 240, 250))
            val dateStr = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            addPdfCell(metadataTable, dateStr, normalFont, null)
            
            addPdfCell(metadataTable, "Verification Status", boldFont, BaseColor(230, 240, 250))
            addPdfCell(metadataTable, "VERIFIED & COMPLETED", boldFont, BaseColor(230, 248, 230))
            
            document.add(metadataTable)

            // Document List
            document.add(Paragraph("Processed Files:", headerFont).apply { spacingAfter = 5f })
            for (doc in documentList) {
                document.add(Paragraph("• $doc", normalFont))
            }
            document.add(Paragraph(" ", normalFont).apply { spacingAfter = 10f })

            // Extracted Info Table
            val extractedInfo = GeminiResponseExtractor.extract(geminiResponseText)
            if (extractedInfo.details.isNotEmpty()) {
                document.add(Paragraph("Extracted Customer Details (Key-Value Analysis):", headerFont).apply { spacingAfter = 5f })
                
                val detailsTable = PdfPTable(2).apply {
                    widthPercentage = 100f
                    setSpacingAfter(15f)
                }
                
                // Table Headers
                addPdfCell(detailsTable, "Property", boldFont, BaseColor(0, 51, 102), BaseColor.WHITE)
                addPdfCell(detailsTable, "Extracted Value", boldFont, BaseColor(0, 51, 102), BaseColor.WHITE)
                
                var rowIndex = 1
                for ((key, value) in extractedInfo.details) {
                    val isEven = rowIndex % 2 == 0
                    val cellBg = if (isEven) BaseColor(242, 242, 242) else null
                    addPdfCell(detailsTable, key, boldFont, cellBg)
                    addPdfCell(detailsTable, value, normalFont, cellBg)
                    rowIndex++
                }
                
                document.add(detailsTable)
            }

            // Detailed Response Transcript
            document.add(Paragraph("Detailed Verification Summary:", headerFont).apply { spacingAfter = 5f })
            
            val lines = geminiResponseText.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.count { it == '|' } >= 2) {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().count { it == '|' } >= 2) {
                        tableLines.add(lines[i].trim())
                        i++
                    }
                    
                    val rows = mutableListOf<List<String>>()
                    for (tblLine in tableLines) {
                        val cleanLine = tblLine.replace("|", "").replace("-", "").replace(":", "").replace(" ", "")
                        if (cleanLine.isEmpty()) continue
                        
                        val rawCells = tblLine.split("|")
                        val cells = mutableListOf<String>()
                        val startIndex = if (tblLine.startsWith("|")) 1 else 0
                        val endIndex = if (tblLine.endsWith("|")) rawCells.size - 1 else rawCells.size
                        
                        for (c in startIndex until endIndex) {
                            cells.add(rawCells[c].trim())
                        }
                        if (cells.isNotEmpty()) {
                            rows.add(cells)
                        }
                    }
                    
                    if (rows.isNotEmpty()) {
                        val maxCols = rows.maxOf { it.size }
                        val pdfTable = PdfPTable(maxCols).apply {
                            widthPercentage = 100f
                            spacingAfter = 10f
                        }
                        
                        for (r in rows.indices) {
                            val rowCells = rows[r]
                            for (c in 0 until maxCols) {
                                val cellValue = if (c < rowCells.size) rowCells[c] else ""
                                val isHeader = r == 0
                                val bg = if (isHeader) BaseColor(0, 51, 102) else if (r % 2 == 0) BaseColor(242, 242, 242) else null
                                val fg = if (isHeader) BaseColor.WHITE else BaseColor.BLACK
                                val font = if (isHeader) boldFont else normalFont
                                
                                addPdfCell(pdfTable, cellValue, font, bg, fg)
                            }
                        }
                        document.add(pdfTable)
                    }
                } else {
                    if (line.isNotEmpty()) {
                        document.add(Paragraph(line, normalFont))
                    } else {
                        document.add(Paragraph(" ", normalFont))
                    }
                    i++
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error generating PDF report")
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

    private fun setCellText(cell: XWPFTableCell, text: String, bold: Boolean, bgColor: String?) {
        val paragraph = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        // clear existing runs
        while (paragraph.runs.isNotEmpty()) {
            paragraph.removeRun(0)
        }
        val run = paragraph.createRun().apply {
            setText(text)
            isBold = bold
            fontSize = 10
            fontFamily = "Arial"
        }
        if (bgColor != null) {
            cell.setColor(bgColor)
        }
    }

    private fun addPdfCell(table: PdfPTable, text: String, font: Font, bgColor: BaseColor?, textColor: BaseColor = BaseColor.BLACK) {
        val styledFont = Font(font).apply { color = textColor }
        val cell = PdfPCell(Phrase(text, styledFont)).apply {
            setPadding(6f)
            if (bgColor != null) {
                backgroundColor = bgColor
            }
        }
        table.addCell(cell)
    }
}
