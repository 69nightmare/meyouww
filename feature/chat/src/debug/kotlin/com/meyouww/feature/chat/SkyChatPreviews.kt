package com.meyouww.feature.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.meyouww.core.chat.ChatConversation
import com.meyouww.core.designsystem.component.kasukabeBackdrop
import com.meyouww.core.designsystem.theme.MeyouwwLook
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.SkyPhase
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel

/** Debug-only: People and the chat in the Living Sky look with sample data, to compare with their design boards. */
@Preview(name = "Living Sky · People · golden hour", widthDp = 390, heightDp = 844, apiLevel = 37)
@Composable
fun SkyPeopleGoldPreview() = SkySample(SkyPhase.Gold) {
    val now = System.currentTimeMillis()
    val friends = listOf(
        Friend("riya", "riya.sun", "Riya", level = RelationshipLevel.Partner.wire, label = "Girlfriend"),
        Friend("meera", "meera", "Meera", level = RelationshipLevel.BestFriend.wire),
        Friend("arjun", "arjun", "Arjun"),
        Friend("kabir", "kabir", "Kabir"),
    )
    val chats = listOf(
        ChatConversation("c1", "riya", "Riya", "Did you see the sky today?", "text", false, now - 60_000, unread = 2, muted = false, pinned = true),
        ChatConversation("c2", "meera", "Meera", null, "sticker", false, now - 100 * 60_000, unread = 1, muted = false),
        ChatConversation("c3", "goa", "Goa trip", "Kabir: Which beach first?", "text", false, now - 3 * 86_400_000L, unread = 1, muted = true, isGroup = true),
        ChatConversation("c4", "arjun", "Arjun", "see you at 6", "text", true, now - 4 * 86_400_000L, unread = 0, muted = false),
    )
    SkyPeopleScreen(chats, friends, "", {}, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Living Sky · chat · dusk", widthDp = 390, heightDp = 844, apiLevel = 37)
@Composable
fun SkyChatDuskPreview() = SkySample(SkyPhase.Dusk) { ChatDesignSample(RelationshipLevel.Partner, wallpaper = null) }

@Composable
private fun SkySample(phase: SkyPhase, content: @Composable () -> Unit) {
    MeyouwwTheme(look = MeyouwwLook.Sky, skyPhase = phase) {
        Surface(Modifier.fillMaxSize().kasukabeBackdrop(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { content() }
    }
}
