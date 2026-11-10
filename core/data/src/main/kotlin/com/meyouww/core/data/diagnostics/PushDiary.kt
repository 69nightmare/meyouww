package com.meyouww.core.data.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A short diary of what happened to recent pushes on this phone: when one arrived, whether
 * the app could fetch what it announced, and why not. It never holds names, message text or
 * IDs, only kinds, counts, timings and error names, and it never leaves the phone by itself:
 * the person reads it on the Background delivery screen (and may copy it to share).
 */
@Singleton
class PushDiary @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("push_diary", Context.MODE_PRIVATE)
    private val stamp = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US)

    @Synchronized
    fun note(line: String) {
        val all = (lines() + (stamp.format(Date()) + "  " + line.take(MAX_LINE))).takeLast(MAX_LINES)
        prefs.edit().putString(KEY, all.joinToString("\n")).apply()
    }

    /** Oldest first. */
    fun lines(): List<String> = prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "lines"
        const val MAX_LINES = 80
        const val MAX_LINE = 200
    }
}
