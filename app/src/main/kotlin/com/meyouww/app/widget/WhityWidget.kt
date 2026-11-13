package com.meyouww.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.meyouww.app.MainActivity
import com.meyouww.app.R
import com.meyouww.core.alarm.AlarmScheduler
import com.meyouww.core.alarm.EventReminders
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.profile.AvatarRepository
import com.meyouww.core.notifications.chat.ChatNotifications
import com.meyouww.core.notifications.push.AndroidPushNotifications
import com.meyouww.core.notifications.push.WidgetRefresh
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.PI
import com.meyouww.core.designsystem.R as DesignR
import java.time.format.TextStyle as DayStyle

/** A pinned chat on the widget: its round picture (photo, or first letter) and where it opens. */
internal class PinnedFace(val id: String, val name: String, val picture: Bitmap)

/** What the widget shows, read from this phone (plus the sign-in check and missing photos). */
internal class WidgetData(
    val signedIn: Boolean,
    val unread: Int = 0,
    /** The newest chat with unread messages (its name, or the nickname I gave). */
    val firstName: String? = null,
    val otherChats: Int = 0,
    /** Where tapping the message line goes: that chat when only one has news. */
    val chatId: String? = null,
    /** "📅 Movie night · Fri 6:30 pm", the next alarm or reminder within a week. */
    val next: String? = null,
    /** Up to three pinned chats, tap to open. */
    val pinned: List<PinnedFace> = emptyList(),
)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun chat(): ChatRepository
    fun auth(): AuthRepository
    fun friends(): FriendsRepository
    fun avatars(): AvatarRepository
}

/**
 * Whity's cloud on the home screen: a crayon cloud-shaped speech bubble with the meyouww logo,
 * Whity peeking over the corner, new messages and from whom, the next alarm or reminder, the
 * pinned people's photos (tap one to open that chat) and round Chats and Doodle buttons.
 * Whity's face changes: love eyes for news, a wave when all is read, a hoodie at night.
 */
class WhityWidget : GlanceAppWidget() {
    // Drawn at the widget's real size, so the cloud's outline and the content always line up.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { load(context) }
        provideContent { Content(data) }
    }

    private suspend fun load(context: Context): WidgetData {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val auth = entry.auth()
        // Freshen the token for the friends' photos below when the network allows. Whether it
        // worked doesn't decide "signed in": offline, or with a connection Android froze, the
        // refresh fails for a while and the widget used to ask a signed-in person to sign in.
        // The widget shows this phone's own chats, so only a real sign-out turns it off.
        withTimeoutOrNull(4_000) { auth.ensureFreshSession() }
        val signedIn = withTimeoutOrNull(2_000) { auth.hasSessionOnThisPhone() } ?: (auth.currentUserId() != null)
        if (!signedIn) return WidgetData(signedIn = false)
        val all = runCatching { entry.chat().conversations().first() }.getOrDefault(emptyList())
        val withNews = all.filter { it.unread > 0 && !it.muted }.sortedByDescending { it.lastAt }
        val pins = all.filter { it.pinned }.take(MAX_FACES)
        val density = context.resources.displayMetrics.density
        val faces = withTimeoutOrNull(5_000) {
            val friends = if (pins.any { !it.isGroup }) {
                entry.friends().friends.value ?: (entry.friends().refreshFriends() as? Outcome.Success)?.value
            } else {
                null
            }
            pins.map { c ->
                val path = if (c.isGroup) null else friends?.firstOrNull { it.id == c.peerId }?.card?.avatar
                val photo = path?.let { runCatching { entry.avatars().bytes(it) }.getOrNull() }
                PinnedFace(c.id, c.peerName, Faces.draw(c.peerName, c.peerId, photo, c.unread > 0 && !c.muted, density, context))
            }
        } ?: pins.map { c -> PinnedFace(c.id, c.peerName, Faces.draw(c.peerName, c.peerId, null, c.unread > 0 && !c.muted, density, context)) }
        return WidgetData(
            signedIn = true,
            unread = withNews.sumOf { it.unread },
            firstName = withNews.firstOrNull()?.peerName?.ifBlank { null },
            otherChats = (withNews.size - 1).coerceAtLeast(0),
            chatId = withNews.singleOrNull()?.id,
            next = nextUp(context),
            pinned = faces,
        )
    }

    /** The soonest alarm or reminder in the next 7 days, from what is planned on this phone. */
    private fun nextUp(context: Context): String? {
        val now = System.currentTimeMillis()
        val limit = now + 7 * 24 * 3_600_000L
        val event = runCatching { EventReminders(context).all() }.getOrDefault(emptyList())
            .distinctBy { it.eventId }
            .filter { it.startMs in now..limit }
            .minByOrNull { it.startMs }
        val alarm = runCatching { AlarmScheduler(context).all() }.getOrDefault(emptyList())
            .filter { it.nextAtMs in now..limit }
            .minByOrNull { it.nextAtMs }
        return when {
            event != null && (alarm == null || event.startMs <= alarm.nextAtMs) ->
                event.emoji + " " + event.title + " · " + whenText(context, event.startMs, event.allDay)
            alarm != null -> "⏰ " + whenText(context, alarm.nextAtMs, false) + " · " + alarm.title
            else -> null
        }
    }

    private fun whenText(context: Context, at: Long, allDay: Boolean): String {
        val zone = ZoneId.systemDefault()
        val moment = Instant.ofEpochMilli(at).atZone(zone)
        val today = LocalDate.now(zone)
        val day = when (moment.toLocalDate()) {
            today -> context.getString(R.string.widget_today)
            today.plusDays(1) -> context.getString(R.string.widget_tomorrow)
            else -> moment.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault())
        }
        return if (allDay) day else day + " " + moment.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    private companion object {
        const val MAX_FACES = 3
    }
}

