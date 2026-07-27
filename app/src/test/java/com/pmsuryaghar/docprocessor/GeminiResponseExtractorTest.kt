package com.pmsuryaghar.docprocessor

import com.pmsuryaghar.docprocessor.data.util.GeminiResponseExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiResponseExtractorTest {

    @Test
    fun testExtractSuccessfulResponse() {
        val rawResponse = """
            Customer Name:
            RAMESH KUMAR
            
            Mobile Number:
            9876543210
            
            Aadhaar Number: 1234 5678 9012
            PAN Number: ABCDE1234F
            Electricity Consumer Number: 99887766
            Khata Number: Khata 42
            Plot Number: Plot 108
            
            All documents appear to belong to the same applicant. No major mismatches detected.
        """.trimIndent()

        val extracted = GeminiResponseExtractor.extract(rawResponse)

        assertEquals("RAMESH KUMAR", extracted.customerName)
        assertEquals("9876543210", extracted.mobileNumber)
        assertEquals("1234 5678 9012", extracted.details["Aadhaar Number"])
        assertEquals("ABCDE1234F", extracted.details["PAN Number"])
        assertEquals("99887766", extracted.details["Electricity Consumer Number"])
        assertEquals("Khata 42", extracted.details["Khata Number"])
        assertEquals("Plot 108", extracted.details["Plot Number"])
    }

    @Test
    fun testExtractWithBoldMarkdown() {
        val rawResponse = """
            Customer Name: **SUDHIR PATNAIK**
            Mobile Number: **9999888877**
            
            **PAN Number**: **XYZ12345**
            **IFSC Code**: **SBIN0001234**
        """.trimIndent()

        val extracted = GeminiResponseExtractor.extract(rawResponse)

        assertEquals("SUDHIR PATNAIK", extracted.customerName)
        assertEquals("9999888877", extracted.mobileNumber)
        assertEquals("XYZ12345", extracted.details["PAN Number"])
        assertEquals("SBIN0001234", extracted.details["IFSC Code"])
    }
}
