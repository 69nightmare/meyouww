package com.meyouww.feature.inbox

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.activity.ActivityRepository
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.ActivityItem
import com.meyouww.core.model.ActivityKind
import com.meyouww.core.model.WallpaperMode
import com.meyouww.core.model.WallpaperTarget
import com.meyouww.core.notifications.push.WallpaperWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Wallpapers friends sent that are waiting for an answer, for a banner on Home. */
@HiltViewModel
class WaitingWallpapersViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val activities: ActivityRepository,
    private val friends: FriendsRepository,
) : ViewModel() {
    val waiting: StateFlow<List<ActivityItem>> = activities.items
        .map { items -> items.orEmpty().filter { it.actionable && it.kindValue == ActivityKind.WallpaperRequest && it.subjectId != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() = viewModelScope.launch { activities.refresh() }

    fun apply(item: ActivityItem, target: WallpaperTarget, always: Boolean) {
        val commandId = item.subjectId ?: return
        WallpaperWorker.enqueue(context, commandId, approved = true, target = target)
        activities.markResolved(commandId, "applied")
        val friendId = item.actor?.id
        if (always && friendId != null) {
            viewModelScope.launch {
                friends.updatePermissions(friendId, buildJsonObject { put("wallpaper_mode", WallpaperMode.Auto.wire) }, null)
            }
        }
    }
}

/** "Riya sent you a wallpaper": put it on right from Home, or look at it first in the inbox. */
@Composable
fun WaitingWallpapers(onOpenInbox: () -> Unit, modifier: Modifier = Modifier, viewModel: WaitingWallpapersViewModel = hiltViewModel()) {
    val waiting by viewModel.waiting.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val item = waiting.firstOrNull() ?: return
    var choosing by remember { mutableStateOf(false) }
    val name = item.actor?.name?.ifBlank { null } ?: stringResource(R.string.inbox_someone)
    CrayonCard(modifier = modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.peach, seed = 77) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (waiting.size > 1) stringResource(R.string.waiting_many, name, waiting.size - 1) else stringResource(R.string.waiting_one, name),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CrayonButton(stringResource(R.string.inbox_apply), onClick = { choosing = true }, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(R.string.waiting_look), onClick = onOpenInbox, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
            }
        }
    }
    if (choosing) {
        ApplyChooser(
            name = name,
            suggested = WallpaperTarget.fromWire(item.dataString("target")),
            onApply = { target, always ->
                choosing = false
                viewModel.apply(item, target, always)
            },
            onClose = { choosing = false },
        )
    }
}
