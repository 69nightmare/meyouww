package com.meyouww.core.chat

import javax.inject.Inject
import javax.inject.Singleton

/** Which chat is on screen right now, so its messages don't also raise a notification. */
@Singleton
class ChatVisibility @Inject constructor() {
    @Volatile
    var openConversationId: String? = null

    /** The app itself is on screen (set by the activity). A chat is never "open" behind a closed app. */
    @Volatile
    var foreground: Boolean = false

    fun isOpen(conversationId: String): Boolean = foreground && openConversationId == conversationId
}
