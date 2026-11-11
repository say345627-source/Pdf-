package com.example

import android.app.Application
import com.example.data.engine.DocumentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WaraqApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val docManager = DocumentManager(this@WaraqApplication)
            docManager.seedSampleDocumentsIfEmpty()
        }
    }
}
