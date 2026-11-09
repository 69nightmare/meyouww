package com.meyouww.feature.studio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewRecipientUiTest {
    @Test
    fun chatKeepsItsRecipientAndOffersNoOtherDestination() {
        val ui = previewRecipientUi(chatFriendId = "chat-friend", loadedFriendId = "chat-friend")

        assertThat(ui.canChooseDestination).isFalse()
        assertThat(ui.selectedFriendId).isEqualTo("chat-friend")
    }

    @Test
    fun missingChatRecipientDoesNotFallBackToDestinationPicker() {
        val ui = previewRecipientUi(chatFriendId = "chat-friend", loadedFriendId = null)

        assertThat(ui.canChooseDestination).isFalse()
        assertThat(ui.selectedFriendId).isNull()
    }

    @Test
    fun differentLoadedFriendCannotAppearAsChatRecipient() {
        val ui = previewRecipientUi(chatFriendId = "chat-friend", loadedFriendId = "other-friend")

        assertThat(ui.canChooseDestination).isFalse()
        assertThat(ui.selectedFriendId).isNull()
    }

    @Test
    fun standaloneCreateOffersDestinationsBeforeASelection() {
        val ui = previewRecipientUi(chatFriendId = null, loadedFriendId = null)

        assertThat(ui.canChooseDestination).isTrue()
        assertThat(ui.selectedFriendId).isNull()
    }

    @Test
    fun standaloneCreateCanSelectAndSwitchFriends() {
        val first = previewRecipientUi(chatFriendId = null, loadedFriendId = "first-friend")
        val second = previewRecipientUi(chatFriendId = null, loadedFriendId = "second-friend")

        assertThat(first.canChooseDestination).isTrue()
        assertThat(first.selectedFriendId).isEqualTo("first-friend")
        assertThat(second.canChooseDestination).isTrue()
        assertThat(second.selectedFriendId).isEqualTo("second-friend")
    }
}
