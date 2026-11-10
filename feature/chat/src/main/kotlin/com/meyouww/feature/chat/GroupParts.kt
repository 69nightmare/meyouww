package com.meyouww.feature.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meyouww.core.chat.ChatGroupMember
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.chat.PollState
import com.meyouww.core.chat.ReminderState
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Friend
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The most people one group can hold (me included), as the server allows. */
internal const val GROUP_MAX = 32

/** A name colour per member, readable on both bubble colours and in dark mode. */
internal fun memberInk(userId: String): Color {
    val inks = listOf(
        Color(0xFFB0306A), Color(0xFF2D6CB5), Color(0xFF2E8B57),
        Color(0xFFB8641A), Color(0xFF7A4BC2), Color(0xFFC0392B), Color(0xFF00838F),
    )
    return inks[Math.floorMod(userId.hashCode(), inks.size)]
}

/**
 * Picking friends: for a new group (with its name) or to add people to one.
 * [error] is a message to show, e.g. when the server said no.
 */
@Composable
internal fun PickFriendsDialog(
    title: String,
    friends: List<Friend>,
    confirm: String,
    askName: Boolean,
    busy: Boolean,
    error: String?,
    maxPick: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, ids: List<String>) -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val picked = rememberSaveable(saver = stringListSaver()) { mutableStateListOf() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CrayonCard(modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth(), seed = 914) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel)) }
                }
                if (askName) {
                    CrayonTextField(
                        value = name,
                        onValueChange = { name = it.take(ChatRepository.MAX_GROUP_TITLE) },
                        label = stringResource(R.string.group_name_label),
                        placeholder = stringResource(R.string.group_name_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (friends.size > 6) {
                    CrayonTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = stringResource(R.string.people_search_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (friends.isEmpty()) {
                    Text(stringResource(R.string.group_no_friends), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
                }
                val term = query.trim()
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                ) {
                    friends.filter { term.isEmpty() || it.card.name.contains(term, true) || it.card.handle.contains(term, true) }.forEach { friend ->
                        val on = friend.id in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .toggleable(value = on, role = Role.Checkbox, enabled = on || picked.size < maxPick) {
                                    if (on) picked.remove(friend.id) else picked.add(friend.id)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            PersonAvatar(friend.card.initial, friend.id.hashCode(), size = 40.dp, photo = friend.card.avatar)
                            Text(friend.card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(if (on) "✅" else "⬜", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Text(
                    pluralStringResource(R.plurals.group_picked, picked.size, picked.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = crayon.textSoft,
                )
                if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                CrayonButton(
                    confirm,
                    onClick = { onConfirm(name.trim(), picked.toList()) },
                    enabled = picked.isNotEmpty() && (!askName || name.isNotBlank()),
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Tapping a group's name: who's in it, adding and removing people, its name, media, leaving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupSheet(
    name: String,
    groupId: String,
    members: List<ChatGroupMember>,
    myId: String?,
    muted: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    /** Admins only. */
    onAddPeople: (() -> Unit)?,
    onRemove: (ChatGroupMember) -> Unit,
    onLeave: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val amAdmin = members.any { it.userId == myId && it.admin }
    var removing by remember { mutableStateOf<ChatGroupMember?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
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
                PersonAvatar(name.take(1).uppercase(), groupId.hashCode(), size = 76.dp)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(
                        pluralStringResource(R.plurals.group_people, members.size, members.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = crayon.textSoft,
                    )
                }
            }
            CrayonButton(stringResource(R.string.chat_gallery_open), onClick = onOpenGallery, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CrayonButton(stringResource(R.string.group_rename), onClick = onRename, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(if (muted) R.string.chat_unmute else R.string.chat_mute), onClick = onToggleMute, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.group_members_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (onAddPeople != null && members.size < GROUP_MAX) {
                CrayonButton(stringResource(R.string.group_add_people), onClick = onAddPeople, modifier = Modifier.fillMaxWidth())
            }
            members.forEach { member ->
                val me = member.userId == myId
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(member.name.take(1).uppercase().ifBlank { "?" }, member.userId.hashCode(), size = 42.dp, photo = member.avatar)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (me) stringResource(R.string.group_member_you) else member.name.ifBlank { member.username.orEmpty() },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        member.username?.takeIf { it.isNotBlank() && !me }?.let {
                            Text("@$it", style = MaterialTheme.typography.bodySmall, color = crayon.textSoft, maxLines = 1)
                        }
                    }
                    if (member.admin) CrayonChip(stringResource(R.string.group_admin), fill = crayon.yellow)
                    if (amAdmin && !me) {
                        TextButton(onClick = { removing = member }) { Text(stringResource(R.string.group_remove)) }
                    }
                }
            }
            CrayonButton(stringResource(R.string.group_leave), onClick = { leaving = true }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Text(stringResource(R.string.group_e2ee_hint), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
        }
    }
    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.group_remove_title, member.name)) },
            text = { Text(stringResource(R.string.group_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    onRemove(member)
                }) { Text(stringResource(R.string.group_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text(stringResource(R.string.group_leave_title, name)) },
            text = { Text(stringResource(R.string.group_leave_body)) },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    onLeave()
                }) { Text(stringResource(R.string.group_leave)) }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }
}

/** A new name for the group, for everyone in it. */
@Composable
internal fun RenameGroupDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_rename)) },
        text = {
            CrayonTextField(
                value = name,
                onValueChange = { name = it.take(ChatRepository.MAX_GROUP_TITLE) },
                label = stringResource(R.string.group_name_label),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.group_rename_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
    )
}

/** Writing a poll: a question and 2 to 8 choices, one answer or several. */
@Composable
internal fun PollDialog(onDismiss: () -> Unit, onSend: (question: String, options: List<String>, multi: Boolean) -> Unit) {
    val crayon = MeyouwwTheme.crayon
    var question by rememberSaveable { mutableStateOf("") }
    var multi by rememberSaveable { mutableStateOf(false) }
    val options = rememberSaveable(saver = stringListSaver()) { mutableStateListOf("", "") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CrayonCard(modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth(), seed = 915) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.group_poll_new), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel)) }
                }
                CrayonTextField(
                    value = question,
                    onValueChange = { question = it.take(ChatRepository.MAX_QUESTION) },
                    label = stringResource(R.string.group_poll_question),
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { i, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CrayonTextField(
                            value = value,
                            onValueChange = { options[i] = it.take(PollState.MAX_OPTION_CHARS) },
                            label = stringResource(R.string.group_poll_choice, i + 1),
                            modifier = Modifier.weight(1f),
                        )
                        if (options.size > 2) {
                            IconButton(onClick = { options.removeAt(i) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.group_poll_remove_choice, i + 1))
                            }
                        }
                    }
                }
                if (options.size < PollState.MAX_OPTIONS) {
                    CrayonButton(stringResource(R.string.group_poll_add_choice), onClick = { options.add("") }, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
                }
                CrayonChip(
                    text = (if (multi) "✓ " else "") + stringResource(R.string.group_poll_multi),
                    fill = if (multi) crayon.blue else MaterialTheme.colorScheme.surface,
                    onClick = { multi = !multi },
                )
                val ready = question.isNotBlank() && ChatRepository.cleanOptions(options) != null
                CrayonButton(
                    stringResource(R.string.group_poll_send),
                    onClick = { onSend(question.trim(), options.toList(), multi) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A reminder for everyone in the group: what and when. It goes into each person's calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupReminderDialog(onDismiss: () -> Unit, onSend: (what: String, start: LocalDateTime) -> Unit) {
    val context = LocalContext.current
    val initial = remember { LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0) }
    var what by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableLongStateOf(initial.toLocalDate().toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.minute) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpochDay)
    val start = LocalDateTime.of(date, LocalTime.of(hour, minute))
    val ahead = start.isAfter(LocalDateTime.now())
    val crayon = MeyouwwTheme.crayon
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CrayonCard(modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth(), seed = 916) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.group_reminder_new), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel)) }
                }
                Text(stringResource(R.string.group_reminder_explain), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
                CrayonTextField(
                    value = what,
                    onValueChange = { what = it.take(ChatRepository.MAX_REMINDER_TITLE) },
                    label = stringResource(R.string.group_reminder_what),
                    placeholder = stringResource(R.string.group_reminder_what_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrayonChip(
                        text = "📅 " + remember(date) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date) },
                        fill = crayon.green,
                        onClick = { pickDate = true },
                    )
                    CrayonChip(
                        text = "🕒 " + remember(hour, minute) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.of(hour, minute)) },
                        fill = crayon.blue,
                        onClick = { pickTime = true },
                    )
                }
                if (!ahead) {
                    Text(stringResource(R.string.group_reminder_past), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                CrayonButton(
                    stringResource(R.string.group_reminder_send),
                    onClick = { onSend(what.trim(), start) },
                    enabled = what.isNotBlank() && ahead,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (pickDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickDate = false
                }) { Text(stringResource(R.string.group_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.chat_cancel)) } },
        ) { DatePicker(state = dateState) }
    }
    if (pickTime) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    pickTime = false
                }) { Text(stringResource(R.string.group_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.chat_cancel)) } },
            text = { TimePicker(state = timeState) },
        )
    }
}

