package com.meyouww.core.data.prefs

import android.content.Context

/**
 * The names I gave people in chats ("nicknames"), by user ID, so notifications can say them
 * too: messages, wallpapers, alarms, invites. A copy of what the chat database holds, kept on
 * this phone only. Before the phone's first unlock it can't be read, and the person's own
 * name is shown instead.
 */
object NicknameBook {
    private const val FILE = "nicknames"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** My name for [userId], or null to use theirs. */
    fun get(context: Context, userId: String?): String? =
        userId?.let { id -> runCatching { prefs(context).getString(id, null) }.getOrNull() }?.ifBlank { null }

    /** [name], unless I gave [userId] a nickname. */
    fun nameFor(context: Context, userId: String?, name: String): String = get(context, userId) ?: name

    fun set(context: Context, userId: String, nickname: String?) {
        runCatching {
            prefs(context).edit().apply { if (nickname.isNullOrBlank()) remove(userId) else putString(userId, nickname) }.apply()
        }
    }

    /** Replaces the whole book (after a restore, or to catch up with the chat database). */
    fun replaceAll(context: Context, nicknames: Map<String, String>) {
        runCatching {
            prefs(context).edit().clear().apply { nicknames.forEach { (id, name) -> putString(id, name) } }.apply()
        }
    }

    fun clear(context: Context) {
        runCatching { prefs(context).edit().clear().apply() }
    }
}
