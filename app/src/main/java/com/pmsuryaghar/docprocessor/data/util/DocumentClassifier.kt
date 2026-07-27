package com.pmsuryaghar.docprocessor.data.util

import com.pmsuryaghar.docprocessor.domain.model.DocumentType
import java.util.Locale

object DocumentClassifier {

    fun classify(fileName: String): DocumentType {
        val lower = fileName.lowercase(Locale.ROOT)
        
        return when {
            lower.contains("aadhaar") || lower.contains("adhar") || lower.contains("uid") || lower.contains("uidai") -> DocumentType.AADHAAR
            lower.contains("pancard") || (lower.contains("pan") && !lower.contains("company") && !lower.contains("panther") && !lower.contains("passbook")) -> DocumentType.PAN
            lower.contains("electricity") || lower.contains("bill") || lower.contains("eb") || lower.contains("cesu") || 
                    lower.contains("tpsodl") || lower.contains("tpcodl") || lower.contains("tpnodl") || lower.contains("tpwodl") || lower.contains("bijli") || lower.contains("electric") -> DocumentType.ELECTRICITY_BILL
            lower.contains("land") || lower.contains("patta") || lower.contains("ror") || lower.contains("record") || lower.contains("khata") || lower.contains("plot") || lower.contains("bhulekh") || lower.contains("jamabandi") -> DocumentType.LAND_RECORD
            lower.contains("passbook") || lower.contains("pass book") || lower.contains("statement") || lower.contains("cheque") || lower.contains("chk") || (lower.contains("bank") && !lower.contains("bill")) -> DocumentType.PASSBOOK
            lower.contains("rooftop") || lower.contains("roof") || lower.contains("solar") || lower.contains("rooftopphoto") -> DocumentType.ROOFTOP_PHOTO
            lower.contains("house") || lower.contains("building") || lower.contains("home") || lower.contains("front") || lower.contains("premises") -> DocumentType.HOUSE_PHOTO
            lower.contains("passport") || lower.contains("photo") || lower.contains("photograph") || lower.contains("pic") || lower.contains("image") -> DocumentType.PASSPORT_PHOTO
            else -> DocumentType.OTHER
        }
    }
}
