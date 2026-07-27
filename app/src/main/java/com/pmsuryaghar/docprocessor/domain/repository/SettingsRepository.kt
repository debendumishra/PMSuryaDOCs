package com.pmsuryaghar.docprocessor.domain.repository

import com.pmsuryaghar.docprocessor.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
    suspend fun updateLastProcessingTimestamp(timestamp: Long)
}
