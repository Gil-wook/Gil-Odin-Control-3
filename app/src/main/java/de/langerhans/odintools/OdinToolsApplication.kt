package de.langerhans.odintools

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

@HiltAndroidApp
class OdinToolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { writer ->
                    error.printStackTrace(PrintWriter(writer))
                }.toString()
                File(filesDir, "gil-last-crash.txt").writeText(
                    "time=${Instant.now()}\nthread=${thread.name}\n$trace",
                    Charsets.UTF_8,
                )
            } catch (writeError: Throwable) {
                Log.e("GilCrashRecorder", "Could not save crash report", writeError)
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
