package com.jnetai.assistant

import android.app.Application
import com.jnetai.assistant.data.AppGraph
import com.jnetai.assistant.util.Err

class JNetAssistantApp : Application() {
    val graph: AppGraph by lazy { AppGraph.get(this) }

    override fun onCreate() {
        super.onCreate()
        Err.initLog(this)
        Err.i("App starting — version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        // Persistent crash logger: every uncaught crash is appended to
        // filesDir/jnet_diagnostics.log with the full stack trace so it can be
        // diagnosed even if the user can't describe the error.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Err.e("FATAL", "Uncaught crash on ${thread.name}", throwable)
            runCatching {
                Err.w("App terminating after crash on ${thread.name}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}