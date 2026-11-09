package com.meyouww.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel

/**
 * Tapping the name in a chat: their photo, the relationship (Best Friend, girlfriend /
 * boyfriend), their profile, and the chat's own settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeerSheet(
    peer: Friend?,
    peerId: String,
    name: String,
    muted: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onInvite: (RelationshipLevel, String?) -> Unit,
    onAnswerInvite: (Boolean) -> Unit,
    onCancelInvite: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSafety: () -> Unit,
    /** Photos, videos, documents, wallpapers and links shared in this chat. */
    onOpenGallery: () -> Unit = {},
) {
    val crayon = MeyouwwTheme.crayon
    val level = peer?.levelValue ?: RelationshipLevel.Friend
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PersonAvatar(
                    name.take(1).uppercase(),
                    peerId.hashCode(),
                    size = 76.dp,
                    photo = peer?.avatar,
                    modifier = Modifier.clip(CircleShape).clickable(onClickLabel = stringResource(R.string.chat_view_photo), role = Role.Button, onClick = onPhoto),
                )
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    peer?.card?.handle?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft) }
                    Text(levelName(level, peer?.label), style = MaterialTheme.typography.titleMedium)
                }
            }

            CrayonButton(
                stringResource(R.string.chat_gallery_open),
                onClick = onOpenGallery,
                kind = ButtonKind.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )

            // The relationship: invites in either direction, or the next step up.
            val invite = peer?.invite
            when {
                invite != null && invite.incoming -> {
                    Text(
                        stringResource(R.string.sheet_invite_incoming, name, levelName(invite.levelValue, invite.label)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CrayonButton(stringResource(R.string.sheet_yes), onClick = { onAnswerInvite(true) }, loading = busy, modifier = Modifier.weight(1f))
                        CrayonButton(stringResource(R.string.sheet_not_now), onClick = { onAnswerInvite(false) }, kind = ButtonKind.Quiet, enabled = !busy, modifier = Modifier.weight(1f))
                    }
                }
                invite != null -> {
                    Text(stringResource(R.string.sheet_invite_waiting, name, levelName(invite.levelValue, invite.label)), style = MaterialTheme.typography.bodyLarge)
                    CrayonButton(stringResource(R.string.sheet_invite_cancel), onClick = onCancelInvite, kind = ButtonKind.Quiet, loading = busy, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    if (level.rank < RelationshipLevel.BestFriend.rank) {
                        CrayonButton(
                            stringResource(R.string.sheet_make_bestie),
                            onClick = { onInvite(RelationshipLevel.BestFriend, null) },
                            kind = ButtonKind.Secondary,
                            loading = busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (level != RelationshipLevel.Partner) {
                        val girlfriend = stringResource(R.string.sheet_label_girlfriend)
                        val boyfriend = stringResource(R.string.sheet_label_boyfriend)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CrayonButton(stringResource(R.string.sheet_make_girlfriend), onClick = { onInvite(RelationshipLevel.Partner, girlfriend) }, enabled = !busy, modifier = Modifier.weight(1f))
                            CrayonButton(stringResource(R.string.sheet_make_boyfriend), onClick = { onInvite(RelationshipLevel.Partner, boyfriend) }, enabled = !busy, modifier = Modifier.weight(1f))
                        }
                        Text(stringResource(R.string.sheet_partner_hint), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                CrayonButton(stringResource(if (muted) R.string.chat_unmute else R.string.chat_mute), onClick = onToggleMute, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(R.string.sheet_safety), onClick = onOpenSafety, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
            }
            CrayonButton(stringResource(R.string.sheet_profile), onClick = onOpenProfile, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun levelName(level: RelationshipLevel, label: String?): String = when (level) {
    RelationshipLevel.Friend -> stringResource(R.string.chat_level_friend)
    RelationshipLevel.BestFriend -> stringResource(R.string.chat_level_bestie)
    RelationshipLevel.Partner -> stringResource(R.string.chat_level_partner, label?.ifBlank { null } ?: stringResource(R.string.chat_level_partner_default))
}
