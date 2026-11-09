package com.meyouww.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.meyouww.core.data.device.DeviceRepository
import com.meyouww.core.data.device.DeviceStateCollector
import com.meyouww.core.data.device.PushTokenProvider
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.prefs.DeliveryTestRecord
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CheckState
import com.meyouww.core.designsystem.component.ChecklistRow
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.CheckStatus
import com.meyouww.core.model.DeliveryCheck
import com.meyouww.core.model.DeliveryCheckResult
import com.meyouww.core.model.DeliveryHealth
import com.meyouww.core.model.PushDiagnosis
import com.meyouww.core.model.PushProblem
import com.meyouww.core.model.PushStatus
import com.meyouww.core.model.RelativeTime
import com.meyouww.core.model.parseInstant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

sealed interface TestPhase {
    data object Idle : TestPhase
    data object Starting : TestPhase
    data class Waiting(val testId: String) : TestPhase
    data class Late(val testId: String) : TestPhase
    data class Failed(val error: AppError) : TestPhase
}

/** The server half of the notification check. */
data class ServerCheck(
    val loading: Boolean = true,
    val status: PushStatus? = null,
    val problem: PushProblem? = null,
    val error: AppError? = null,
    val reRegistering: Boolean = false,
)

data class DeliverySetupUiState(
    val checks: List<DeliveryCheckResult> = emptyList(),
    val manufacturer: String = Build.MANUFACTURER,
    val phase: TestPhase = TestPhase.Idle,
    val server: ServerCheck = ServerCheck(),
)

@HiltViewModel
class DeliverySetupViewModel @Inject constructor(
    private val collector: DeviceStateCollector,
    private val prefs: AppPreferences,
    private val devices: DeviceRepository,
    private val tokens: PushTokenProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(DeliverySetupUiState())
    val state: StateFlow<DeliverySetupUiState> = _state.asStateFlow()

    val lastTest: StateFlow<DeliveryTestRecord?> =
        prefs.lastDeliveryTest.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = viewModelScope.launch {
        val report = collector.collect()
        val checks = DeliveryHealth.evaluate(
            state = report,
            manufacturer = Build.MANUFACTURER,
            phoneMakerStepConfirmed = prefs.autostartConfirmed.first(),
            pushConfigured = BackendConfig.isFirebaseConfigured,
        )
        _state.update { it.copy(checks = checks) }
        devices.reportState() // keep the server's priority decisions current; failures are fine
        loadServer()
    }

    private suspend fun loadServer() {
        _state.update { it.copy(server = it.server.copy(loading = true)) }
        val phoneHasToken = tokens.currentToken() != null
        val server = when (val outcome = devices.pushStatus()) {
            is Outcome.Success -> ServerCheck(
                loading = false,
                status = outcome.value,
                problem = PushDiagnosis.diagnose(outcome.value, phoneHasToken),
            )
            is Outcome.Failure -> ServerCheck(loading = false, error = outcome.error)
        }
        _state.update { it.copy(server = server) }
    }

    /** Uploads this phone's token again, then re-reads the server's view. */
    fun registerAgain() = viewModelScope.launch {
        _state.update { it.copy(server = it.server.copy(reRegistering = true)) }
        devices.register(tokens.currentToken())
        loadServer()
    }

    fun setPhoneMakerConfirmed(confirmed: Boolean) = viewModelScope.launch {
        prefs.setAutostartConfirmed(confirmed)
        refresh()
    }

    fun startTest() = viewModelScope.launch {
        _state.update { it.copy(phase = TestPhase.Starting) }
        // Re-register first so the server has this phone's current token and settings.
        val registered = devices.register(tokens.currentToken())
        if (registered is Outcome.Failure) {
            _state.update { it.copy(phase = TestPhase.Failed(registered.error)) }
            return@launch
        }
        when (val outcome = devices.startDeliveryTest()) {
            is Outcome.Success -> {
                val id = outcome.value.testId
                _state.update { it.copy(phase = TestPhase.Waiting(id)) }
                delay(LATE_AFTER_MS)
                val record = prefs.lastDeliveryTest.first()
                if (record?.testId == id && record.arrivedAtMs == null) {
                    _state.update { it.copy(phase = TestPhase.Late(id)) }
                }
            }
            is Outcome.Failure -> _state.update { it.copy(phase = TestPhase.Failed(outcome.error)) }
        }
    }

    private companion object {
        /** 20 s scheduled delay plus generous room for Doze and slow networks. */
        const val LATE_AFTER_MS = 120_000L
    }
}

