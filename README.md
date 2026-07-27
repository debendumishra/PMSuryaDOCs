# PM Surya Ghar Document Processor - Android Application

A production-ready Android application built with **Kotlin** and **Jetpack Compose** designed to automate document organization, verification, PDF generation, report generation, and sharing for the PM Surya Ghar Scheme.

This app implements **MVVM + Clean Architecture**, **Repository Pattern**, **Hilt Dependency Injection**, and **Room Database**.

---

## Technical Stack & Libraries

- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** Clean Architecture + MVVM + Repository Pattern
- **Dependency Injection:** Hilt
- **Local DB:** Room Database (History persistence)
- **Local Preferences:** Preferences DataStore (Settings)
- **PDF Generation & Compression:** OpenPDF (`com.github.librepdf:openpdf`)
- **Word DOCX Report Generation:** Apache POI (`org.apache.poi:poi-ooxml`)
- **ZIP Creation:** Zip4j (`net.lingala.zip4j:zip4j`)
- **Logging:** Timber
- **Image Loading:** Coil
- **Testing:** JUnit4

---

## Core Features & Workflow

The application **does not monitor WhatsApp continuously**. Document processing starts only when the user presses **Start Processing**.

1. **Scan WhatsApp Folders:** Automatically scans configured WhatsApp directories for new images or PDFs since the last successful processing run.
2. **Auto-Classification:** Filenames are classified into specific types:
   - Aadhaar Card
   - PAN Card
   - Electricity Bill
   - Land Record
   - Bank Passbook
   - Rooftop Photograph (kept as compressed JPG)
   - House Photograph
   - Other Documents
3. **PDF Generation & Aggregation:** Combines multiple images belonging to the same category (e.g., Aadhaar Front and Aadhaar Back) into a single PDF (e.g., `Aadhaar.pdf`).
4. **Targeted PDF Compression:** Dynamically scales down images and applies JPEG compression to keep final PDFs under the target limit (default 2 MB) while maintaining OCR readability.
5. **Gemini App Intent Integration:** Rather than calling the paid cloud API, the app opens the local Gemini Android app (`com.google.android.apps.bard`) using an Android `ACTION_SEND_MULTIPLE` intent, passing all PDFs and the generated prompt.
6. **Data Extraction & Verification:** Gemini performs OCR, classifies documents, extracts customer metadata, and verifies data matching across documents.
7. **Intent Receiver & Copy-Paste Parser:** The user can paste the Gemini response, copy it to the clipboard, or simply **Share** the response from Gemini and select this application. The app parses the response and extracts the customer's name and mobile number.
8. **Folder Review Screen:** Shows a complete folder preview path inside Scoped Storage. The user can customize the name, mobile number, folder name, or change the target directory before continuing.
9. **Report & ZIP Generation:** Generates a professional styled `Verification_Report.docx` (and optional PDF report) using Apache POI. Compresses all PDFs, reports, and the raw Gemini response into a ZIP archive.
10. **WhatsApp Dispatch:** Launches WhatsApp to share the final ZIP archive to the target destination contact with prefilled messaging.
11. **Execution History:** Room DB logs every run, status, files created, dates, and saves folder details.

---

## Setup & Running Instructions

### Prerequisites
1. **Android Studio:** Hedgehog (2023.1.1) or newer.
2. **JDK:** Java 17.
3. **Android Device/Emulator:** Android 11 (API 30) to Android 16.
4. **Gemini Android App:** Installed on the device/emulator to run the AI verification.

### Import and Build
1. Open Android Studio.
2. Select **File -> Open** and choose the project directory (`PMSuryaDOCs`).
3. Let Gradle sync and download dependencies.
4. Clean and build: Select **Build -> Make Project** (or run `gradlew assembleDebug` in the terminal).

### Directory Configuration (First Run)
To run Scoped Storage correctly:
1. Open settings in the app.
2. Select **Source WhatsApp Folder** (e.g., your WhatsApp documents folder).
3. Select **Default Output Folder** (e.g., your local `Documents/` folder).
4. Enter the **Destination WhatsApp Number** (with country code, e.g. `919876543210`).
5. Click back, and you are ready to process documents!

### Run Unit Tests
To verify business logic:
- Run in terminal:
  ```bash
  gradlew test
  ```
- Or right-click the `test` directory in Android Studio and select **Run 'Tests in com.pmsuryaghar...'**.
