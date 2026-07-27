package com.pmsuryaghar.docprocessor

import com.pmsuryaghar.docprocessor.data.util.DocumentClassifier
import com.pmsuryaghar.docprocessor.domain.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentClassifierTest {

    @Test
    fun testClassifyAadhaar() {
        assertEquals(DocumentType.AADHAAR, DocumentClassifier.classify("my_aadhaar_card.jpg"))
        assertEquals(DocumentType.AADHAAR, DocumentClassifier.classify("adhar_front.png"))
        assertEquals(DocumentType.AADHAAR, DocumentClassifier.classify("uidai_back.jpg"))
    }

    @Test
    fun testClassifyPan() {
        assertEquals(DocumentType.PAN, DocumentClassifier.classify("pan_card.pdf"))
        assertEquals(DocumentType.PAN, DocumentClassifier.classify("pancard.jpg"))
    }

    @Test
    fun testClassifyElectricity() {
        assertEquals(DocumentType.ELECTRICITY_BILL, DocumentClassifier.classify("electricity_bill_june.pdf"))
        assertEquals(DocumentType.ELECTRICITY_BILL, DocumentClassifier.classify("tpsodl_eb_bill.png"))
        assertEquals(DocumentType.ELECTRICITY_BILL, DocumentClassifier.classify("bijli_bill.jpg"))
    }

    @Test
    fun testClassifyLandRecord() {
        assertEquals(DocumentType.LAND_RECORD, DocumentClassifier.classify("land_patta.pdf"))
        assertEquals(DocumentType.LAND_RECORD, DocumentClassifier.classify("ror_record.jpg"))
        assertEquals(DocumentType.LAND_RECORD, DocumentClassifier.classify("bhulekh_details.png"))
    }

    @Test
    fun testClassifyPassbook() {
        assertEquals(DocumentType.PASSBOOK, DocumentClassifier.classify("sbi_passbook.jpg"))
        assertEquals(DocumentType.PASSBOOK, DocumentClassifier.classify("bank_statement.pdf"))
        assertEquals(DocumentType.PASSBOOK, DocumentClassifier.classify("cancelled_cheque.png"))
    }

    @Test
    fun testClassifyRooftop() {
        assertEquals(DocumentType.ROOFTOP_PHOTO, DocumentClassifier.classify("rooftop_solar.jpg"))
        assertEquals(DocumentType.ROOFTOP_PHOTO, DocumentClassifier.classify("roof_view.png"))
    }

    @Test
    fun testClassifyHouse() {
        assertEquals(DocumentType.HOUSE_PHOTO, DocumentClassifier.classify("house_front.jpg"))
        assertEquals(DocumentType.HOUSE_PHOTO, DocumentClassifier.classify("home_building.png"))
    }

    @Test
    fun testClassifyOther() {
        assertEquals(DocumentType.OTHER, DocumentClassifier.classify("tax_receipt.pdf"))
        assertEquals(DocumentType.OTHER, DocumentClassifier.classify("random_doc.jpg"))
    }
}
