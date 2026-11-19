package com.meyouww.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.kasukabeBackdrop
import com.meyouww.core.designsystem.theme.MeyouwwLook
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.SkyPhase
import com.meyouww.core.model.AlarmInfo
import com.meyouww.core.model.EventInfo
import com.meyouww.core.model.PersonCard
import com.meyouww.core.model.Profile
import java.time.LocalDate
import java.time.ZoneId

/** Debug-only: Home in the Living Sky look with sample data, to compare with its design board. */
@Preview(name = "Living Sky · Home · day", widthDp = 390, heightDp = 844, apiLevel = 37)
@Composable
fun SkyHomeDayPreview() = SkyHomeSample(SkyPhase.Day)

@Preview(name = "Living Sky · Home · night", widthDp = 390, heightDp = 844, apiLevel = 37)
@Composable
fun SkyHomeNightPreview() = SkyHomeSample(SkyPhase.Night)

@Composable
private fun SkyHomeSample(phase: SkyPhase) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val me = PersonCard("me", "kabir.cloud", "Kabir")
    val riya = PersonCard("riya", "riya.sun", "Riya")
    fun at(date: LocalDate, h: Int, m: Int) = date.atTime(h, m).atZone(zone).toInstant().toString()
    val events = listOf(
        EventInfo("e1", owner = riya, title = "Movie night", emoji = "🎬", startsAt = at(today, 18, 30), localDate = today.toString(), myStatus = EventInfo.STATUS_INVITED),
        EventInfo("e2", owner = me, mine = true, title = "Goa trip", emoji = "🏖️", startsAt = at(today.plusDays(9), 9, 0), localDate = today.plusDays(9).toString(), myStatus = EventInfo.STATUS_GOING),
        EventInfo("e3", owner = riya, title = "Riya's birthday", emoji = "🎂", startsAt = at(today.plusDays(13), 0, 0), allDay = true, localDate = today.plusDays(13).toString(), myStatus = EventInfo.STATUS_GOING),
    )
    val alarms = listOf(
        AlarmInfo(id = "a1", toMe = true, sender = riya, recipient = me, title = "Call me back", ringAt = at(today, 21, 30), localTime = "21:30", status = "accepted"),
        AlarmInfo(id = "a2", toMe = true, sender = riya, recipient = me, title = "Wake up!", ringAt = at(today.plusDays(1), 7, 0), localTime = "07:00", status = "accepted"),
    )
    val state = HomeUiState(profile = Profile(id = "me", username = "kabir.cloud", displayName = "Kabir", nickname = "Kabu"), loading = false, events = events, alarms = alarms)
    val none = SkyHomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
    MeyouwwTheme(look = MeyouwwLook.Sky, skyPhase = phase) {
        Surface(Modifier.fillMaxSize().kasukabeBackdrop(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            SkyHomeScreen(state, none, heart = { SkyIconButton(SkyIcons.Heart, "Inbox", {}, badge = 3, tint = MaterialTheme.colorScheme.primary) }, banner = {})
        }
    }
}