@Composable
fun DeliverySetupScreen(
    onBack: () -> Unit,
    viewModel: DeliverySetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastTest by viewModel.lastTest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) SystemSettings.appNotifications(context)
        viewModel.refresh()
    }
    fun fixNotifications() {
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (canAsk) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else SystemSettings.appNotifications(context)
    }

    CrayonScreen(title = stringResource(R.string.delivery_title), onBack = onBack) {
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.blue, seed = 11) {
            Text(stringResource(R.string.delivery_body), style = MaterialTheme.typography.bodyLarge, color = crayon.textSoft)
        }

        val okLabel = stringResource(com.meyouww.core.designsystem.R.string.status_ok)
        val fixLabel = stringResource(com.meyouww.core.designsystem.R.string.status_needs_fix)
        val confirmLabel = stringResource(com.meyouww.core.designsystem.R.string.status_unknown)

        state.checks.filter { it.status != CheckStatus.NotApplicable }.forEach { result ->
            val (checkState, label) = when (result.status) {
                CheckStatus.Ok -> CheckState.Ok to okLabel
                CheckStatus.NeedsFix -> CheckState.NeedsFix to fixLabel
                else -> CheckState.Unknown to confirmLabel
            }
            val ok = result.status == CheckStatus.Ok
            when (result.check) {
                DeliveryCheck.PushConfigured -> ChecklistRow(
                    checkState, stringResource(R.string.check_push_title),
                    stringResource(if (ok) R.string.check_push_ok else R.string.check_push_fix), label,
                )
                DeliveryCheck.Notifications -> ChecklistRow(
                    checkState, stringResource(R.string.check_notifications_title),
                    stringResource(if (ok) R.string.check_notifications_ok else R.string.check_notifications_fix), label,
                    actionLabel = if (ok) null else stringResource(R.string.check_notifications_action),
                    onAction = ::fixNotifications,
                )
                DeliveryCheck.Battery -> ChecklistRow(
                    checkState, stringResource(R.string.check_battery_title),
                    stringResource(if (ok) R.string.check_battery_ok else R.string.check_battery_fix), label,
                    actionLabel = if (ok) null else stringResource(R.string.check_battery_action),
                    onAction = { SystemSettings.appInfo(context) },
                )
                DeliveryCheck.BackgroundData -> ChecklistRow(
                    checkState, stringResource(R.string.check_data_title),
                    stringResource(if (ok) R.string.check_data_ok else R.string.check_data_fix), label,
                    actionLabel = if (ok) null else stringResource(R.string.check_data_action),
                    onAction = { SystemSettings.backgroundData(context) },
                )
                DeliveryCheck.PhoneMaker -> PhoneMakerRow(
                    manufacturer = state.manufacturer,
                    confirmed = ok,
                    checkState = checkState,
                    label = label,
                    onOpen = { SystemSettings.phoneMaker(context, state.manufacturer) },
                    onConfirm = viewModel::setPhoneMakerConfirmed,
                )
                DeliveryCheck.PlayServices -> ChecklistRow(
                    checkState, stringResource(R.string.check_play_title),
                    stringResource(if (ok) R.string.check_play_ok else R.string.check_play_fix), label,
                    actionLabel = if (ok) null else stringResource(R.string.check_play_action),
                    onAction = { SystemSettings.playServices(context) },
                )
                DeliveryCheck.ExactAlarms -> ChecklistRow(
                    checkState, stringResource(R.string.check_alarms_title),
                    stringResource(if (ok) R.string.check_alarms_ok else R.string.check_alarms_fix), label,
                    actionLabel = if (ok) null else stringResource(R.string.check_alarms_action),
                    onAction = { SystemSettings.exactAlarms(context) },
                )
            }
        }

        ServerCard(
            server = state.server,
            onRegisterAgain = viewModel::registerAgain,
            onOpenPlayServices = { SystemSettings.playServices(context) },
        )

        val notificationsOk = state.checks.any { it.check == DeliveryCheck.Notifications && it.status == CheckStatus.Ok }
        TestCard(
            phase = state.phase,
            lastTest = lastTest,
            notificationsOk = notificationsOk,
            onStart = viewModel::startTest,
        )

        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.peach, seed = 99) {
            Text(stringResource(R.string.delivery_limits_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.delivery_limits_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PhoneMakerRow(
    manufacturer: String,
    confirmed: Boolean,
    checkState: CheckState,
    label: String,
    onOpen: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    val brand = manufacturer.replaceFirstChar { it.uppercaseChar() }
    val detail = if (confirmed) {
        stringResource(R.string.check_maker_ok)
    } else {
        stringResource(R.string.check_maker_fix, brand) + "\n\n" + stringResource(SystemSettings.makerSteps(manufacturer))
    }
    ChecklistRow(
        state = checkState,
        title = stringResource(R.string.check_maker_title, brand),
        detail = detail,
        stateLabel = label,
        actionLabel = if (confirmed) null else stringResource(R.string.check_maker_action),
        onAction = onOpen,
    )
    CrayonButton(
        text = stringResource(if (confirmed) R.string.check_maker_undo else R.string.check_maker_confirm),
        onClick = { onConfirm(!confirmed) },
        kind = ButtonKind.Quiet,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestCard(
    phase: TestPhase,
    lastTest: DeliveryTestRecord?,
    notificationsOk: Boolean,
    onStart: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.blue, seed = 20) {
        Text(stringResource(R.string.delivery_test_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.delivery_test_body), style = MaterialTheme.typography.bodyMedium)

        val arrivedForCurrent = lastTest?.latencyMs != null &&
            (phase !is TestPhase.Waiting || phase.testId == lastTest.testId) &&
            (phase !is TestPhase.Late || phase.testId == lastTest.testId)
        val status: String? = when {
            !notificationsOk -> stringResource(R.string.delivery_test_needs_notifications)
            arrivedForCurrent -> stringResource(
                R.string.delivery_test_arrived,
                secondsText(lastTest?.latencyMs ?: 0L),
            )
            phase is TestPhase.Starting -> stringResource(R.string.delivery_test_starting)
            phase is TestPhase.Waiting -> stringResource(R.string.delivery_test_waiting)
            phase is TestPhase.Late -> stringResource(R.string.delivery_test_late)
            phase is TestPhase.Failed -> "⚠ " + stringResource(phase.error.messageRes())
            else -> null
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        CrayonButton(
            text = stringResource(R.string.delivery_test_button),
            onClick = onStart,
            enabled = notificationsOk && phase !is TestPhase.Starting,
            loading = phase is TestPhase.Starting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ServerCard(
    server: ServerCheck,
    onRegisterAgain: () -> Unit,
    onOpenPlayServices: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 41) {
        Text(
            stringResource(R.string.server_check_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        when {
            server.loading -> Text(stringResource(R.string.server_check_loading), style = MaterialTheme.typography.bodyMedium)
            server.error != null -> Text("⚠ " + stringResource(server.error.messageRes()), style = MaterialTheme.typography.bodyMedium)
            server.problem != null -> {
                val (title, body) = problemText(server.problem)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(body, style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
                when (server.problem) {
                    PushProblem.NotRegistered, PushProblem.TokenNotUploaded, PushProblem.TokenExpired -> CrayonButton(
                        text = stringResource(R.string.server_check_register_again),
                        onClick = onRegisterAgain,
                        loading = server.reRegistering,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PushProblem.NoTokenOnPhone -> CrayonButton(
                        text = stringResource(R.string.check_play_action),
                        onClick = onOpenPlayServices,
                        kind = ButtonKind.Quiet,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> Unit
                }
            }
        }
        val recent = server.status?.recent.orEmpty()
        if (recent.isNotEmpty()) {
            Text(stringResource(R.string.server_check_recent), style = MaterialTheme.typography.titleSmall)
            recent.take(6).forEach { attempt ->
                val result = when {
                    attempt.sent > 0 -> stringResource(R.string.server_check_sent)
                    attempt.error == "no_push_token" -> stringResource(R.string.server_check_failed_no_token)
                    attempt.error == "no_devices" -> stringResource(R.string.server_check_failed_no_device)
                    else -> stringResource(R.string.server_check_failed, attempt.error.orEmpty())
                }
                Text(
                    stringResource(R.string.server_check_line, pushTypeText(attempt.type), ageText(attempt.at), result),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun problemText(problem: PushProblem): Pair<String, String> = when (problem) {
    PushProblem.NotRegistered -> stringResource(R.string.server_problem_not_registered) to stringResource(R.string.server_problem_not_registered_body)
    PushProblem.NoTokenOnPhone -> stringResource(R.string.server_problem_no_token) to stringResource(R.string.server_problem_no_token_body)
    PushProblem.TokenNotUploaded -> stringResource(R.string.server_problem_not_uploaded) to stringResource(R.string.server_problem_not_uploaded_body)
    PushProblem.SenderNotSetUp -> stringResource(R.string.server_problem_sender) to stringResource(R.string.server_problem_sender_body)
    PushProblem.FirebaseRejected -> stringResource(R.string.server_problem_firebase) to stringResource(R.string.server_problem_firebase_body)
    PushProblem.WrongProject -> stringResource(R.string.server_problem_project) to stringResource(R.string.server_problem_project_body)
    PushProblem.TokenExpired -> stringResource(R.string.server_problem_expired) to stringResource(R.string.server_problem_expired_body)
    PushProblem.PhoneSide -> stringResource(R.string.server_problem_phone) to stringResource(R.string.server_problem_phone_body)
    PushProblem.NothingYet -> stringResource(R.string.server_problem_nothing) to stringResource(R.string.server_problem_nothing_body)
}

@Composable
private fun pushTypeText(type: String): String = when (type) {
    "activity" -> stringResource(R.string.server_type_activity)
    "wallpaper" -> stringResource(R.string.server_type_wallpaper)
    "message" -> stringResource(R.string.server_type_message)
    else -> type
}

@Composable
private fun ageText(at: String): String {
    val instant = parseInstant(at) ?: return ""
    return when (val t = RelativeTime.between(instant, Instant.now())) {
        RelativeTime.JustNow -> stringResource(R.string.server_age_now)
        is RelativeTime.Minutes -> stringResource(R.string.server_age_minutes, t.value)
        is RelativeTime.Hours -> stringResource(R.string.server_age_hours, t.value)
        is RelativeTime.Days -> stringResource(R.string.server_age_days, t.value)
        is RelativeTime.OnDate -> stringResource(R.string.server_age_days, 7)
    }
}

/** "2.1" for 2100 ms, in the phone's number format. */
internal fun secondsText(ms: Long): String = String.format(Locale.getDefault(), "%.1f", ms / 1000.0)
