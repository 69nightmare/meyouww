package com.meyouww.feature.onboarding

import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButtonTone
import com.meyouww.core.designsystem.component.SkyButton
import com.meyouww.core.designsystem.component.LocalOnCard
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import com.meyouww.core.designsystem.component.CardLevel
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
import com.meyouww.core.designsystem.component.CrayonScreen
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

    if (crayon.living) {
        SkyPermissionsList(
            asked = asked,
            running = running,
            onAllowAll = {
                running = true
                next()
            },
            onLater = onDone,
        )
        return
    }
    CrayonScreen(
        title = stringResource(R.string.perms_title),
        // Living Sky: Whity on the sky, what to allow on a sheet below her.
        sheet = true,
        sheetStageHeight = 110.dp,
        sheetStage = { Sticker(WhitySticker.ToonHiIAmHere, decorative = true, size = 128.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp)) },
    ) {
        Column(
            Modifier.widthIn(max = MAX_CONTENT_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!crayon.living) Sticker(WhitySticker.Hi, decorative = true, size = 96.dp)
            Text(stringResource(R.string.perms_body), style = MaterialTheme.typography.bodyLarge, color = crayon.textSoft, textAlign = TextAlign.Center)
            // Living Sky: these still need an OK, so they float up.
            CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 12, level = CardLevel.Raised) {
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

/**
 * Living Sky, as on the board: the title and why on the sky with Whity, each thing to allow as a
 * card (raised with "Needs OK", or mint with "Done ✓"), and a sheet with Allow all and Later.
 */
@Composable
private fun SkyPermissionsList(asked: List<String>, running: Boolean, onAllowAll: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    val missing = remember(asked, running) { PhonePermissions.missing(context) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val full = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MAX_CONTENT_WIDTH)
                .verticalScroll(rememberScrollState())
                .heightIn(min = full),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
                    Text(stringResource(R.string.perms_title), style = SkyType.h2.copy(shadow = skyHalo()), color = SkyInk.onSky, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.perms_sky_body), style = SkyType.sm.copy(shadow = skyHalo()), color = SkyInk.onSkySoft)
                }
                Box(Modifier.fillMaxWidth().height(84.dp)) {
                    Sticker(WhitySticker.ToonHiIAmHere, decorative = true, size = 96.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 26.dp).offset(y = 8.dp))
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        Triple(PhonePermission.Notifications, R.string.perms_sky_notif, R.string.perms_sky_notif_sub) to SkyIcons.Bell,
                        Triple(PhonePermission.Battery, R.string.perms_sky_battery, R.string.perms_sky_battery_sub) to SkyIcons.Cloud,
                        Triple(PhonePermission.ExactAlarms, R.string.perms_sky_alarms, R.string.perms_sky_alarms_sub) to SkyIcons.Alarm,
                        Triple(PhonePermission.FullScreen, R.string.perms_sky_full, R.string.perms_sky_full_sub) to SkyIcons.Phone,
                        Triple(PhonePermission.DoNotDisturb, R.string.perms_sky_dnd, R.string.perms_sky_dnd_sub) to SkyIcons.Mute,
                    ).forEach { (what, icon) ->
                        val (permission, title, sub) = what
                        val done = permission !in missing
                        SkyCard(
                            modifier = Modifier.fillMaxWidth(),
                            tone = if (done) SkyTone.Mint else null,
                            level = if (done) CardLevel.Flat else CardLevel.Raised,
                            padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            corner = 24.dp,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val face = (if (done) SkyTone.Mint else SkyTone.Butter).fill()
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(38.dp).skySurface(face, crayon.pressShadowFor(face), crayon.shadow, crayon.sunX * 0.5f, cornerRadius = 19.dp, depth = 3.dp, lift = 6.dp, blur = 10.dp),
                                ) { Icon(if (done) SkyIcons.Check else icon, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(20.dp)) }
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(title), style = SkyType.tt, color = if (done) SkyInk.onPastel else MaterialTheme.colorScheme.onSurface)
                                    Text(stringResource(sub), style = if (done) SkyType.sm else SkyType.sb, color = if (done) SkyInk.onPastel else crayon.textSoft)
                                }
                                SkyPill(stringResource(if (done) R.string.perms_sky_done else R.string.perms_sky_needs), tone = if (done) null else SkyTone.Butter)
                            }
                        }
                    }
                }
            }
            val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
            Column(
                Modifier
                    .padding(top = 18.dp)
                    .fillMaxWidth()
                    .shadow(18.dp, shape, ambientColor = crayon.shadow, spotColor = crayon.shadow)
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CompositionLocalProvider(LocalOnCard provides true, LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    SkyButton(stringResource(R.string.perms_allow_all), onAllowAll, block = true, loading = running)
                    SkyButton(stringResource(R.string.perms_later), onLater, tone = SkyButtonTone.Text)
                    Text(stringResource(R.string.perms_later_note), style = SkyType.hp, color = crayon.textSoft, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
