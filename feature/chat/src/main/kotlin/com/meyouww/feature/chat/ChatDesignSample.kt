package com.meyouww.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.meyouww.core.chat.ChatMessage
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.WallpaperTarget
import java.io.File

/**
 * The chat screen filled with made-up messages, for design checks: the debug build's
 * design preview shows it so CI can screenshot the chat without signing in.
 */
@Composable
fun ChatDesignSample(level: RelationshipLevel, wallpaper: File?) {
    val now = remember { System.currentTimeMillis() }
    val peer = remember(level) {
        Friend(
            id = "sample-riya",
            username = "riya",
            displayName = "Riya",
            level = level.wire,
            label = if (level == RelationshipLevel.Partner) "Girlfriend" else null,
        )
    }
    val timeline = remember(now, wallpaper) {
        fun msg(id: String, mine: Boolean, text: String?, minutesAgo: Long, status: String, sticker: String? = null, replyTo: String? = null, reactions: Map<String, List<String>> = emptyMap()) =
            TimelineItem.Message(
                ChatMessage(
                    id = id,
                    fromMe = mine,
                    kind = if (sticker != null) "sticker" else "text",
                    text = text,
                    sticker = sticker,
                    replyTo = replyTo,
                    status = status,
                    sentAt = now - minutesAgo * 60_000,
                    edited = false,
                    deleted = false,
                    reactions = reactions,
                ),
            )
        listOf(
            msg("1", false, "Good morning sunshine ☀️", 26 * 60, "seen"),
            msg("2", true, "Morning!! Did you sleep well?", 26 * 60 - 2, ChatRepository.STATUS_READ),
            msg("3", false, "Like a baby. I had the weirdest dream about cats running a bakery", 50, "seen"),
            msg("4", false, "They made amazing croissants though", 49, "seen", reactions = mapOf("😂" to listOf("me"))),
            msg("5", true, "Haha that is the best dream ever", 48, ChatRepository.STATUS_READ, replyTo = "4"),
            TimelineItem.Wallpaper("w1", fromMe = true, status = "applied", at = now - 40 * 60_000, file = wallpaper, target = WallpaperTarget.Lock),
            msg("6", false, null, 30, "seen", sticker = "whity:Love"),
            msg("7", true, "See you at 6? 🍜", 3, ChatRepository.STATUS_DELIVERED),
            msg("8", true, "Bring the umbrella, it might rain", 2, ChatRepository.STATUS_DELIVERED),
        )
    }
    val mood = chatMood(level)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ChatHeader(
            name = "Riya",
            peer = peer,
            peerId = peer.id,
            mood = mood,
            muted = false,
            onBack = {},
            onPhoto = {},
            onSheet = {},
            onSafety = {},
            onRinger = {},
            onToggleMute = {},
            onOpenProfile = {},
        )
        ChatTimeline(
            timeline = timeline,
            name = "Riya",
            mood = mood,
            onOpenSafety = {},
            onReact = { _, _ -> },
            onReply = {},
            onEdit = {},
            onDelete = {},
            onRetry = {},
            onSave = {},
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Composer(
            peerName = "Riya",
            mood = mood,
            replyTo = null,
            editing = null,
            onCancelContext = {},
            onSend = {},
            onSticker = {},
            onDraw = {},
            onPhoto = {},
            onAlarm = {},
        )
    }
}
