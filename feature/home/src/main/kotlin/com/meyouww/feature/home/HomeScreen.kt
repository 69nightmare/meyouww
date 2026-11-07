package com.meyouww.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.BackendConfig
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.device.DeviceStateCollector
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.MeyouwwFonts
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.DayPart
import com.meyouww.core.model.DeliveryHealth
import com.meyouww.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class HomeUiState(
    val profile: Profile? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val deliveryReady: Boolean? = null,
    val dayPart: DayPart = DayPart.ofHour(LocalTime.now().hour),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val stateCollector: DeviceStateCollector,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(profile = profiles.profile.value, loading = profiles.profile.value == null))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(dayPart = DayPart.ofHour(LocalTime.now().hour)) }
        val results = DeliveryHealth.evaluate(
            state = stateCollector.collect(),
            manufacturer = Build.MANUFACTURER,
            phoneMakerStepConfirmed = prefs.autostartConfirmed.first(),
            pushConfigured = BackendConfig.isFirebaseConfigured,
        )
        _state.update { it.copy(deliveryReady = DeliveryHealth.isReady(results)) }
        when (val outcome = profiles.refresh()) {
            is Outcome.Success -> _state.update { it.copy(profile = outcome.value ?: it.profile, loading = false, error = null) }
            // Keep showing the cached profile when offline.
            is Outcome.Failure -> _state.update { it.copy(loading = false, error = if (it.profile == null) outcome.error else null) }
        }
    }
}

/** Home for Phase 1: greeting, your ID and the background-delivery health check. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenDeliverySetup: () -> Unit,
    onAddFriend: () -> Unit,
    onDraw: () -> Unit,
    heart: @Composable () -> Unit,
    onAlarms: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val profile = state.profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TopRow(profile, onOpenSettings, heart)
        when {
            profile != null -> {
                GreetingCard(profile, state.dayPart)
                DeliveryChip(state.deliveryReady, onOpenDeliverySetup)
                CrayonButton(
                    text = stringResource(R.string.home_draw),
                    onClick = onDraw,
                    modifier = Modifier.fillMaxWidth(),
                )
                CrayonButton(
                    text = stringResource(R.string.home_add_friend),
                    onClick = onAddFriend,
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                CrayonButton(
                    text = stringResource(R.string.home_alarms),
                    onClick = onAlarms,
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                IdCard(profile)
            }
            state.loading -> WhityLoading(stringResource(R.string.home_loading), Modifier.fillMaxWidth().padding(top = 48.dp))
            else -> StickerMessage(
                sticker = WhitySticker.Confused,
                title = stringResource(R.string.home_profile_error_title),
                body = stringResource((state.error ?: AppError.Unknown).messageRes()),
                actionLabel = stringResource(R.string.home_retry),
                onAction = { viewModel.refresh() },
            )
        }
    }
}

@Composable
private fun TopRow(profile: Profile?, onOpenSettings: () -> Unit, heart: @Composable () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.home_wordmark),
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MeyouwwFonts.Fredoka, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        heart()
        val label = stringResource(R.string.home_open_profile)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .crayonSurface(fill = crayon.yellow, outline = crayon.outline, seed = 52, cornerRadius = 26.dp)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onOpenSettings)
                .semantics { this.contentDescription = label },
        ) {
            Text(
                (profile?.greetingName?.firstOrNull() ?: '♥').uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = crayon.onFill,
            )
        }
    }
}

@Composable
private fun GreetingCard(profile: Profile, dayPart: DayPart) {
    val crayon = MeyouwwTheme.crayon
    val (sticker, greeting, sub) = when (dayPart) {
        DayPart.MORNING -> Triple(WhitySticker.Hi, R.string.greeting_morning, R.string.greeting_morning_sub)
        DayPart.AFTERNOON -> Triple(WhitySticker.Flowers, R.string.greeting_afternoon, R.string.greeting_afternoon_sub)
        DayPart.EVENING -> Triple(WhitySticker.Tired, R.string.greeting_evening, R.string.greeting_evening_sub)
        DayPart.NIGHT -> Triple(WhitySticker.Sleepy, R.string.greeting_night, R.string.greeting_night_sub)
    }
    val date = remember { todayText() }
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 7) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(greeting, profile.greetingName), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(sub), style = MaterialTheme.typography.bodyLarge)
                Text(date, style = MaterialTheme.typography.labelMedium)
            }
            Sticker(sticker, size = 104.dp)
        }
    }
}

@Composable
private fun IdCard(profile: Profile) {
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    var copied by remember { mutableStateOf(false) }
    val username = profile.username ?: return
    CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 11) {
        Text(stringResource(R.string.home_your_id), style = MaterialTheme.typography.labelLarge, color = crayon.textSoft)
        Text("@$username", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.home_your_id_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        CrayonButton(
            text = stringResource(R.string.home_copy_id),
            onClick = {
                copyToClipboard(context, "@$username")
                copied = true
            },
            kind = ButtonKind.Secondary,
        )
        if (copied) {
            Text(
                stringResource(R.string.home_copied, username),
                color = crayon.success,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun DeliveryChip(ready: Boolean?, onOpenDeliverySetup: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    when (ready) {
        null -> Unit
        false -> CrayonChip(
            text = stringResource(R.string.home_delivery_fix),
            fill = crayon.bubblegum,
            onClick = onOpenDeliverySetup,
            modifier = Modifier.fillMaxWidth(),
        )
        true -> CrayonChip(
            text = stringResource(R.string.home_delivery_ready),
            fill = crayon.green,
            onClick = onOpenDeliverySetup,
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("meyouww ID", text))
}

/** Today's date in the phone's long date format, for example "Friday, 6 November 2026". */
private fun todayText(): String = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
