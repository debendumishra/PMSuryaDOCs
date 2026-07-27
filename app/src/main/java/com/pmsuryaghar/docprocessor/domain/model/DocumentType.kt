package com.pmsuryaghar.docprocessor.domain.model

enum class DocumentType(val displayName: String, val systemName: String) {
    AADHAAR("Aadhaar Card", "Aadhaar"),
    PAN("PAN Card", "PAN"),
    ELECTRICITY_BILL("Electricity Bill", "ElectricityBill"),
    LAND_RECORD("Land Record", "LandRecord"),
    PASSBOOK("Bank Passbook", "Passbook"),
    ROOFTOP_PHOTO("Rooftop Photograph", "RooftopPhoto"),
    HOUSE_PHOTO("House Photograph", "HousePhoto"),
    PASSPORT_PHOTO("Passport Photo", "PassportPhoto"),
    OTHER("Other Document", "Other");
}
