package com.pmsuryaghar.docprocessor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class DocProcessorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Timber logger for debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
