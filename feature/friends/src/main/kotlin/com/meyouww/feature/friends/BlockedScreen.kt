package com.meyouww.feature.friends

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
    CrayonScreen(title = stringResource(R.string.blocked_title), onBack = onBack) {
        state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (!state.loading && state.people.isEmpty()) {
            StickerMessage(WhitySticker.Cool, title = stringResource(R.string.blocked_empty), body = "")
        }
        state.people.forEach { person ->
            CrayonCard(modifier = Modifier.fillMaxWidth(), seed = person.id.hashCode()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(person.initial, person.id.hashCode())
                    Text("${person.name} ${person.handle}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    CrayonButton(stringResource(R.string.blocked_unblock), onClick = { viewModel.unblock(person) }, kind = ButtonKind.Quiet)
                }
            }
        }
    }
}
