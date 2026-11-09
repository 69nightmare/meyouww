package com.meyouww.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.SectionTitle
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.component.kasukabeBackdrop
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.feature.chat.ChatDesignSample
import com.meyouww.feature.onboarding.WelcomeScreen
import com.meyouww.feature.friends.R as FriendsR
import com.meyouww.feature.settings.R as SettingsR

/** Debug-only render targets. Callbacks are inert and no account or repository is opened. */
@Preview(name = "Welcome · phone", widthDp = 360, heightDp = 800, apiLevel = 37)
@Composable
fun WelcomePhonePreview() {
    CrayonPreviewSurface { WelcomeScreen(onGetStarted = {}) }
}

@Preview(name = "Partner chat · phone", widthDp = 360, heightDp = 800, apiLevel = 37)
@Composable
fun PartnerChatPhonePreview() {
    CrayonPreviewSurface { ChatDesignSample(RelationshipLevel.Partner, wallpaper = null) }
}

@Preview(name = "Paper components · phone", widthDp = 360, heightDp = 800, apiLevel = 37)
@Composable
fun PaperComponentsPhonePreview() {
    CrayonPreviewSurface { PaperComponentsSample() }
}

@Preview(name = "Paper components · 200% text", widthDp = 320, heightDp = 800, fontScale = 2f, apiLevel = 37)
@Composable
fun PaperComponentsLargeTextPreview() {
    CrayonPreviewSurface { PaperComponentsSample() }
}

@Composable
private fun CrayonPreviewSurface(content: @Composable () -> Unit) {
    MeyouwwTheme {
        Surface(
            modifier = Modifier.fillMaxSize().kasukabeBackdrop(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

/** Exercises the actual shared layouts with long production labels and narrow action rows. */
@Composable
private fun PaperComponentsSample() {
    CrayonScreen(title = stringResource(SettingsR.string.settings_background_delivery), onBack = {}) {
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.peach, seed = 940) {
            Text(stringResource(SettingsR.string.settings_home_base), style = MaterialTheme.typography.titleMedium)
            CrayonButton(
                stringResource(SettingsR.string.settings_home_base_pick),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            CrayonButton(
                stringResource(SettingsR.string.settings_home_base_restore),
                onClick = {},
                kind = ButtonKind.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(SettingsR.string.settings_my_phone_body), style = MaterialTheme.typography.bodyMedium)
        }
        SectionTitle(stringResource(SettingsR.string.settings_my_phone))
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 941) {
            SwitchRow(
                title = stringResource(SettingsR.string.settings_share_model),
                detail = stringResource(SettingsR.string.settings_share_model_detail),
                checked = true,
                onCheckedChange = {},
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrayonButton(stringResource(FriendsR.string.friends_accept), onClick = {}, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(FriendsR.string.friends_not_now), onClick = {}, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
            }
        }
    }
}
