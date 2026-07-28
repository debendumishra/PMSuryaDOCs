package com.pmsuryaghar.docprocessor.data.util

import timber.log.Timber
import java.util.Locale

object GeminiResponseExtractor {

    data class ExtractedInfo(
        val customerName: String,
        val mobileNumber: String,
        val documentMapping: Map<Int, String>,
        val details: Map<String, String>,
        val negativeRemarks: List<String> = emptyList(),
        val missingDocuments: List<String> = emptyList()
    )

    fun extract(text: String): ExtractedInfo {
        var customerName = ""
        var mobileNumber = ""
        val documentMapping = mutableMapOf<Int, String>()
        val details = mutableMapOf<String, String>()
        val negativeRemarks = mutableListOf<String>()
        val missingDocuments = mutableListOf<String>()

        val lines = text.lines()
        var parsingMapping = false

        for (i in lines.indices) {
            val rawLine = lines[i]
            val cleanLine = cleanMarkdown(rawLine).trim()
            val lowerLine = cleanLine.lowercase(Locale.ROOT)

            // 1. Process table rows (Pipe separated "|" or Tab separated "\t")
            val hasPipe = rawLine.contains("|") && rawLine.count { it == '|' } >= 2
            val hasTab = rawLine.contains("\t") && rawLine.split("\t").size >= 2

            if (hasPipe || hasTab) {
                val cols = if (hasPipe) {
                    rawLine.split("|").map { cleanMarkdown(it).trim() }
                } else {
                    rawLine.split("\t").map { cleanMarkdown(it).trim() }
                }

                // Remove leading/trailing empty cells if pipe-split created them
                val filteredCols = cols.filterIndexed { index, cell ->
                    if (hasPipe && (index == 0 || index == cols.size - 1) && cell.isEmpty()) false else true
                }

                if (filteredCols.size >= 2) {
                    // Check if 4-column format (ChatGPT: Sl. No. | Particular | Extracted Information | Status)
                    // or 3-column format (Gemini: Field | Information | Status)
                    val keyIndex = if (filteredCols.size >= 4) 1 else 0
                    val valueIndex = if (filteredCols.size >= 4) 2 else if (filteredCols.size >= 3) 1 else 1

                    val key = filteredCols.getOrNull(keyIndex) ?: ""
                    val value = filteredCols.getOrNull(valueIndex) ?: ""

                    if (key.contains("Customer Name", ignoreCase = true)) {
                        val rawVal = value.split(Regex("[\\(\\[]"))[0].trim()
                        if (rawVal.isNotEmpty() && !rawVal.equals("Extracted Information", ignoreCase = true) && !rawVal.equals("Information Present in Documents", ignoreCase = true)) {
                            customerName = rawVal
                        }
                    } else if (key.contains("Mobile Number", ignoreCase = true) ||
                        key.contains("Aadhaar Mobile No", ignoreCase = true) ||
                        key.contains("Mobile No", ignoreCase = true) ||
                        key.contains("Mobile", ignoreCase = true)) {
                        val digits = value.replace(Regex("[^0-9]"), "").trim()
                        if (digits.length >= 10 && mobileNumber.isEmpty()) {
                            mobileNumber = digits.take(10)
                        }
                    } else {
                        if (key.length in 3..40 && value.length in 2..150 &&
                            !value.contains("---") && !key.contains("---") &&
                            !key.equals("Field", ignoreCase = true) &&
                            !key.equals("Particular", ignoreCase = true) &&
                            !key.equals("Sl. No.", ignoreCase = true) &&
                            !key.equals("Document Type", ignoreCase = true) &&
                            !key.equals("Item", ignoreCase = true) &&
                            !key.equals("Document", ignoreCase = true) &&
                            !key.equals("Page No.", ignoreCase = true)
                        ) {
                            details[key] = value
                        }
                    }
                }
            }

            // 2. Extract customer name from simple key-value lines
            if (cleanLine.startsWith("Customer Name:", ignoreCase = true)) {
                val value = cleanLine.substring("Customer Name:".length).trim()
                val rawVal = value.split(Regex("[\\(\\[]"))[0].trim()
                if (rawVal.isNotEmpty()) {
                    customerName = rawVal
                } else if (i + 1 < lines.size) {
                    val nextLineClean = cleanMarkdown(lines[i + 1]).trim()
                    val rawValNext = nextLineClean.split(Regex("[\\(\\[]"))[0].trim()
                    if (rawValNext.isNotEmpty()) {
                        customerName = rawValNext
                    }
                }
            }

            // 3. Extract mobile number from simple key-value lines
            if (cleanLine.startsWith("Mobile Number:", ignoreCase = true) || cleanLine.startsWith("Aadhaar Mobile No:", ignoreCase = true)) {
                val colonPos = cleanLine.indexOf(':')
                var value = cleanLine.substring(colonPos + 1).trim()
                val digitsOnSameLine = value.replace(Regex("[^0-9]"), "").trim()
                if (digitsOnSameLine.length >= 10 && mobileNumber.isEmpty()) {
                    mobileNumber = digitsOnSameLine.take(10)
                } else if (i + 1 < lines.size) {
                    val nextLineClean = cleanMarkdown(lines[i + 1]).trim()
                    val digitsNext = nextLineClean.replace(Regex("[^0-9]"), "").trim()
                    if (digitsNext.length >= 10 && mobileNumber.isEmpty()) {
                        mobileNumber = digitsNext.take(10)
                    }
                }
            }

            // 4. Document Page Identification Trigger - catch all common section header formats
            if (lowerLine.contains("document-wise page identification") ||
                lowerLine.contains("document page identification") ||
                lowerLine.contains("page identification") ||
                lowerLine.contains("document mapping") ||
                lowerLine.contains("page breakdown") ||
                lowerLine.contains("page mapping") ||
                lowerLine.contains("page-wise") ||
                lowerLine.contains("document-wise") ||
                (lowerLine.contains("page") && lowerLine.contains("document type"))) {
                parsingMapping = true
                continue
            }

            if (parsingMapping) {
                // Parse all page-mapping lines once we're in section
                val parsed = parsePageMappingLine(cleanLine)
                if (parsed != null) {
                    val (pages, docType) = parsed
                    for (page in pages) {
                        documentMapping[page] = docType
                    }
                }
            } else {
                // Global fallback: try to parse ANY line starting with Page/Pages or numbered list with Page prefix
                val trimmedForPage = cleanLine.trimStart { it.isDigit() || it == '.' || it == ' ' }
                val looksLikePageLine = cleanLine.startsWith("Page ", ignoreCase = true) ||
                    cleanLine.startsWith("Pages ", ignoreCase = true) ||
                    trimmedForPage.startsWith("Page ", ignoreCase = true) ||
                    trimmedForPage.startsWith("Pages ", ignoreCase = true)
                if (looksLikePageLine) {
                    val parsed = parsePageMappingLine(cleanLine)
                    if (parsed != null) {
                        val (pages, docType) = parsed
                        for (page in pages) {
                            documentMapping[page] = docType
                        }
                    }
                }
            }

            // 5. Extract other fields from simple key-value lines (e.g. "Bank Account No: 37992511069")
            val colonIndex = cleanLine.indexOf(':')
            if (colonIndex > 0 && colonIndex < cleanLine.length - 1) {
                val key = cleanLine.substring(0, colonIndex).trim()
                val value = cleanLine.substring(colonIndex + 1).trim()

                val trimmedRaw = rawLine.trim()
                val isBullet = (trimmedRaw.startsWith("- ") || trimmedRaw.startsWith("* ") || trimmedRaw.startsWith("• "))

                if (key.length in 3..40 && value.length in 2..100 &&
                    !key.equals("Customer Name", ignoreCase = true) &&
                    !key.equals("Mobile Number", ignoreCase = true) &&
                    !key.contains("http") &&
                    !isBullet
                ) {
                    details[key] = value
                }
            }

            // 6. Detect negative remarks and missing document lines
            val isNegativeLine = lowerLine.contains("missing") ||
                lowerLine.contains("not found") ||
                lowerLine.contains("not available") ||
                lowerLine.contains("mismatch") ||
                lowerLine.contains("discrepancy") ||
                lowerLine.contains("unclear") ||
                lowerLine.contains("unreadable") ||
                lowerLine.contains("blurry") ||
                lowerLine.contains("illegible") ||
                lowerLine.contains("expired") ||
                lowerLine.contains("invalid") ||
                lowerLine.contains("does not match") ||
                lowerLine.contains("name mismatch") ||
                lowerLine.contains("address mismatch") ||
                lowerLine.contains("❌") ||
                lowerLine.contains("✗") ||
                lowerLine.contains("[missing]") ||
                lowerLine.contains("not provided")

            val isMissingDocLine = lowerLine.contains("missing document") ||
                lowerLine.contains("document not found") ||
                lowerLine.contains("document missing") ||
                lowerLine.contains("required document") ||
                (lowerLine.contains("missing") && (
                    lowerLine.contains("aadhaar") || lowerLine.contains("pan") ||
                    lowerLine.contains("electricity") || lowerLine.contains("passbook") ||
                    lowerLine.contains("land") || lowerLine.contains("photo") ||
                    lowerLine.contains("signature") || lowerLine.contains("certificate")
                ))

            if (cleanLine.length > 10) {
                if (isMissingDocLine) {
                    missingDocuments.add(cleanLine.trimStart('-', '*', '•', ' '))
                } else if (isNegativeLine) {
                    negativeRemarks.add(cleanLine.trimStart('-', '*', '•', ' '))
                }
            }
        }

        // Clean up any remaining markdown characters
        customerName = cleanMarkdown(customerName)
        mobileNumber = cleanMarkdown(mobileNumber)

        return ExtractedInfo(
            customerName = customerName,
            mobileNumber = mobileNumber,
            documentMapping = documentMapping,
            details = details,
            negativeRemarks = negativeRemarks.distinct().take(20),
            missingDocuments = missingDocuments.distinct().take(10)
        )
    }

