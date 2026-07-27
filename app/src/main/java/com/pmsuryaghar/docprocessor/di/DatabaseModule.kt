package com.pmsuryaghar.docprocessor.di

import android.content.Context
import androidx.room.Room
import com.pmsuryaghar.docprocessor.data.local.AppDatabase
import com.pmsuryaghar.docprocessor.data.local.dao.ProcessingHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "doc_processor.db"
        ).fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideProcessingHistoryDao(database: AppDatabase): ProcessingHistoryDao {
        return database.processingHistoryDao()
    }
}
