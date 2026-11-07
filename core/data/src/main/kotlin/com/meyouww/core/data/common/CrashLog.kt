package com.meyouww.core.data.common

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Keeps the last crash's stack trace on this phone (never uploaded) so it can be
 * copied from Settings and shared when reporting a bug. Only the trace is kept:
 * exception class names, messages and code locations.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 12_000

    fun install(context: Context) {
        val app = context.applicationContext
        val appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(app.filesDir, FILE).writeText(
                    "meyouww $appVersion · ${Instant.now()} · thread ${thread.name}\n${trace.take(MAX_CHARS)}",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
