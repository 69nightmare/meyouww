package com.meyouww.feature.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme

/** Everything meyouww asks Android for, in the order it asks. */
enum class PhonePermission { Notifications, Battery, ExactAlarms, FullScreen, DoNotDisturb }

object PhonePermissions {
    /** What this phone still needs. Empty when everything is already allowed. */
    fun missing(context: Context): List<PhonePermission> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            add(PhonePermission.Notifications)
        }
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(context.packageName)) add(PhonePermission.Battery)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == false
        ) {
            add(PhonePermission.ExactAlarms)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == false
        ) {
            add(PhonePermission.FullScreen)
        }
        // Lets a girlfriend / boyfriend switch this phone to mute.
        if (context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == false) {
            add(PhonePermission.DoNotDisturb)
        }
    }

    /** The system screen for a special permission (battery is a one-tap system dialog). */
    @SuppressLint("BatteryLife") // meyouww is a messaging, wallpaper and alarm app: late delivery is the bug.
    fun intentFor(context: Context, permission: PhonePermission): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (permission) {
            PhonePermission.Notifications -> null
            PhonePermission.Battery -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            PhonePermission.ExactAlarms ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else null
            PhonePermission.FullScreen ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else null
            // This settings page has no per-app shortcut on most phones: meyouww is in the list.
            PhonePermission.DoNotDisturb -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }
}

/**
 * First launch: one button asks for everything, one prompt after another, so nobody
 * has to find the settings pages. Anything refused can be fixed later in
 * Settings → Background delivery.
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    var asked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var running by rememberSaveable { mutableStateOf(false) }
    val missing = remember { PhonePermissions.missing(context) }

    // Nothing to ask (an older Android, or already allowed): straight on.
    LaunchedEffect(Unit) { if (missing.isEmpty()) onDone() }

    lateinit var next: () -> Unit
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }
    val special = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { next() }
    next = {
        val ask = PhonePermissions.missing(context).firstOrNull { it.name !in asked }
        if (ask == null) {
            running = false
            onDone()
        } else {
            asked = asked + ask.name
            if (ask == PhonePermission.Notifications) {
                runtime.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = PhonePermissions.intentFor(context, ask)
                try {
                    if (intent != null) special.launch(intent) else next()
                } catch (e: ActivityNotFoundException) {
                    next()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.widthIn(max = MAX_CONTENT_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Sticker(WhitySticker.Hi, decorative = true, size = 120.dp)
            Text(stringResource(R.string.perms_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.perms_body), style = MaterialTheme.typography.bodyLarge, color = crayon.textSoft, textAlign = TextAlign.Center)
            CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 12) {
                PermissionLine("🔔", R.string.perms_notifications)
                PermissionLine("🔋", R.string.perms_battery)
                PermissionLine("⏰", R.string.perms_alarms)
                PermissionLine("📺", R.string.perms_full_screen)
                PermissionLine("🔇", R.string.perms_dnd)
            }
            CrayonButton(
                text = stringResource(R.string.perms_allow_all),
                onClick = {
                    running = true
                    next()
                },
                loading = running,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onDone) { Text(stringResource(R.string.perms_later), color = crayon.link) }
            Text(stringResource(R.string.perms_later_note), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PermissionLine(emoji: String, text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
