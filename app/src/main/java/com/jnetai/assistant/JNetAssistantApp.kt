package com.jnetai.assistant

import android.app.Application
import androidx.work.WorkManager
import com.jnetai.assistant.data.AppGraph

class JNetAssistantApp : Application() {
    val graph: AppGraph by lazy { AppGraph.get(this) }

    override fun onCreate() {
        super.onCreate()
        // AppGraph initialisation is lazy; nothing runs eagerly to avoid
        // doing heavy work or touching the database on the main thread.
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            com.jnetai.assistant.util.Err.e("FATAL", "Uncaught crash", e)
            android.util.Log.getStackTraceString(e)
        }
    }
}