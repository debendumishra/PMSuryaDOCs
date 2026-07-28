package com.pmsuryaghar.docprocessor.domain.model

data class AppSettings(
    val sourceFolderUri: String = "",
    val destinationWhatsAppNumber: String = "",
    val defaultGeminiPrompt: String = DEFAULT_PROMPT,
    val defaultOutputFolderUri: String = "",
    val language: String = "English", // "English" or "Odia"
    val maxPdfSizeMb: Float = 2.0f,
    val zipFilenameFormat: String = "{CustomerName}_{MobileNumber}",
    val generatePdfReport: Boolean = false,
    val selectedAiAgent: String = "Gemini", // "Gemini" or "ChatGPT"
    val lastProcessingTimestamp: Long = 0L,
    val aadhaarPortalUrl: String = "https://tathya.uidai.gov.in/access/login?role=resident",
    val electricityPortalUrl: String = "https://mytatapowerplus.tatapower.com/#/offerings",
    val landRecordPortalUrl: String = "https://bhulekh.ori.nic.in/",
    val whatsappDocsFolderUri: String = "",
    val whatsappImagesFolderUri: String = ""
) {
    companion object {
        const val DEFAULT_PROMPT = """**PM SURYA GHAR DOCUMENT VERIFICATION PROMPT**

You are an expert document verification assistant for the PM Surya Ghar Scheme.
Analyze every attached PDF carefully.
Perform all OCR yourself.
Do not assume anything.
Read every document completely.

Your tasks are:
1. Identify every attached document.
Possible document types include:
* Aadhaar Card
* PAN Card
* Electricity Bill
* Bank Passbook
* Land Record
* House Photograph
* Rooftop Photograph
* Property Tax Receipt
* Income Certificate
* Any other PM Surya Ghar document

2. Extract information from every document.
Extract wherever available:
Customer Name
Mobile Number
Father/Husband Name
Address
PIN Code
Village
Post Office
Police Station
District
State
Aadhaar Number
PAN Number
Electricity Consumer Number
Consumer ID
Sanction Load
Account Number
IFSC Code
Khata Number
Plot Number
Area
Owner Name
Bank Name
Branch
Application Number
Connection Number

3. Determine whether all documents belong to the same applicant.
4. Report any mismatch including different names, different addresses, duplicate/missing pages, or unreadable scans.
5. Prepare a PM Surya Ghar Document Verification Report.
6. At the very beginning of your response, provide the following fields in this exact format:
Customer Name: 
Mobile Number: 
Document Mapping:
Page 1: <English Document Type>
Page 2: <English Document Type>

Use the English document type names for the mapping (Aadhaar Card, PAN Card, Electricity Bill, Bank Passbook, Land Record, House Photograph, Rooftop Photograph, Other Document).

These values will be used by the Android application to automatically split the PDF and create the customer folder.

7. After these two fields, generate a complete verification report.

End of Prompt."""
    }
}
