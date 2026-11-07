package com.meyouww.core.notifications.push

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meyouww.core.chat.ChatVisibility
import com.meyouww.core.model.ActivityItem
import com.meyouww.core.model.ActivityKind
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.WallpaperTarget
import com.meyouww.core.notifications.R
import com.meyouww.core.notifications.channels.MeyouwwChannels
import com.meyouww.core.notifications.chat.ChatNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts notifications from local data only. */
@Singleton
class AndroidPushNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notified: NotifiedStore,
    private val chat: ChatNotifications,
    private val chatVisibility: ChatVisibility,
) : PushNotifications {

    override fun showChatIncoming(conversationId: String) {
        if (!chatVisibility.isOpen(conversationId)) chat.showIncoming(conversationId)
    }

    override fun showDeliveryTestArrived(testId: String) {
        post(
            tag = TAG_DELIVERY_TEST,
            notification = deliveryTestBuilder(context.getString(R.string.delivery_test_arrived_text)).build(),
        )
    }

    /** Updates the self-test notification in place once the latency is known. */
    fun showDeliveryTestLatency(latencyMs: Long) {
        val seconds = String.format(Locale.getDefault(), "%.1f", latencyMs / 1000.0)
        post(
            tag = TAG_DELIVERY_TEST,
            notification = deliveryTestBuilder(context.getString(R.string.delivery_test_latency_text, seconds))
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    /** Posted straight from the push: no names, just what kind of thing arrived. */
    override fun showActivity(activityId: String, kind: String) {
        val k = ActivityKind.fromWire(kind)
        val (title, text) = genericActivityText(k)
        // The server may already have shown this same notification (same tag): don't alert twice.
        post(activityTag(activityId), activityBuilder(k, title, text).setOnlyAlertOnce(true).build())
        notified.add(activityId)
    }

    /** Replaces the generic notification once the inbox item has been fetched. */
    fun showActivityDetails(item: ActivityItem) {
        val k = item.kindValue
        val name = item.actor?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.activity_someone)
        val (title, text) = detailedActivityText(k, name, item)
        val builder = activityBuilder(k, title, text).setOnlyAlertOnce(true)
        val subject = item.subjectId
        if (item.actionable && subject != null &&
            (k == ActivityKind.FriendRequest || k == ActivityKind.RelationshipInvite || k == ActivityKind.WallpaperRequest ||
                k == ActivityKind.AlarmRequest || k == ActivityKind.EventInvite)
        ) {
            builder.addAction(0, context.getString(R.string.activity_action_accept), actionIntent(item.id, subject, k, accept = true))
            builder.addAction(0, context.getString(R.string.activity_action_decline), actionIntent(item.id, subject, k, accept = false))
        }
        // "Alarm added by Riya ⏰": nothing is silent, and Undo is one tap away.
        if (item.actionable && subject != null && (k == ActivityKind.AlarmAdded || k == ActivityKind.EventAdded)) {
            builder.addAction(0, context.getString(R.string.activity_action_undo), actionIntent(item.id, subject, k, accept = false))
        }
        post(activityTag(item.id), builder.build())
        notified.add(item.id)
    }

    fun cancelActivity(activityId: String) {
        NotificationManagerCompat.from(context).cancel(activityTag(activityId), NOTIFICATION_ID)
    }

    // ------------------------------------------------------------ wallpapers

    /** Auto mode, step 1: posted straight from the push. Every change is announced. */
    override fun showWallpaperIncoming(commandId: String) {
        post(
            wallpaperTag(commandId),
            wallpaperBuilder(s(R.string.wp_incoming_title), s(R.string.wp_incoming_text))
                .setProgress(0, 0, true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    /** Auto mode, step 2: updated in place with Undo, Pause and View. */
    fun showWallpaperApplied(commandId: String, senderId: String, senderName: String, target: WallpaperTarget) {
        val builder = wallpaperBuilder(s(R.string.wp_applied_title, senderName, targetName(target)), s(R.string.wp_applied_text))
            .setOnlyAlertOnce(true)
            .addAction(0, s(R.string.wp_action_undo), wallpaperAction(WallpaperActionReceiver.ACTION_UNDO, commandId, senderId, target))
            .addAction(0, s(R.string.wp_action_pause, senderName), wallpaperAction(WallpaperActionReceiver.ACTION_PAUSE, commandId, senderId, target))
            .addAction(0, s(R.string.wp_action_view), openApp(DESTINATION_INBOX))
        post(wallpaperTag(commandId), builder.build())
    }

    fun showWallpaperFailed(commandId: String, senderName: String?) {
        val name = senderName ?: s(R.string.activity_someone)
        post(wallpaperTag(commandId), wallpaperBuilder(s(R.string.wp_failed_title, name), s(R.string.wp_failed_text)).setOnlyAlertOnce(true).build())
    }

    fun cancelWallpaper(commandId: String) {
        NotificationManagerCompat.from(context).cancel(wallpaperTag(commandId), NOTIFICATION_ID)
    }

    /** Result of Undo or "Restore my wallpaper". */
    fun showUndoResult(restored: Boolean) {
        val (title, text) = if (restored) {
            s(R.string.wp_undo_done_title) to s(R.string.wp_undo_done_text)
        } else {
            s(R.string.wp_undo_none_title) to s(R.string.wp_undo_none_text)
        }
        post("wallpaper:undo", wallpaperBuilder(title, text).setOnlyAlertOnce(true).build())
    }

    fun showPaused(senderName: String) {
        post("wallpaper:pause", wallpaperBuilder(s(R.string.wp_paused_title, senderName), s(R.string.wp_paused_text)).build())
    }

    /** The sender's side: progress and the neutral result of a send. */
    fun showSending(key: String, recipientName: String) {
        post(
            "send:$key",
            NotificationCompat.Builder(context, MeyouwwChannels.DELIVERY_STATUS)
                .setSmallIcon(R.drawable.ic_stat_meyouww)
                .setContentTitle(s(R.string.wp_sending_title, recipientName))
                .setContentText(s(R.string.wp_sending_text))
                .setProgress(0, 0, true)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build(),
        )
    }

    fun showSendResult(key: String, recipientName: String, @StringRes title: Int, @StringRes text: Int) {
        post(
            "send:$key",
            NotificationCompat.Builder(context, MeyouwwChannels.DELIVERY_STATUS)
                .setSmallIcon(R.drawable.ic_stat_meyouww)
                .setContentTitle(s(title, recipientName))
                .setContentText(s(text, recipientName))
                .setAutoCancel(true)
                .setContentIntent(openApp(DESTINATION_INBOX))
                .build(),
        )
    }

    private fun wallpaperBuilder(title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, MeyouwwChannels.WALLPAPERS)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(DESTINATION_INBOX))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(MeyouwwChannels.WALLPAPERS))

    private fun wallpaperAction(action: String, commandId: String, senderId: String, target: WallpaperTarget): PendingIntent {
        val intent = Intent(context, WallpaperActionReceiver::class.java).apply {
            this.action = action
            putExtra(WallpaperActionReceiver.EXTRA_COMMAND_ID, commandId)
            putExtra(WallpaperActionReceiver.EXTRA_SENDER_ID, senderId)
            putExtra(WallpaperActionReceiver.EXTRA_TARGET, target.wire)
        }
        return PendingIntent.getBroadcast(
            context,
            (commandId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun targetName(target: WallpaperTarget): String = when (target) {
        WallpaperTarget.Home -> s(R.string.wp_target_home)
        WallpaperTarget.Lock -> s(R.string.wp_target_lock)
        WallpaperTarget.Both -> s(R.string.wp_target_both)
    }

    /** Shown when a button press could not reach the server even after retrying. */
    fun showActionFailed(activityId: String) {
        post(
            activityTag(activityId),
            activityBuilder(ActivityKind.Unknown, context.getString(R.string.activity_action_failed_title), context.getString(R.string.activity_action_failed_text))
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    private fun genericActivityText(kind: ActivityKind): Pair<String, String> = when (kind) {
        ActivityKind.FriendRequest -> s(R.string.activity_friend_request_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.FriendAccepted -> s(R.string.activity_friend_accepted_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.RelationshipInvite -> s(R.string.activity_relationship_invite_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.RelationshipAccepted -> s(R.string.activity_relationship_accepted_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.WallpaperRequest -> s(R.string.activity_wallpaper_request_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.WallpaperReceived -> s(R.string.activity_wallpaper_received_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.WallpaperApplied -> s(R.string.activity_wallpaper_applied_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.WallpaperDeclined -> s(R.string.activity_wallpaper_declined_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.DoodleReceived -> s(R.string.activity_doodle_received_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.EventInvite -> s(R.string.activity_event_invite_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.EventAdded -> s(R.string.activity_event_added_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.EventAccepted -> s(R.string.activity_event_accepted_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.EventDeclined -> s(R.string.activity_event_declined_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.EventCancelled -> s(R.string.activity_event_cancelled_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.AlarmRequest -> s(R.string.activity_alarm_request_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.AlarmAdded -> s(R.string.activity_alarm_added_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.AlarmAccepted -> s(R.string.activity_alarm_accepted_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.AlarmDeclined -> s(R.string.activity_alarm_declined_generic) to s(R.string.activity_tap_to_see)
        ActivityKind.Unknown -> s(R.string.activity_unknown_title) to s(R.string.activity_tap_to_see)
    }

    private fun detailedActivityText(kind: ActivityKind, name: String, item: ActivityItem): Pair<String, String> {
        val level = RelationshipLevel.fromWire(item.dataString("level"))
        val label = item.dataString("label") ?: context.getString(R.string.activity_partner_default_label)
        return when (kind) {
            ActivityKind.FriendRequest -> s(R.string.activity_friend_request_title, name) to
                s(R.string.activity_friend_request_text, item.actor?.handle.orEmpty())
            ActivityKind.FriendAccepted -> s(R.string.activity_friend_accepted_title, name) to s(R.string.activity_friend_accepted_text)
            ActivityKind.RelationshipInvite -> if (level == RelationshipLevel.Partner) {
                s(R.string.activity_partner_invite_title, name) to s(R.string.activity_partner_invite_text, label)
            } else {
                s(R.string.activity_bestie_invite_title, name) to s(R.string.activity_bestie_invite_text)
            }
            ActivityKind.RelationshipAccepted -> if (level == RelationshipLevel.Partner) {
                s(R.string.activity_partner_accepted_title, name) to s(R.string.activity_partner_accepted_text, label)
            } else {
                s(R.string.activity_bestie_accepted_title, name) to s(R.string.activity_bestie_accepted_text)
            }
            ActivityKind.WallpaperRequest -> s(R.string.activity_wallpaper_request_title, name) to s(R.string.activity_wallpaper_request_text)
            ActivityKind.WallpaperReceived -> s(R.string.activity_wallpaper_received_title, name) to s(R.string.activity_wallpaper_received_text)
            ActivityKind.WallpaperApplied -> s(R.string.activity_wallpaper_applied_title, name) to s(R.string.activity_wallpaper_applied_text)
            ActivityKind.WallpaperDeclined -> s(R.string.activity_wallpaper_declined_title, name) to s(R.string.activity_wallpaper_declined_text)
            ActivityKind.DoodleReceived -> s(R.string.activity_doodle_received_title, name) to s(R.string.activity_tap_to_see)
            ActivityKind.EventInvite -> s(R.string.activity_event_invite_title, name) to eventText(item)
            ActivityKind.EventAdded -> s(R.string.activity_event_added_title, name) to eventText(item)
            ActivityKind.EventAccepted -> s(R.string.activity_event_accepted_title, name) to eventText(item)
            ActivityKind.EventDeclined -> s(R.string.activity_event_declined_title, name) to eventText(item)
            ActivityKind.EventCancelled -> s(R.string.activity_event_cancelled_title, name) to eventText(item)
            ActivityKind.AlarmRequest -> s(R.string.activity_alarm_request_title, name) to alarmText(item)
            ActivityKind.AlarmAdded -> s(R.string.activity_alarm_added_title, name) to alarmText(item)
            ActivityKind.AlarmAccepted -> s(R.string.activity_alarm_accepted_title, name) to alarmText(item)
            ActivityKind.AlarmDeclined -> s(R.string.activity_alarm_declined_title, name) to alarmText(item)
            ActivityKind.Unknown -> genericActivityText(kind)
        }
    }

    /** "🍿 Movie night · 2026-12-05 19:30" from the inbox item's data. */
    private fun eventText(item: ActivityItem): String =
        listOfNotNull(
            listOfNotNull(item.dataString("emoji"), item.dataString("title")).joinToString(" ").ifEmpty { null },
            listOfNotNull(item.dataString("date"), item.dataString("time")).joinToString(" ").ifEmpty { null },
        ).joinToString(" · ").ifEmpty { s(R.string.activity_tap_to_see) }

    /** "Wake up! · 7:00" from the inbox item's data. */
    private fun alarmText(item: ActivityItem): String =
        listOfNotNull(item.dataString("title"), item.dataString("local_time")).joinToString(" · ")
            .ifEmpty { s(R.string.activity_tap_to_see) }

    private fun activityBuilder(kind: ActivityKind, title: String, text: String): NotificationCompat.Builder {
        val channel = channelFor(kind)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(openApp(DESTINATION_INBOX))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channel))
    }

    private fun actionIntent(activityId: String, subjectId: String, kind: ActivityKind, accept: Boolean): PendingIntent {
        val intent = Intent(context, ActivityActionReceiver::class.java).apply {
            action = if (accept) ActivityActionReceiver.ACTION_ACCEPT else ActivityActionReceiver.ACTION_DECLINE
            putExtra(ActivityActionReceiver.EXTRA_ACTIVITY_ID, activityId)
            putExtra(ActivityActionReceiver.EXTRA_SUBJECT_ID, subjectId)
            putExtra(ActivityActionReceiver.EXTRA_KIND, kind.wire)
        }
        return PendingIntent.getBroadcast(
            context,
            (activityId + accept).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Low-importance notification shown only when Android 11 or older runs expedited work as a service. */
    fun backgroundWorkNotification(): Notification =
        NotificationCompat.Builder(context, MeyouwwChannels.BACKGROUND_WORK)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setContentTitle(context.getString(R.string.background_work_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun deliveryTestBuilder(text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, MeyouwwChannels.DELIVERY_STATUS)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setContentTitle(context.getString(R.string.delivery_test_arrived_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(DESTINATION_DELIVERY_SETUP))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(MeyouwwChannels.DELIVERY_STATUS))

    private fun publicVersion(channel: String): Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setContentTitle(context.getString(R.string.notification_public_version))
            .build()

    private fun openApp(destination: String): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_DESTINATION, destination)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return null
        return PendingIntent.getActivity(
            context,
            destination.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun post(tag: String, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        // Tag + fixed id deduplicates: a repeated event updates the same notification.
        NotificationManagerCompat.from(context).notify(tag, NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_DESTINATION = "com.meyouww.extra.DESTINATION"
        const val DESTINATION_DELIVERY_SETUP = "delivery_setup"
        const val DESTINATION_INBOX = "inbox"

        fun channelFor(kind: ActivityKind): String = when (kind) {
            ActivityKind.WallpaperRequest -> MeyouwwChannels.WALLPAPER_REQUESTS
            ActivityKind.WallpaperReceived, ActivityKind.WallpaperApplied, ActivityKind.WallpaperDeclined,
            ActivityKind.DoodleReceived -> MeyouwwChannels.WALLPAPERS
            // The alarms channel (alarm sound) is only for an alarm that is ringing.
            ActivityKind.AlarmRequest, ActivityKind.AlarmAdded -> MeyouwwChannels.REMINDERS
            ActivityKind.EventInvite, ActivityKind.EventAdded, ActivityKind.EventCancelled -> MeyouwwChannels.REMINDERS
            else -> MeyouwwChannels.FRIENDS
        }

        private fun activityTag(activityId: String) = "activity:$activityId"

        private fun wallpaperTag(commandId: String) = "wallpaper:$commandId"
        private const val TAG_DELIVERY_TEST = "delivery_test"
        /**
         * Always 0: notifications Google Play services shows for the server (see
         * push-dispatch) use ID 0 and the same tags, so the app's version replaces them.
         */
        private const val NOTIFICATION_ID = 0
    }
}
