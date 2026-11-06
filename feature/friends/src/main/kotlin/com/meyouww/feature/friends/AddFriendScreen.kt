package com.meyouww.feature.friends

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.InviteLinks
import com.meyouww.core.model.LookupRelation
import com.meyouww.core.model.LookupUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddFriendUiState(
    val query: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val found: LookupUser? = null,
    val sending: Boolean = false,
    val message: Int? = null,
    val success: Int? = null,
    val askNotifications: Boolean = false,
)

@HiltViewModel
class AddFriendViewModel @Inject constructor(
    private val repo: FriendsRepository,
    private val prefs: AppPreferences,
    profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AddFriendUiState())
    val state: StateFlow<AddFriendUiState> = _state.asStateFlow()

    val myUsername: String? = profiles.profile.value?.username

    fun onQueryChange(value: String) = _state.update {
        it.copy(query = value.take(24), searched = false, found = null, message = null, success = null)
    }

    /** Looks up an ID taken from a link, a scan or the text field. */
    fun search(raw: String = _state.value.query) {
        val id = InviteLinks.parse(raw)
        if (id == null) {
            _state.update { it.copy(message = R.string.add_invalid_id, searched = false, found = null) }
            return
        }
        _state.update { it.copy(query = "@$id", searching = true, message = null, success = null) }
        viewModelScope.launch {
            when (val outcome = repo.lookup(id)) {
                is Outcome.Success -> _state.update {
                    val reply = outcome.value
                    it.copy(
                        searching = false,
                        searched = true,
                        found = reply.user,
                        message = when {
                            reply.status == "rate_limited" -> R.string.result_rate_limited
                            reply.user == null -> R.string.add_not_found
                            else -> null
                        },
                    )
                }
                is Outcome.Failure -> _state.update { it.copy(searching = false, message = outcome.error.messageRes()) }
            }
        }
    }

    fun sendRequest() {
        val user = _state.value.found ?: return
        val username = user.username ?: return
        _state.update { it.copy(sending = true, message = null) }
        viewModelScope.launch {
            val outcome = repo.sendRequest(username)
            _state.update { s ->
                when (outcome) {
                    is Outcome.Failure -> s.copy(sending = false, message = outcome.error.messageRes())
                    is Outcome.Success -> when (outcome.value) {
                        ChangeResult.Pending -> s.copy(sending = false, success = R.string.add_requested, found = user.copy(relation = "requested"))
                        ChangeResult.Accepted -> s.copy(sending = false, success = R.string.add_accepted, found = user.copy(relation = "friend"))
                        ChangeResult.AlreadyFriends -> s.copy(sending = false, success = R.string.add_already_friends, found = user.copy(relation = "friend"))
                        ChangeResult.NotFound -> s.copy(sending = false, found = null, message = R.string.add_not_found)
                        else -> s.copy(sending = false, message = resultMessage(outcome.value))
                    }
                }
            }
            if (outcome is Outcome.Success && outcome.value in setOf(ChangeResult.Pending, ChangeResult.Accepted)) {
                repo.refreshRequests()
                repo.refreshFriends()
                if (!prefs.notificationExplainerShown.first()) _state.update { it.copy(askNotifications = true) }
            }
        }
    }

    fun scanFailed(noId: Boolean) = _state.update { it.copy(message = if (noId) R.string.add_scan_no_id else R.string.add_scan_failed) }

    fun explainerDone() = viewModelScope.launch {
        prefs.setNotificationExplainerShown()
        _state.update { it.copy(askNotifications = false) }
    }
}

@Composable
fun AddFriendScreen(
    prefill: String?,
    onBack: () -> Unit,
    viewModel: AddFriendViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    LaunchedEffect(prefill) { if (!prefill.isNullOrBlank()) viewModel.search(prefill) }

    CrayonScreen(title = stringResource(R.string.add_title), onBack = onBack) {
        CrayonTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = stringResource(R.string.add_id_label),
            placeholder = stringResource(R.string.add_id_placeholder),
            supportingText = stringResource(R.string.add_id_help),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            modifier = Modifier.fillMaxWidth(),
        )
        CrayonButton(
            text = stringResource(R.string.add_find),
            onClick = { viewModel.search() },
            loading = state.searching,
            enabled = state.query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        state.message?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }

        state.found?.let { user ->
            CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = user.id.hashCode()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(user.card.initial, user.id.hashCode(), size = 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(user.card.name, style = MaterialTheme.typography.titleLarge)
                        Text(user.card.handle, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                when (user.relationValue) {
                    LookupRelation.None -> CrayonButton(stringResource(R.string.add_send_request), onClick = viewModel::sendRequest, loading = state.sending, modifier = Modifier.fillMaxWidth())
                    LookupRelation.Incoming -> {
                        Text(stringResource(R.string.add_accept_theirs), style = MaterialTheme.typography.bodyLarge)
                        CrayonButton(stringResource(R.string.friends_accept), onClick = viewModel::sendRequest, loading = state.sending, modifier = Modifier.fillMaxWidth())
                    }
                    LookupRelation.Requested -> Text(stringResource(state.success ?: R.string.add_requested), style = MaterialTheme.typography.bodyLarge)
                    LookupRelation.Friend -> Text(stringResource(state.success ?: R.string.add_already_friends), style = MaterialTheme.typography.bodyLarge)
                    LookupRelation.Self -> Text(stringResource(R.string.add_self), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        val myId = viewModel.myUsername
        if (myId != null) {
            val link = InviteLinks.forUsername(myId)
            val dark = crayon.outline.toArgb()
            val light = MaterialTheme.colorScheme.surface.toArgb()
            val qr = remember(link, dark, light) { qrBitmap(link, 512, dark, light).asImageBitmap() }
            CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 77) {
                Text(stringResource(R.string.add_your_code), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.add_your_code_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
                Image(
                    bitmap = qr,
                    contentDescription = stringResource(R.string.add_qr_description, myId),
                    modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally),
                )
                Text("@$myId", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                val shareMessage = stringResource(R.string.add_share_text, myId, link)
                val chooser = stringResource(R.string.add_share_chooser)
                CrayonButton(stringResource(R.string.add_share), onClick = { shareText(context, shareMessage, chooser) }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
            }
        }
        CrayonButton(
            text = stringResource(R.string.add_scan),
            onClick = {
                scanQr(
                    context,
                    onResult = { raw ->
                        if (raw != null) {
                            val id = InviteLinks.parse(raw)
                            if (id != null) viewModel.search(id) else viewModel.scanFailed(noId = true)
                        }
                    },
                    onFailed = { viewModel.scanFailed(noId = false) },
                )
            },
            kind = ButtonKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (state.askNotifications) {
        if (notificationsAllowed(context)) {
            LaunchedEffect(Unit) { viewModel.explainerDone() }
        } else {
            NotificationExplainer(onDone = viewModel::explainerDone)
        }
    }
}
