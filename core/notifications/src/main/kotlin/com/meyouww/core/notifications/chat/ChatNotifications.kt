package com.meyouww.core.notifications.chat

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.meyouww.core.chat.ChatConversation
import com.meyouww.core.chat.ChatMessage
import com.meyouww.core.chat.MediaKind
import com.meyouww.core.notifications.R
import com.meyouww.core.notifications.channels.MeyouwwChannels
import com.meyouww.core.notifications.push.AndroidPushNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message notifications: one per chat, tagged "chat:<id>" like the server's generic
 * one so they replace each other. Shown in full on the lock screen as well.
 */
@Singleton
class ChatNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Straight from the push, before anything is fetched or decrypted. */
    fun showIncoming(conversationId: String) {
        post(
            conversationId,
            base(conversationId)
                .setContentTitle(context.getString(R.string.chat_push_title))
                .setContentText(context.getString(R.string.chat_push_text))
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    /** The real thing, after decryption: who and what, with Reply and Mark as read. */
    /** [senderNames]: for a group, each member's name by user ID (shown with their messages). */
    fun showConversation(conversation: ChatConversation, unseen: List<ChatMessage>, senderNames: Map<String, String> = emptyMap()) {
        if (unseen.isEmpty()) {
            cancel(conversation.id)
            return
        }
        val me = Person.Builder().setName(context.getString(R.string.chat_you)).build()
        val name = conversation.peerName.ifBlank {
            context.getString(if (conversation.isGroup) R.string.chat_group else R.string.chat_friend)
        }
        val them = Person.Builder().setName(name).setKey(conversation.peerId).build()
        // Their name on top and the newest few messages stacked under it, like other chat apps.
        // In a group: the group's name on top and who said what under it.
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(name).setGroupConversation(conversation.isGroup)
        val people = mutableMapOf<String, Person>()
        fun sender(message: ChatMessage): Person = if (!conversation.isGroup) them else people.getOrPut(message.senderId) {
            Person.Builder()
                .setName(senderNames[message.senderId]?.ifBlank { null } ?: context.getString(R.string.chat_friend))
                .setKey(message.senderId)
                .build()
        }
        unseen.takeLast(MAX_STACKED).forEach { message ->
            val text = when {
                message.deleted -> context.getString(R.string.chat_deleted)
                message.kind == "sticker" -> context.getString(R.string.chat_sticker_preview)
                message.kind == "poll" -> context.getString(R.string.chat_poll_preview, message.text.orEmpty())
                message.kind == "reminder" -> context.getString(R.string.chat_reminder_preview, message.text.orEmpty())
                message.attachment != null -> context.getString(
                    when (message.attachment?.kind) {
                        MediaKind.Video -> R.string.chat_video_preview
                        MediaKind.Pdf -> R.string.chat_pdf_preview
                        MediaKind.Doc -> R.string.chat_doc_preview
                        else -> R.string.chat_photo_preview
                    },
                )
                else -> message.text.orEmpty()
            }
            style.addMessage(NotificationCompat.MessagingStyle.Message(text, message.sentAt, sender(message)))
        }
        val reply = NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.chat_action_reply),
            actionIntent(ChatActionReceiver.ACTION_REPLY, conversation.id, mutable = true),
        )
            .addRemoteInput(RemoteInput.Builder(ChatActionReceiver.KEY_TEXT).setLabel(context.getString(R.string.chat_reply_hint)).build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val read = NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.chat_action_read),
            actionIntent(ChatActionReceiver.ACTION_READ, conversation.id, mutable = false),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        post(
            conversation.id,
            base(conversation.id)
                .setStyle(style)
                .addAction(reply)
                .addAction(read)
                .setNumber(unseen.size)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    /** After my reply from the notification: keep it, but stop the spinner. */
    fun showReplied(conversationId: String) = cancel(conversationId)

    fun cancel(conversationId: String) {
        NotificationManagerCompat.from(context).cancel(tag(conversationId), 0)
    }

    /**
     * Chats whose notification in the shade is only the generic "New message" (the app's
     * placeholder, or the copy Android shows straight from the server), posted before
     * [postedBefore]. Real notifications list their messages and are never included.
     */
    fun genericChatNotifications(postedBefore: Long): List<String> = runCatching {
        context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
            .filter { sbn ->
                sbn.tag?.startsWith(TAG_PREFIX) == true && sbn.postTime < postedBefore &&
                    !sbn.notification.extras.containsKey(NotificationCompat.EXTRA_MESSAGES)
            }
            .mapNotNull { it.tag?.removePrefix(TAG_PREFIX) }
    }.getOrDefault(emptyList())

    private fun base(conversationId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, MeyouwwChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openChat(conversationId))
            // The owner's choice: who and what show in full on the lock screen too. (Android's own
            // "hide sensitive content" setting can still hide it per phone.)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun openChat(conversationId: String): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(AndroidPushNotifications.EXTRA_DESTINATION, DESTINATION_PREFIX + conversationId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return null
        return PendingIntent.getActivity(
            context,
            ("open" + conversationId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, conversationId: String, mutable: Boolean): PendingIntent {
        val intent = Intent(context, ChatActionReceiver::class.java).apply {
            this.action = action
            putExtra(ChatActionReceiver.EXTRA_CONVERSATION_ID, conversationId)
        }
        // A reply needs a mutable PendingIntent so Android can add the typed text.
        val mutability = when {
            !mutable -> PendingIntent.FLAG_IMMUTABLE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> PendingIntent.FLAG_MUTABLE
            else -> 0
        }
        return PendingIntent.getBroadcast(context, (action + conversationId).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
    }

    private fun post(conversationId: String, notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        NotificationManagerCompat.from(context).notify(tag(conversationId), 0, notification)
    }

    companion object {
        /** Destination extra for "open this chat": "chat:<conversation id>". */
        const val DESTINATION_PREFIX = "chat:"

        /** How many unread messages a chat notification shows at once (newest last). */
        const val MAX_STACKED = 4

        private const val TAG_PREFIX = "chat:"

        private fun tag(conversationId: String) = TAG_PREFIX + conversationId
    }
}
