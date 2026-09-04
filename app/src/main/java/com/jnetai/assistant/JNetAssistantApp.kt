package com.jnetai.assistant

import android.app.Application
import androidx.work.WorkManager
import com.jnetai.assistant.data.AppGraph
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class JNetAssistantApp : Application() {
    val graph: AppGraph by lazy { AppGraph.get(this) }

    override fun onCreate() {
        super.onCreate()
        // Persistent crash logger: every uncaught crash is appended to
        // filesDir/jnet_crash.log with the full stack trace so it can be
        // diagnosed even if the user can't describe the error.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val logFile = File(filesDir, "jnet_crash.log")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                logFile.appendText(
                    "\n===== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}" +
                        " on ${thread.name} =====\n" + sw.toString() + "\n"
                )
            }
            com.jnetai.assistant.util.Err.e("FATAL", "Uncaught crash on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}