package com.meyouww.app

import androidx.lifecycle.ViewModel
import com.meyouww.core.chat.ChatVisibility
import com.meyouww.core.data.presence.PresenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** "Online" for friends: a heartbeat every so often while meyouww is on screen. */
@HiltViewModel
class PresenceViewModel @Inject constructor(
    private val presence: PresenceRepository,
    private val visibility: ChatVisibility,
) : ViewModel() {
    suspend fun beat() {
        presence.heartbeat(visibility.openConversationId)
    }

    companion object {
        const val INTERVAL_MS = 25_000L
    }
}
