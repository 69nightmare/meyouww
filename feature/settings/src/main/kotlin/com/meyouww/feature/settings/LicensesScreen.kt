package com.meyouww.feature.settings

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.SectionTitle
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.R as DesignR

private data class FontLicense(val name: String, val use: String, val rawRes: Int)

private val fonts = listOf(
    FontLicense("Fredoka", "Headings", DesignR.raw.ofl_fredoka),
    FontLicense("Nunito", "Body text", DesignR.raw.ofl_nunito),
    FontLicense("Quicksand", "Labels", DesignR.raw.ofl_quicksand),
    FontLicense("Gaegu", "Handwritten notes", DesignR.raw.ofl_gaegu),
    FontLicense("Zen Maru Gothic", "Body text", DesignR.raw.ofl_zen_maru_gothic),
    FontLicense("Gochi Hand", "Handwriting", DesignR.raw.ofl_gochi_hand),
    FontLicense("Mochiy Pop One", "Headings and buttons", DesignR.raw.ofl_mochiy),
)

/** Main libraries and their licences. Kept in sync with gradle/libs.versions.toml. */
private val libraries = listOf(
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
    "Android Jetpack (Compose, Material 3, Lifecycle, Navigation, WorkManager, DataStore, Core)" to "Apache License 2.0",
    "Dagger / Hilt" to "Apache License 2.0",
    "supabase-kt" to "MIT License",
    "Ktor" to "Apache License 2.0",
    "Google Tink" to "Apache License 2.0",
    "Firebase Cloud Messaging" to "Apache License 2.0",
    "Google Play services base and code scanner" to "Android Software Development Kit License",
    "libsignal (Signal Messenger)" to "GNU Affero General Public License v3.0",
    "SQLCipher for Android (Zetetic)" to "BSD-style licence",
    "Room, SQLite (Android Jetpack)" to "Apache License 2.0",
    "ZXing" to "Apache License 2.0",
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    CrayonScreen(title = stringResource(R.string.licenses_title), onBack = onBack) {

        SectionTitle(stringResource(R.string.licenses_art))
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 1) {
            Text(stringResource(R.string.licenses_whity), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.licenses_whity_note), style = MaterialTheme.typography.bodyMedium)
        }

        SectionTitle(stringResource(R.string.licenses_fonts))
        Text(stringResource(R.string.licenses_font_note), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        fonts.forEach { FontCard(it) }

        SectionTitle(stringResource(R.string.licenses_libraries))
        Text(stringResource(R.string.licenses_app_agpl), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 2) {
            libraries.forEach { (name, license) ->
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(license, style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
            }
        }
    }
}

@Composable
private fun FontCard(font: FontLicense) {
    val context = LocalContext.current
    var open by rememberSaveable(font.name) { mutableStateOf(false) }
    val text = remember(open) {
        if (open) context.resources.openRawResource(font.rawRes).bufferedReader().use { it.readText() } else ""
    }
    CrayonCard(modifier = Modifier.fillMaxWidth(), seed = font.name.hashCode()) {
        Text(font.name, style = MaterialTheme.typography.titleMedium)
        Text("${font.use} · SIL Open Font License 1.1", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { open = !open }) {
            Text(stringResource(if (open) R.string.licenses_hide else R.string.licenses_show), color = MeyouwwTheme.crayon.link)
        }
        if (open) Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
