package com.meyouww.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meyouww.core.chat.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Unread messages across chats, for the badge on the Chats tab. */
@HiltViewModel
class ChatBadgeViewModel @Inject constructor(chat: ChatRepository) : ViewModel() {
    val unread: StateFlow<Int> = chat.unreadTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