/** A poll inside its bubble: tap a choice to vote (again to take it back); bars show how it's going. */
@Composable
internal fun PollContent(question: String, poll: PollState, myId: String?, ink: Color, onVote: (List<Int>) -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val counts = poll.counts()
    val voters = poll.votes.size
    val mine = myId?.let { poll.votes[it] }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(min = 200.dp)) {
        Text(stringResource(R.string.group_poll_label), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f))
        Text(question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
        poll.options.forEachIndexed { i, option ->
            val picked = i in mine
            val share by animateFloatAsState(if (voters == 0) 0f else counts[i] / voters.toFloat(), tween(320), label = "poll")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, crayon.outline, RoundedCornerShape(12.dp))
                    .drawBehind { drawRect(ink.copy(alpha = 0.14f), size = Size(size.width * share, size.height)) }
                    .toggleable(value = picked, role = if (poll.multi) Role.Checkbox else Role.RadioButton) {
                        val next = when {
                            poll.multi && picked -> mine - i
                            poll.multi -> mine + i
                            picked -> emptyList()
                            else -> listOf(i)
                        }
                        onVote(next.sorted())
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(if (picked) "✅" else if (poll.multi) "⬜" else "⚪", style = MaterialTheme.typography.bodyMedium)
                Text(option, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
                Text(counts[i].toString(), style = MaterialTheme.typography.labelLarge, color = ink)
            }
        }
        Text(
            when {
                voters == 0 -> stringResource(if (poll.multi) R.string.group_poll_tap_multi else R.string.group_poll_tap)
                else -> pluralStringResource(R.plurals.group_poll_votes, voters, voters)
            },
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = 0.78f),
        )
    }
}

/** A reminder inside its bubble: what, when, and whether it is in this phone's calendar. */
@Composable
internal fun ReminderContent(what: String, reminder: ReminderState, ink: Color) {
    val start = remember(reminder.start) { runCatching { LocalDateTime.parse(reminder.start) }.getOrNull() }
    val whenText = remember(start) { start?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(it) }.orEmpty() }
    val past = start?.isBefore(LocalDateTime.now()) == true
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.widthIn(min = 180.dp)) {
        Text(stringResource(R.string.group_reminder_label), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f))
        Text(what, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
        if (whenText.isNotEmpty()) Text("🕒 $whenText", style = MaterialTheme.typography.bodyMedium, color = ink)
        Text(
            stringResource(
                when {
                    past -> R.string.group_reminder_done
                    reminder.added -> R.string.group_reminder_added
                    else -> R.string.group_reminder_adding
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = 0.78f),
        )
    }
}

/** The member's name over their first bubble in a run. */
@Composable
internal fun SenderName(name: String, userId: String, modifier: Modifier = Modifier) {
    Text(
        name,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = memberInk(userId),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun stringListSaver() = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)