    private fun cleanMarkdown(value: String): String {
        return value.replace(Regex("[*#_`~]"), "").trim()
    }

    fun parsePageMappingLine(line: String): Pair<List<Int>, String>? {
        var clean = cleanMarkdown(line).trim()
        if (clean.isEmpty()) return null

        // Strip leading list numbers, bullets, or pipes e.g. "1. Page 1: ..." or "* Page 1: ..." or "| Page 1 | Aadhaar Card |"
        clean = clean.replace(Regex("^(?:[\\d\\.\\*\\-\\+]+|\\|)\\s*"), "").trim()
        if (clean.startsWith("|")) clean = clean.substring(1).trim()
        if (clean.endsWith("|")) clean = clean.substring(0, clean.length - 1).trim()

        // Handle table row split e.g. "Page 1 | Aadhaar Card"
        if (clean.contains("|")) {
            val parts = clean.split("|").map { cleanMarkdown(it).trim() }
            if (parts.size >= 2) {
                val pagePart = parts[0]
                val docPart = parts[1]
                val pageDigits = pagePart.replace(Regex("[^0-9]"), "").toIntOrNull()
                if (pageDigits != null && docPart.isNotBlank() && !docPart.equals("Document Type", ignoreCase = true)) {
                    return Pair(listOf(pageDigits), docPart)
                }
            }
        }

        // Support formats:
        // Page 1: Electricity Bill
        // Page 1 - Aadhaar Card
        // Page 2 and 3 - Agreement
        // Pages 4 to 6: Land Record
        val regex = Regex("^(?:Pages?|Page)?\\s*([\\d\\s\\-\\,andto&]+?)(?:\\s*[\t\\|:\\-]+\\s*|\\s+)(.+)$", RegexOption.IGNORE_CASE)
        val matchResult = regex.matchEntire(clean.trim()) ?: return null

        val pageText = matchResult.groups[1]?.value?.trim() ?: return null
        val docType = matchResult.groups[2]?.value?.replace(Regex("[*#_`~]"), "")?.trim() ?: return null
        if (docType.isEmpty() || docType.equals("Document Type", ignoreCase = true) || docType.equals("Particular", ignoreCase = true)) return null

        val pages = mutableListOf<Int>()
        try {
            if (pageText.contains("and", ignoreCase = true) || pageText.contains("&") || pageText.contains(",")) {
                val parts = pageText.split(Regex("and|&|,", RegexOption.IGNORE_CASE))
                for (p in parts) {
                    p.trim().toIntOrNull()?.let { pages.add(it) }
                }
            } else if (pageText.contains("to", ignoreCase = true)) {
                val parts = pageText.split(Regex("to", RegexOption.IGNORE_CASE))
                if (parts.size == 2) {
                    val start = parts[0].trim().toIntOrNull()
                    val end = parts[1].trim().toIntOrNull()
                    if (start != null && end != null) {
                        for (p in start..end) {
                            pages.add(p)
                        }
                    }
                }
            } else if (pageText.contains("-")) {
                val parts = pageText.split("-")
                if (parts.size == 2) {
                    val start = parts[0].trim().toIntOrNull()
                    val end = parts[1].trim().toIntOrNull()
                    if (start != null && end != null) {
                        for (p in start..end) {
                            pages.add(p)
                        }
                    }
                }
            } else {
                pageText.toIntOrNull()?.let { pages.add(it) }
            }
        } catch (e: Exception) {
            // ignore
        }

        if (pages.isEmpty()) return null
        return Pair(pages, docType)
    }
}
