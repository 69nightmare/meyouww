package com.meyouww.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * The full-screen wake-up scene: shows over the lock screen and turns the screen on.
 * Snooze and Dismiss are big; "hold to dismiss" alarms need a 2-second press (TalkBack
 * users can double-tap the button instead).
 */
class AlarmActivity : ComponentActivity() {

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
            if (id == alarmId && !intent.getBooleanExtra(AlarmService.EXTRA_RINGING, true)) finish()
        }
    }
    private var alarmId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        val alarm = id?.let { AlarmScheduler(this).get(it) }
        alarmId = id
        ContextCompat.registerReceiver(this, stateReceiver, IntentFilter(AlarmService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            MeyouwwTheme {
                AlarmScene(
                    title = alarm?.title ?: getString(R.string.alarm_default_title),
                    message = alarm?.message,
                    fromName = alarm?.fromName,
                    holdToDismiss = alarm?.dismiss == "hold",
                    snoozeMinutes = alarm?.snoozeMinutes ?: 5,
                    onSnooze = { act(AlarmService.ACTION_SNOOZE) },
                    onDismiss = { act(AlarmService.ACTION_DISMISS) },
                )
            }
        }
    }

    private fun act(action: String) {
        val id = alarmId
        if (id != null) {
            // The service snoozes or dismisses (it also handles the fallback ring, which has no service yet).
            runCatching { startService(AlarmService.actionIntent(this, action, id)) }
            AlarmNotifier(this).cancel(id)
        }
        finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stateReceiver) }
        super.onDestroy()
    }
}

@Composable
private fun AlarmScene(
    title: String,
    message: String?,
    fromName: String?,
    holdToDismiss: Boolean,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(crayon.yellow)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)),
                fontSize = 64.sp,
                style = MaterialTheme.typography.displayLarge,
                color = crayon.onFill,
            )
            Sticker(WhitySticker.Sleepy, size = 180.dp)
            Text("⏰ $title", style = MaterialTheme.typography.headlineMedium, color = crayon.onFill, textAlign = TextAlign.Center)
            fromName?.let {
                Text(stringResource(R.string.alarm_from, it), style = MaterialTheme.typography.titleMedium, color = crayon.onFill)
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = crayon.onFill, textAlign = TextAlign.Center)
            }
            CrayonButton(
                stringResource(R.string.alarm_snooze_for, snoozeMinutes),
                onClick = onSnooze,
                kind = ButtonKind.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            if (holdToDismiss) {
                HoldButton(stringResource(R.string.alarm_hold_to_dismiss), onDone = onDismiss)
            } else {
                CrayonButton(stringResource(R.string.alarm_dismiss), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Press and hold for 2 seconds. Accessibility services get a plain click action. */
@Composable
private fun HoldButton(label: String, onDone: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = if (holding) tween(2_000, easing = LinearEasing) else tween(150),
        label = "hold",
    )
    LaunchedEffect(holding) {
        if (holding) {
            delay(2_000)
            if (holding) onDone()
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(crayon.blue, RoundedCornerShape(22.dp))
            .padding(18.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { onDone(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    tryAwaitRelease()
                    holding = false
                })
            },
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = crayon.onFill)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 6.dp))
    }
}