// The "Pastel Whity" palette (see core/designsystem theme/Palette.kt).
private val INK = Color(0xFF3A2A32)
private val INK_SOFT = Color(0xFF6B5560)
private val RED = Color(0xFFC7386B)

/** Opens meyouww at [destination] (a chat, a tab), or just opens it. */
private fun open(context: Context, destination: String?): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // A different address per button, so Android keeps their taps apart.
        .setData(Uri.parse("meyouww-widget://open/" + (destination ?: "home")))
        .apply { if (destination != null) putExtra(AndroidPushNotifications.EXTRA_DESTINATION, destination) }

@Composable
private fun Content(data: WidgetData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val small = size.width < 200.dp
    val hour = LocalTime.now().hour
    val whity = when {
        !data.signedIn -> DesignR.drawable.whity_peek
        data.unread > 0 -> DesignR.drawable.whity_love
        hour >= 22 || hour < 6 -> DesignR.drawable.whity_hoodie
        else -> DesignR.drawable.whity_hi
    }
    val m = CloudMetrics(size.width.value, size.height.value, logo = !small)
    val background = CloudShape.draw(m, context)
    val tapTo = when {
        !data.signedIn -> null
        data.chatId != null -> ChatNotifications.DESTINATION_PREFIX + data.chatId
        data.unread > 0 -> DESTINATION_PEOPLE
        else -> null
    }
    val headline = when {
        !data.signedIn -> context.getString(R.string.widget_signed_out)
        data.unread == 0 -> context.getString(R.string.widget_caught_up)
        data.otherChats > 0 -> context.resources.getQuantityString(R.plurals.widget_new_many, data.unread, data.unread, data.firstName.orEmpty(), data.otherChats)
        else -> context.resources.getQuantityString(R.plurals.widget_new_one, data.unread, data.unread, data.firstName ?: context.getString(R.string.widget_a_friend))
    }

    Box(modifier = GlanceModifier.fillMaxSize().background(ImageProvider(background)).clickable(actionStartActivity(open(context, tapTo)))) {
        if (small) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(top = m.bodyTopDp.dp, bottom = m.bottomDp.dp, start = 8.dp, end = (m.shadowDp + 8).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(whity), contentDescription = null, contentScale = ContentScale.Fit, modifier = GlanceModifier.size(48.dp))
                Text(
                    if (!data.signedIn) "🔒" else if (data.unread > 0) "💌 " + data.unread else "✨",
                    style = TextStyle(color = ColorProvider(if (data.unread > 0) RED else INK), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        } else {
            // The logo is drawn into the cloud itself (in the app's own font); the content sits under it.
            val roomy = size.height.value >= 200f
            val showRow = data.signedIn && size.height.value >= 150f
            // As many pinned faces as fit beside the two round buttons (three at most).
            val room = size.width.value - 18f - (m.shadowDp + 14f) - 2 * BUTTON_DP - 8f - 8f
            val faces = data.pinned.take(((room + FACE_GAP_DP) / (FACE_DP + FACE_GAP_DP)).toInt().coerceIn(0, 3))
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(start = 18.dp, top = m.contentTopDp.dp, end = (m.shadowDp + 14).dp, bottom = m.bottomDp.dp),
            ) {
                Text(
                    headline,
                    style = TextStyle(color = ColorProvider(INK), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = if (roomy) 2 else 1,
                )
                if (data.signedIn) {
                    Text(
                        data.next ?: context.getString(R.string.widget_nothing_next),
                        style = TextStyle(color = ColorProvider(INK_SOFT), fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
                if (showRow) {
                    Spacer(GlanceModifier.defaultWeight())
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        faces.forEachIndexed { i, face ->
                            if (i > 0) Spacer(GlanceModifier.width(FACE_GAP_DP.dp))
                            Face(face, withName = roomy)
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        RoundButton("💬", R.drawable.widget_circle_yellow, context.getString(R.string.widget_chats), open(context, DESTINATION_PEOPLE))
                        Spacer(GlanceModifier.width(8.dp))
                        RoundButton("✏️", R.drawable.widget_circle_pink, context.getString(R.string.widget_doodle), open(context, DESTINATION_CREATE))
                    }
                }
            }
            // Whity peeks over the cloud's top-right corner.
            Box(modifier = GlanceModifier.fillMaxSize().padding(end = (m.shadowDp + 2).dp), contentAlignment = Alignment.TopEnd) {
                Image(ImageProvider(whity), contentDescription = null, contentScale = ContentScale.Fit, modifier = GlanceModifier.size(m.whityDp.dp))
            }
        }
    }
}

/** A pinned person: their round photo (and first name when there is room); tap opens their chat. */
@Composable
private fun Face(face: PinnedFace, withName: Boolean) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier
            .clickable(actionStartActivity(open(context, ChatNotifications.DESTINATION_PREFIX + face.id)))
            .semantics { contentDescription = context.getString(R.string.widget_open_chat, face.name) },
    ) {
        Image(ImageProvider(face.picture), contentDescription = null, modifier = GlanceModifier.size(FACE_DP.dp))
        if (withName) {
            Text(
                face.name.substringBefore(' ').take(9),
                style = TextStyle(color = ColorProvider(INK), fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = GlanceModifier.width((FACE_DP + 8).dp),
            )
        }
    }
}

@Composable
private fun RoundButton(emoji: String, background: Int, label: String, intent: Intent) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .size(BUTTON_DP.dp)
            .background(ImageProvider(background))
            .clickable(actionStartActivity(intent))
            .semantics { contentDescription = label },
    ) {
        Text(emoji, style = TextStyle(fontSize = 17.sp, textAlign = TextAlign.Center))
    }
}

private const val FACE_DP = 40f
private const val FACE_GAP_DP = 10f
private const val BUTTON_DP = 40f

/** Where the cloud's parts sit, in dp, for a widget of this size. */
internal class CloudMetrics(val widthDp: Float, val heightDp: Float, val logo: Boolean) {
    val shadowDp = 4f
    val strokeDp = 2.6f
    /** Room at each side for the puffy sides to bulge into. */
    val sideDp = 4f
    /** The puffs along the top, the scallops along the bottom, and the little thought-bubble puffs. */
    val bumpDp = min(heightDp * 0.15f, 22f)
    val scallopDp = min(heightDp * 0.09f, 13f)
    val puffDp = min(heightDp * 0.08f, 12f)
    /** The body's top edge (the puffs rise above it). */
    val bodyTopDp = bumpDp * 1.3f + strokeDp
    /** The body's bottom edge (the scallops and the little puffs hang below it). */
    val bodyBottomDp = heightDp - shadowDp - strokeDp - max(scallopDp * 1.12f, puffDp * 1.37f)
    /** The logo's baseline, under the body's top edge. */
    val logoBaselineDp = bodyTopDp + 22f
    val contentTopDp = if (logo) logoBaselineDp + 8f else bodyTopDp + 6f
    val bottomDp = heightDp - bodyBottomDp + 6f
    val whityDp = min(64f, heightDp * 0.36f)
}

/**
 * Draws the cloud: a soft puffy cloud (four puffs on top, scallops underneath, round sides and two
 * little thought-bubble puffs) in sticker white with a soft rose rim and a gentle shadow, a
 * strawberry shine in the big puffs, two butter sparkles, and, in the app's own font, the meyouww
 * logo on a strip of pastel tape. Drawn at the widget's exact size so everything lines up.
 */
internal object CloudShape {
    private const val TOP_FILL = 0xFFFFFFFF.toInt()
    private const val BOTTOM_FILL = 0xFFFFFBF8.toInt()
    private const val SHINE = 0xFFFFD9E4.toInt()
    private const val INK_ARGB = 0xFF3A2A32.toInt()
    private const val RIM = 0xFFE6D2D6.toInt()
    private const val SHADOW = 0x2A3A2A32
    private const val RED_ARGB = 0xFFC7386B.toInt()
    private const val PINK = 0xFFF4AFC4.toInt()
    private const val SKY = 0xFFD3E8FF.toInt()
    private const val YELLOW = 0xFFF0CF6E.toInt()

    /** Puffs along the top: how far across (0..1), how big and how far down (both times [CloudMetrics.bumpDp]). Clear of Whity on the right. */
    private val TOPS = listOf(Triple(0.12f, 0.72f, 0.38f), Triple(0.30f, 1.0f, 0.16f), Triple(0.50f, 1.22f, 0.05f), Triple(0.67f, 0.88f, 0.26f))
    /** Scallops along the bottom: how far across, how big (times [CloudMetrics.scallopDp]). */
    private val BOTTOMS = listOf(0.24f to 0.9f, 0.40f to 1.1f, 0.57f to 1.0f, 0.73f to 1.12f, 0.88f to 0.95f)
    /** The thought-bubble puffs, bottom left: how far across, how far down and how big (times [CloudMetrics.puffDp]). */
    private val PUFFS = listOf(Triple(0.10f, 0.55f, 0.42f), Triple(0.035f, 1.12f, 0.25f))

    fun draw(m: CloudMetrics, context: Context): Bitmap {
        val d = context.resources.displayMetrics.density.coerceAtMost(2.5f)
        fun px(dp: Float) = dp * d
        val w = px(m.widthDp).toInt().coerceAtLeast(2)
        val h = px(m.heightDp).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val stroke = px(m.strokeDp)
        val left = stroke + px(m.sideDp)
        val right = w - px(m.shadowDp) - stroke - px(m.sideDp)
        val top = px(m.bodyTopDp)
        val bottom = px(m.bodyBottomDp)
        val width = right - left
        val bump = px(m.bumpDp)
        val scallop = px(m.scallopDp)
        val puff = px(m.puffDp)

        val body = Path().apply { addRoundRect(RectF(left, top, right, bottom), px(22f), px(22f), Path.Direction.CW) }
        fun puffOut(cx: Float, cy: Float, r: Float) {
            body.op(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }, Path.Op.UNION)
        }
        TOPS.forEach { (x, scale, drop) -> puffOut(left + width * x, top + bump * drop, bump * scale) }
        BOTTOMS.forEach { (x, scale) -> puffOut(left + width * x, bottom, scallop * scale) }
        // Round sides: one big gentle puff each, or a few on a tall widget.
        val span = (bottom - top) * 0.84f
        val count = ceil(span / px(140f)).toInt().coerceAtLeast(1)
        val half = span / count / 2
        val bulge = min(half * 0.08f, px(m.sideDp))
        repeat(count) { i ->
            val cy = top + (bottom - top) * 0.08f + half * (2 * i + 1)
            puffOut(left + half - bulge, cy, half)
            puffOut(right - half + bulge, cy, half)
        }
        val puffs = Path()
        PUFFS.forEach { (x, down, r) -> puffs.addCircle(left + width * x, bottom + puff * down, puff * r, Path.Direction.CW) }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = SHADOW
        }
        canvas.save()
        // A soft lift straight down, not a comic offset.
        canvas.translate(0f, px(m.shadowDp))
        canvas.drawPath(body, fill)
        canvas.drawPath(puffs, fill)
        canvas.restore()
        // Cream at the top, fading to a pale sky at the bottom.
        fill.shader = LinearGradient(0f, top, 0f, bottom + puff, TOP_FILL, BOTTOM_FILL, Shader.TileMode.CLAMP)
        canvas.drawPath(body, fill)
        canvas.drawPath(puffs, fill)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = RIM
            strokeWidth = stroke * 0.6f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(puffs, outline)
        canvas.drawPath(body, outline)

        // A soft sky-blue shine inside the two big puffs, so they look round.
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = SHINE
            strokeWidth = px(2.6f)
            strokeCap = Paint.Cap.ROUND
        }
        TOPS.subList(1, 3).forEach { (x, scale, drop) ->
            val cx = left + width * x
            val cy = top + bump * drop
            val r = bump * scale * 0.72f
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 205f, 50f, false, shine)
        }
        // Sparkles in the dips between the puffs.
        for ((a, b) in listOf(0 to 1, 2 to 3)) {
            sparkle(canvas, left + width * (TOPS[a].first + TOPS[b].first) / 2, top - bump * 0.8f, px(7f), px(0.8f))
        }

        if (m.logo) logo(canvas, context, left + px(12f), px(m.logoBaselineDp), px(16f), ::px)
        return bitmap
    }

    /** A butter four-point twinkle with a soft rose edge. */
    private fun sparkle(canvas: Canvas, cx: Float, cy: Float, size: Float, stroke: Float) {
        val path = Path()
        for (i in 0 until 8) {
            val angle = -PI / 2 + i * PI / 4
            val r = if (i % 2 == 0) size else size * 0.32f
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YELLOW })
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = RIM
                strokeWidth = stroke
                strokeJoin = Paint.Join.ROUND
            },
        )
    }

    /** "meyouww" in Mochiy Pop One ("you" in berry) on a tilted strip of pastel tape, and a heart. */
    private fun logo(canvas: Canvas, context: Context, x: Float, baseline: Float, size: Float, px: (Float) -> Float) {
        val face = runCatching { ResourcesCompat.getFont(context, DesignR.font.mochiy_pop_one) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
        }
        val parts = listOf("me" to INK_ARGB, "you" to RED_ARGB, "ww" to INK_ARGB)
        val total = parts.sumOf { text.measureText(it.first).toDouble() }.toFloat()
        val tape = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SKY }
        canvas.save()
        canvas.rotate(-3f, x, baseline)
        canvas.drawRect(x - px(6f), baseline - size * 0.62f, x + total + px(8f), baseline + size * 0.3f, tape)
        canvas.restore()
        var at = x
        for ((part, color) in parts) {
            canvas.drawText(part, at, baseline, text.apply { this.color = color })
            at += text.measureText(part)
        }
        heart(canvas, at + px(10f), baseline - size * 0.3f, px(5f), px(1.4f))
    }

    private fun heart(canvas: Canvas, cx: Float, cy: Float, r: Float, stroke: Float) {
        val path = Path().apply {
            moveTo(cx, cy + r * 0.9f)
            cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.8f, cy - r * 1.4f, cx, cy - r * 0.5f)
            cubicTo(cx + r * 0.8f, cy - r * 1.4f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r * 0.9f)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PINK })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = RIM; strokeWidth = stroke })
    }
}

