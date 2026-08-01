package com.pmsuryaghar.docprocessor.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.pmsuryaghar.docprocessor.domain.model.AppSettings
import com.pmsuryaghar.docprocessor.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val SOURCE_FOLDER_URI = stringPreferencesKey("source_folder_uri")
        val DESTINATION_WHATSAPP_NUMBER = stringPreferencesKey("destination_whatsapp_number")
        val DEFAULT_GEMINI_PROMPT = stringPreferencesKey("default_gemini_prompt")
        val DEFAULT_OUTPUT_FOLDER_URI = stringPreferencesKey("default_output_folder_uri")
        val LANGUAGE = stringPreferencesKey("language")
        val MAX_PDF_SIZE_MB = floatPreferencesKey("max_pdf_size_mb")
        val ZIP_FILENAME_FORMAT = stringPreferencesKey("zip_filename_format")
        val GENERATE_PDF_REPORT = booleanPreferencesKey("generate_pdf_report")
        val SELECTED_AI_AGENT = stringPreferencesKey("selected_ai_agent")
        val LAST_PROCESSING_TIMESTAMP = longPreferencesKey("last_processing_timestamp")
        val AADHAAR_PORTAL_URL = stringPreferencesKey("aadhaar_portal_url")
        val ELECTRICITY_PORTAL_URL = stringPreferencesKey("electricity_portal_url")
        val LAND_RECORD_PORTAL_URL = stringPreferencesKey("land_record_portal_url")
        val WHATSAPP_MEDIA_FOLDER_URI = stringPreferencesKey("whatsapp_media_folder_uri")
        val TPCODL_WHATSAPP_NUMBER = stringPreferencesKey("tpcodl_whatsapp_number")
        val IS_APP_UNLOCKED = booleanPreferencesKey("is_app_unlocked")
    }

    override fun getSettings(): Flow<AppSettings> {
        return context.dataStore.data.map { preferences ->
            AppSettings(
                sourceFolderUri = preferences[PreferencesKeys.SOURCE_FOLDER_URI] ?: "",
                destinationWhatsAppNumber = preferences[PreferencesKeys.DESTINATION_WHATSAPP_NUMBER] ?: "",
                defaultGeminiPrompt = preferences[PreferencesKeys.DEFAULT_GEMINI_PROMPT] ?: AppSettings.DEFAULT_PROMPT,
                defaultOutputFolderUri = preferences[PreferencesKeys.DEFAULT_OUTPUT_FOLDER_URI] ?: "",
                language = preferences[PreferencesKeys.LANGUAGE] ?: "English",
                maxPdfSizeMb = preferences[PreferencesKeys.MAX_PDF_SIZE_MB] ?: 2.0f,
                zipFilenameFormat = preferences[PreferencesKeys.ZIP_FILENAME_FORMAT] ?: "{CustomerName}_{MobileNumber}",
                generatePdfReport = preferences[PreferencesKeys.GENERATE_PDF_REPORT] ?: false,
                selectedAiAgent = preferences[PreferencesKeys.SELECTED_AI_AGENT] ?: "Gemini",
                lastProcessingTimestamp = preferences[PreferencesKeys.LAST_PROCESSING_TIMESTAMP] ?: 0L,
                aadhaarPortalUrl = preferences[PreferencesKeys.AADHAAR_PORTAL_URL] ?: "https://tathya.uidai.gov.in/access/login?role=resident",
                electricityPortalUrl = preferences[PreferencesKeys.ELECTRICITY_PORTAL_URL] ?: "https://mytatapowerplus.tatapower.com/#/offerings",
                landRecordPortalUrl = preferences[PreferencesKeys.LAND_RECORD_PORTAL_URL] ?: "https://bhulekh.ori.nic.in/",
                whatsappMediaFolderUri = preferences[PreferencesKeys.WHATSAPP_MEDIA_FOLDER_URI] ?: "",
                tpcodlWhatsappNumber = preferences[PreferencesKeys.TPCODL_WHATSAPP_NUMBER] ?: "",
                isAppUnlocked = preferences[PreferencesKeys.IS_APP_UNLOCKED] ?: false
            )
        }
    }

    override suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOURCE_FOLDER_URI] = settings.sourceFolderUri
            preferences[PreferencesKeys.DESTINATION_WHATSAPP_NUMBER] = settings.destinationWhatsAppNumber
            preferences[PreferencesKeys.DEFAULT_GEMINI_PROMPT] = settings.defaultGeminiPrompt
            preferences[PreferencesKeys.DEFAULT_OUTPUT_FOLDER_URI] = settings.defaultOutputFolderUri
            preferences[PreferencesKeys.LANGUAGE] = settings.language
            preferences[PreferencesKeys.MAX_PDF_SIZE_MB] = settings.maxPdfSizeMb
            preferences[PreferencesKeys.ZIP_FILENAME_FORMAT] = settings.zipFilenameFormat
            preferences[PreferencesKeys.GENERATE_PDF_REPORT] = settings.generatePdfReport
            preferences[PreferencesKeys.SELECTED_AI_AGENT] = settings.selectedAiAgent
            preferences[PreferencesKeys.LAST_PROCESSING_TIMESTAMP] = settings.lastProcessingTimestamp
            preferences[PreferencesKeys.AADHAAR_PORTAL_URL] = settings.aadhaarPortalUrl
            preferences[PreferencesKeys.ELECTRICITY_PORTAL_URL] = settings.electricityPortalUrl
            preferences[PreferencesKeys.LAND_RECORD_PORTAL_URL] = settings.landRecordPortalUrl
            preferences[PreferencesKeys.WHATSAPP_MEDIA_FOLDER_URI] = settings.whatsappMediaFolderUri
            preferences[PreferencesKeys.TPCODL_WHATSAPP_NUMBER] = settings.tpcodlWhatsappNumber
            preferences[PreferencesKeys.IS_APP_UNLOCKED] = settings.isAppUnlocked
        }
    }

    override suspend fun updateLastProcessingTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_PROCESSING_TIMESTAMP] = timestamp
        }
    }
}
