package com.pmsuryaghar.docprocessor.di

import com.pmsuryaghar.docprocessor.data.repository.ProcessingRepositoryImpl
import com.pmsuryaghar.docprocessor.data.repository.SettingsRepositoryImpl
import com.pmsuryaghar.docprocessor.domain.repository.ProcessingRepository
import com.pmsuryaghar.docprocessor.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindProcessingRepository(
        impl: ProcessingRepositoryImpl
    ): ProcessingRepository
}