/** Round pictures for pinned people: their photo, or their first letter on a pastel. */
internal object Faces {
    private const val INK_ARGB = 0xFF3A2A32.toInt()
    private const val RING = 0xFFFFFFFF.toInt()
    private val FILLS = intArrayOf(0xFFFFEDB0.toInt(), 0xFFD3E8FF.toInt(), 0xFFCFF0DC.toInt(), 0xFFFFDDCB.toInt(), 0xFFFFD9E4.toInt(), 0xFFE6DCFF.toInt(), 0xFFFFD8B0.toInt())

    fun draw(name: String, seed: String, photo: ByteArray?, news: Boolean, density: Float, context: Context): Bitmap {
        val d = density.coerceAtMost(2.5f)
        val side = (FACE_DP * d).toInt()
        val ring = 2.2f * d
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val center = side / 2f
        val radius = center - ring
        val picture = photo?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (picture != null) {
            val layer = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val c = Canvas(layer)
            c.drawCircle(center, center, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val s = minOf(picture.width, picture.height)
            val src = Rect((picture.width - s) / 2, (picture.height - s) / 2, (picture.width + s) / 2, (picture.height + s) / 2)
            c.drawBitmap(picture, src, RectF(center - radius, center - radius, center + radius, center + radius), paint)
            paint.xfermode = null
            canvas.drawBitmap(layer, 0f, 0f, null)
        } else {
            paint.color = FILLS[Math.floorMod(seed.hashCode(), FILLS.size)]
            canvas.drawCircle(center, center, radius, paint)
            val letter = name.trim().take(1).uppercase().ifEmpty { "?" }
            val face = runCatching { ResourcesCompat.getFont(context, DesignR.font.fredoka) }.getOrNull() ?: Typeface.DEFAULT_BOLD
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(face, Typeface.BOLD)
                textSize = side * 0.45f
                color = INK_ARGB
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(letter, center, center - (text.descent() + text.ascent()) / 2, text)
        }
        canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = RING; strokeWidth = ring })
        if (news) {
            // A berry dot: something new from them.
            val r = side * 0.14f
            val cx = side - r - ring / 2
            val cy = r + ring / 2
            canvas.drawCircle(cx, cy, r + ring * 0.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC7386B.toInt() })
        }
        return out
    }
}

/** Gets the widget redrawn when meyouww asks (new messages, the app was left) or Android does. */
class WhityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WhityWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != WidgetRefresh.ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching { glanceAppWidget.updateAll(context) }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Widget taps that open a tab (MainNav handles them). */
const val DESTINATION_PEOPLE = "tab:people"
const val DESTINATION_CREATE = "tab:create"
