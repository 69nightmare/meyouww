package com.meyouww.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DiscretePathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.meyouww.app.MainActivity
import com.meyouww.app.R
import com.meyouww.core.alarm.AlarmScheduler
import com.meyouww.core.alarm.EventReminders
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.data.auth.AuthRepository
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
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.math.min
import com.meyouww.core.designsystem.R as DesignR

/** What the widget shows, read from this phone only (no network beyond the sign-in check). */
internal data class WidgetData(
    val signedIn: Boolean,
    val unread: Int = 0,
    /** The newest chat with unread messages (its name, or the nickname I gave). */
    val firstName: String? = null,
    val otherChats: Int = 0,
    /** Where tapping the message line goes: that chat when only one has news. */
    val chatId: String? = null,
    /** "📅 Movie night · Fri 6:30 pm", the next alarm or reminder within a week. */
    val next: String? = null,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun chat(): ChatRepository
    fun auth(): AuthRepository
}

/**
 * Whity's cloud on the home screen: a crayon cloud-shaped speech bubble with Whity peeking
 * over the top, how many new messages there are and from whom, and the next alarm or
 * reminder. Whity's face changes: love eyes for news, a wave when all is read, a hoodie at night.
 */
class WhityWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, BIG))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { load(context) }
        provideContent { Content(data) }
    }

    private suspend fun load(context: Context): WidgetData {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val signedIn = withTimeoutOrNull(4_000) { entry.auth().ensureFreshSession() } ?: (entry.auth().currentUserId() != null)
        if (!signedIn) return WidgetData(signedIn = false)
        val withNews = runCatching { entry.chat().conversations().first() }.getOrDefault(emptyList())
            .filter { it.unread > 0 && !it.muted }
            .sortedByDescending { it.lastAt }
        return WidgetData(
            signedIn = true,
            unread = withNews.sumOf { it.unread },
            firstName = withNews.firstOrNull()?.peerName?.ifBlank { null },
            otherChats = (withNews.size - 1).coerceAtLeast(0),
            chatId = withNews.singleOrNull()?.id,
            next = nextUp(context),
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

    companion object {
        internal val SMALL = DpSize(110.dp, 110.dp)
        internal val WIDE = DpSize(250.dp, 110.dp)
        internal val BIG = DpSize(250.dp, 180.dp)
    }
}

private val INK = Color(0xFF231B1E)
private val INK_SOFT = Color(0xFF45383D)
private val RED = Color(0xFFD8392B)

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
    val big = size.height >= 170.dp
    val hour = LocalTime.now().hour
    val whity = when {
        !data.signedIn -> DesignR.drawable.whity_peek
        data.unread > 0 -> DesignR.drawable.whity_love
        hour >= 22 || hour < 6 -> DesignR.drawable.whity_hoodie
        else -> DesignR.drawable.whity_hi
    }
    val metrics = CloudMetrics(size.width.value, size.height.value)
    val background = CloudShape.draw(metrics, context.resources.displayMetrics.density)
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

    Box(
        modifier = GlanceModifier.fillMaxSize().background(ImageProvider(background)).clickable(actionStartActivity(open(context, tapTo))),
    ) {
        if (small) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(top = metrics.topDp.dp, bottom = metrics.bottomDp.dp, start = 8.dp, end = (metrics.shadowDp + 8).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(whity), contentDescription = null, contentScale = ContentScale.Fit, modifier = GlanceModifier.size(50.dp))
                Text(
                    if (!data.signedIn) "🔒" else if (data.unread > 0) "💌 " + data.unread else "✨",
                    style = TextStyle(color = ColorProvider(if (data.unread > 0) RED else INK), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(start = 18.dp, top = metrics.topDp.dp, end = (metrics.shadowDp + 12).dp, bottom = metrics.bottomDp.dp),
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            context.getString(R.string.widget_wordmark),
                            style = TextStyle(color = ColorProvider(RED), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                        Text(
                            headline,
                            style = TextStyle(color = ColorProvider(INK), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            maxLines = 2,
                        )
                    }
                    // Room for Whity, peeking over the cloud's corner.
                    Spacer(GlanceModifier.width(54.dp))
                }
                if (data.signedIn) {
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        data.next ?: context.getString(R.string.widget_nothing_next),
                        style = TextStyle(color = ColorProvider(INK_SOFT), fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
                if (big && data.signedIn) {
                    Spacer(GlanceModifier.defaultWeight())
                    Row {
                        Pill(context.getString(R.string.widget_chats), R.drawable.widget_pill_yellow, open(context, DESTINATION_PEOPLE))
                        Spacer(GlanceModifier.width(8.dp))
                        Pill(context.getString(R.string.widget_doodle), R.drawable.widget_pill_pink, open(context, DESTINATION_CREATE))
                    }
                }
            }
            Box(modifier = GlanceModifier.fillMaxSize().padding(end = (metrics.shadowDp + 4).dp), contentAlignment = Alignment.TopEnd) {
                Image(ImageProvider(whity), contentDescription = null, contentScale = ContentScale.Fit, modifier = GlanceModifier.size(64.dp))
            }
        }
    }
}

@Composable
private fun Pill(text: String, background: Int, intent: Intent) {
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(background))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = ColorProvider(INK), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/** Where the cloud's parts sit, in dp, for a widget of this size. */
internal class CloudMetrics(val widthDp: Float, val heightDp: Float) {
    val shadowDp = 4f
    val strokeDp = 3f
    val bumpDp = min(heightDp * 0.16f, 24f)
    val tailDp = min(heightDp * 0.12f, 14f)
    /** The body's top edge (the bumps rise above it). */
    val bodyTopDp = bumpDp * 1.3f + strokeDp
    val bodyBottomDp = heightDp - shadowDp - strokeDp - tailDp
    val topDp = bodyTopDp + 6f
    val bottomDp = heightDp - bodyBottomDp + 8f
}

/**
 * Draws the cloud: a paper-white speech bubble with three cloud bumps on top and a little
 * tail, a wobbly crayon-ink outline, a hard comic shadow and two tiny hearts. Drawn at the
 * widget's own size so the outline stays crisp however it is resized.
 */
internal object CloudShape {
    private const val PAPER = 0xFFFFFCF2.toInt()
    private const val INK_ARGB = 0xFF231B1E.toInt()
    private const val PINK = 0xFFF8A5BF.toInt()
    private const val SKY = 0xFFBFE6FF.toInt()

    fun draw(m: CloudMetrics, density: Float): Bitmap {
        val d = density.coerceAtMost(2.5f)
        fun px(dp: Float) = dp * d
        val w = px(m.widthDp).toInt().coerceAtLeast(2)
        val h = px(m.heightDp).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val stroke = px(m.strokeDp)
        val left = stroke
        val right = w - px(m.shadowDp) - stroke
        val top = px(m.bodyTopDp)
        val bottom = px(m.bodyBottomDp)
        val width = right - left
        val bump = px(m.bumpDp)

        val shape = Path().apply { addRoundRect(RectF(left, top, right, bottom), px(24f), px(24f), Path.Direction.CW) }
        // Cloud bumps along the top: small, big, middle.
        listOf(Triple(0.20f, 0.85f, 0.25f), Triple(0.42f, 1.25f, 0.05f), Triple(0.64f, 0.95f, 0.2f)).forEach { (x, scale, drop) ->
            shape.op(Path().apply { addCircle(left + width * x, top + bump * drop, bump * scale, Path.Direction.CW) }, Path.Op.UNION)
        }
        // The speech-bubble tail, bottom left.
        shape.op(
            Path().apply {
                moveTo(left + width * 0.14f, bottom - stroke)
                lineTo(left + width * 0.28f, bottom - stroke)
                lineTo(left + width * 0.10f, bottom + px(m.tailDp))
                close()
            },
            Path.Op.UNION,
        )

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        canvas.save()
        canvas.translate(px(m.shadowDp), px(m.shadowDp))
        canvas.drawPath(shape, fill.apply { color = INK_ARGB })
        canvas.restore()
        canvas.drawPath(shape, fill.apply { color = PAPER })

        // A strip of sky-blue "tape" behind the wordmark corner, like the app's labels.
        canvas.save()
        canvas.clipPath(shape)
        canvas.rotate(-4f, left + px(12f), top)
        canvas.drawRect(left + px(10f), top - px(2f), left + px(92f), top + px(14f), fill.apply { color = SKY; alpha = 150 })
        canvas.restore()

        heart(canvas, right - px(30f), bottom - px(18f), px(7f), fill, stroke * 0.6f)
        heart(canvas, right - px(16f), bottom - px(28f), px(4.5f), fill, stroke * 0.5f)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = INK_ARGB
            strokeWidth = stroke
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            // A hand-drawn wobble, like a crayon line.
            pathEffect = DiscretePathEffect(px(7f), px(0.7f))
        }
        canvas.drawPath(shape, outline)
        return bitmap
    }

    private fun heart(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint, stroke: Float) {
        val path = Path().apply {
            moveTo(cx, cy + r * 0.9f)
            cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.8f, cy - r * 1.4f, cx, cy - r * 0.5f)
            cubicTo(cx + r * 0.8f, cy - r * 1.4f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r * 0.9f)
            close()
        }
        canvas.drawPath(path, paint.apply { color = PINK; alpha = 255 })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = INK_ARGB; strokeWidth = stroke })
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
