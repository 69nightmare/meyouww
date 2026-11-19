package com.meyouww.feature.friends

import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButtonTone
import com.meyouww.core.designsystem.component.SkyButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.model.PersonCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockedUiState(val loading: Boolean = true, val people: List<PersonCard> = emptyList(), val message: Int? = null)

@HiltViewModel
class BlockedViewModel @Inject constructor(private val repo: FriendsRepository) : ViewModel() {
    private val _state = MutableStateFlow(BlockedUiState())
    val state: StateFlow<BlockedUiState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        when (val outcome = repo.blocked()) {
            is Outcome.Success -> _state.update { it.copy(loading = false, people = outcome.value) }
            is Outcome.Failure -> _state.update { it.copy(loading = false, message = outcome.error.messageRes()) }
        }
    }

    fun unblock(person: PersonCard) = viewModelScope.launch {
        if (repo.unblock(person.id) is Outcome.Success) _state.update { it.copy(people = it.people - person) }
    }
}

@Composable
fun BlockedScreen(onBack: () -> Unit, viewModel: BlockedViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (MeyouwwTheme.crayon.living) {
        // Living Sky, as on the board: Whity on guard, each blocked person on a card with Unblock, and a note.
        CrayonScreen(title = stringResource(R.string.blocked_title), onBack = onBack) {
            state.message?.let { Text(stringResource(it), color = MeyouwwTheme.crayon.onError, style = SkyType.sm) }
            Sticker(WhitySticker.GrrOnGuard, decorative = true, size = 150.dp, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
            if (!state.loading && state.people.isEmpty()) {
                Text(stringResource(R.string.blocked_empty), style = SkyType.t.copy(shadow = skyHalo()), color = SkyInk.onSky)
            }
            state.people.forEach { person ->
                SkyCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(person.initial, person.id.hashCode(), size = 38.dp, photo = person.avatar)
                        Column(Modifier.weight(1f)) {
                            Text(person.name, style = SkyType.tt)
                            Text(person.handle, style = SkyType.sb, color = MeyouwwTheme.crayon.textSoft)
                        }
                        SkyButton(stringResource(R.string.blocked_unblock), { viewModel.unblock(person) }, small = true, tone = SkyButtonTone.Quiet)
                    }
                }
            }
            Text(stringResource(R.string.blocked_sky_note), style = SkyType.sm.copy(shadow = skyHalo()), color = SkyInk.onSkySoft)
        }
        return
    }
    CrayonScreen(title = stringResource(R.string.blocked_title), onBack = onBack) {
        state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (!state.loading && state.people.isEmpty()) {
            StickerMessage(WhitySticker.GrrTalkToThePaw, title = stringResource(R.string.blocked_empty), body = "")
        }
        state.people.forEach { person ->
            CrayonCard(modifier = Modifier.fillMaxWidth(), seed = person.id.hashCode()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(person.initial, person.id.hashCode(), photo = person.avatar)
                    Text("${person.name} ${person.handle}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    CrayonButton(stringResource(R.string.blocked_unblock), onClick = { viewModel.unblock(person) }, kind = ButtonKind.Quiet)
                }
            }
        }
    }
}
