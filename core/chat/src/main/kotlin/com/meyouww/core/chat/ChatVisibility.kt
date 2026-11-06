package com.meyouww.core.chat

import javax.inject.Inject
import javax.inject.Singleton

/** Which chat is on screen right now, so its messages don't also raise a notification. */
@Singleton
class ChatVisibility @Inject constructor() {
    @Volatile
    var openConversationId: String? = null

    fun isOpen(conversationId: String): Boolean = openConversationId == conversationId
}
