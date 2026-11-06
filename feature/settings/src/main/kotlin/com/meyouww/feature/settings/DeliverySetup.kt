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
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.prefs.DeliveryTestRecord
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CheckState
import com.meyouww.core.designsystem.component.ChecklistRow
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.CheckStatus
import com.meyouww.core.model.DeliveryCheck
import com.meyouww.core.model.DeliveryCheckResult
import com.meyouww.core.model.DeliveryHealth
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
import java.util.Locale
import javax.inject.Inject

sealed interface TestPhase {
    data object Idle : TestPhase
    data object Starting : TestPhase
    data class Waiting(val testId: String) : TestPhase
    data class Late(val testId: String) : TestPhase
    data class Failed(val error: AppError) : TestPhase
}

data class DeliverySetupUiState(
    val checks: List<DeliveryCheckResult> = emptyList(),
    val manufacturer: String = Build.MANUFACTURER,
    val phase: TestPhase = TestPhase.Idle,
)

@HiltViewModel
class DeliverySetupViewModel @Inject constructor(
    private val collector: DeviceStateCollector,
    private val prefs: AppPreferences,
    private val devices: DeviceRepository,
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
    }

    fun setPhoneMakerConfirmed(confirmed: Boolean) = viewModelScope.launch {
        prefs.setAutostartConfirmed(confirmed)
        refresh()
    }

    fun startTest() = viewModelScope.launch {
        _state.update { it.copy(phase = TestPhase.Starting) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackRow(onBack)
        Text(
            stringResource(R.string.delivery_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.delivery_body), style = MaterialTheme.typography.bodyLarge, color = crayon.textSoft)

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

/** "2.1" for 2100 ms, in the phone's number format. */
internal fun secondsText(ms: Long): String = String.format(Locale.getDefault(), "%.1f", ms / 1000.0)
