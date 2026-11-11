package com.meyouww.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** What one release brought, for the pop-up. */
data class ReleaseNotes(val version: String, val bullets: List<String>)

/**
 * A newer meyouww on GitHub: its version, where its APK is, and what changed in every
 * release since the installed one (newest first), so a big jump shows everything.
 */
data class NewVersion(
    val version: String,
    val url: String,
    val notes: List<ReleaseNotes> = emptyList(),
    val apkUrl: String? = null,
    val sha256Url: String? = null,
)

/** "1.2.10" > "1.2.9"; suffixes like "-debug" are ignored. */
internal fun isNewer(latest: String, installed: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(latest)
    val b = parts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/**
 * The "What's new" bullets from a release's notes: the lines under a "What's new" heading
 * (up to the next heading), without Markdown marks. Wrapped lines join their bullet.
 */
internal fun whatsNew(body: String?): List<String> {
    val lines = body.orEmpty().replace("\r\n", "\n").lines()
    val start = lines.indexOfFirst { it.trimStart('#', ' ').startsWith("What's new", ignoreCase = true) }
    if (start < 0) return emptyList()
    val bullets = mutableListOf<String>()
    var joinable = false
    for (line in lines.drop(start + 1)) {
        if (line.startsWith("#")) break
        val clean = line.replace("**", "").replace("`", "").trimEnd()
        when {
            clean.isBlank() || clean.trimStart().startsWith("<!--") -> joinable = false
            clean.trimStart().startsWith("- ") -> {
                bullets += clean.trimStart().removePrefix("- ").trim()
                joinable = true
            }
            // A wrapped bullet continues on the very next lines only.
            joinable && bullets.isNotEmpty() -> bullets[bullets.lastIndex] = bullets.last() + " " + clean.trim()
        }
    }
    return bullets
}

/** Notes of the releases after [after] up to and including [upTo], newest first. */
internal fun releasesBetween(releases: JsonArray, after: String, upTo: String): List<ReleaseNotes> =
    releases.mapNotNull { it as? JsonObject }
        .filter { it["draft"]?.jsonPrimitive?.booleanOrNull != true }
        .mapNotNull { r -> r["tag_name"]?.jsonPrimitive?.content?.let { it to r } }
        .filter { (tag, _) -> isNewer(tag, after) && !isNewer(tag, upTo) }
        .sortedWith { a, b -> if (isNewer(a.first, b.first)) -1 else if (isNewer(b.first, a.first)) 1 else 0 }
        .map { (t, r) -> ReleaseNotes(t.removePrefix("v"), whatsNew(r["body"]?.jsonPrimitive?.content)) }
        .filter { it.bullets.isNotEmpty() }

/**
 * The newest release above [installed] among GitHub's releases (pre-releases included,
 * drafts not), with the notes of every release between the two.
 */
internal fun newestRelease(releases: JsonArray, installed: String): NewVersion? {
    val newer = releases.mapNotNull { it as? JsonObject }
        .filter { it["draft"]?.jsonPrimitive?.booleanOrNull != true }
        .mapNotNull { r -> r["tag_name"]?.jsonPrimitive?.content?.let { it to r } }
        .filter { (tag, _) -> isNewer(tag, installed) }
        .sortedWith { a, b -> if (isNewer(a.first, b.first)) -1 else if (isNewer(b.first, a.first)) 1 else 0 }
    val (tag, release) = newer.firstOrNull() ?: return null
    val assets = release["assets"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
    fun asset(suffix: String) = assets.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(suffix) == true }
        ?.get("browser_download_url")?.jsonPrimitive?.content
    return NewVersion(
        version = tag.removePrefix("v"),
        url = release["html_url"]?.jsonPrimitive?.content ?: UpdateViewModel.RELEASES,
        notes = newer.map { (t, r) -> ReleaseNotes(t.removePrefix("v"), whatsNew(r["body"]?.jsonPrimitive?.content)) }
            .filter { it.bullets.isNotEmpty() },
        apkUrl = asset(".apk"),
        sha256Url = asset(".apk.sha256"),
    )
}

/** Where the update is: nothing yet, or handed to the browser to download. */
enum class UpdateStep { Idle, Opened }

/**
 * meyouww is installed from GitHub releases, so an old copy on one phone is easy to miss
 * (and makes new features look broken). Checks once per start; offers a pop-up with what
 * is new and a button that hands the download to the phone's browser.
 *
 * meyouww does not install the update itself and holds no permission to install apps: an
 * app that downloads and installs APKs is what Google Play Protect blocks as harmful.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val _newer = MutableStateFlow<NewVersion?>(null)
    val newer: StateFlow<NewVersion?> = _newer.asStateFlow()

    private val _prompt = MutableStateFlow(false)

    /** The pop-up: shown once per app start when there is something newer. */
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    private val _step = MutableStateFlow(UpdateStep.Idle)
    val step: StateFlow<UpdateStep> = _step.asStateFlow()

    private val _whatsNew = MutableStateFlow<List<ReleaseNotes>?>(null)

    /** Just updated: everything that changed since the version this phone had before. */
    val whatsNew: StateFlow<List<ReleaseNotes>?> = _whatsNew.asStateFlow()
    private var installed: String? = null

    init {
        viewModelScope.launch {
            val releases = withContext(Dispatchers.IO) { runCatching { get(LIST)?.let { Json.parseToJsonElement(it).jsonArray } }.getOrNull() }
            val current = installedVersion() ?: return@launch
            installed = current
            if (releases == null) return@launch
            val newest = newestRelease(releases, current)
            _newer.value = newest
            if (newest != null) {
                // Its download was already started: no pop-up every time the app opens (each tap
                // used to start another full download). Home's card says where the file is.
                if (prefs.updateDownloadStarted() == newest.version) {
                    _step.value = UpdateStep.Opened
                } else {
                    _prompt.value = true
                }
                return@launch
            }
            // First start of a new version: show what changed since the one before.
            // Phones updating from before v0.17 never stored it: start after v0.13.
            val last = prefs.lastSeenVersion()
            if (last == null || isNewer(current, last)) {
                val notes = releasesBetween(releases, after = last ?: FIRST_TRACKED, upTo = current)
                if (notes.isEmpty()) prefs.setLastSeenVersion(current) else _whatsNew.value = notes
            }
        }
    }

    fun whatsNewSeen() {
        _whatsNew.value = null
        installed?.let { version -> viewModelScope.launch { prefs.setLastSeenVersion(version) } }
    }

    private fun installedVersion(): String? =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

    fun later() {
        _prompt.value = false
    }

    fun show() {
        _prompt.value = true
    }

    /**
     * Opens the new version's APK in the browser, which downloads it and offers to install it.
     * Without a direct link, the release page.
     */
    fun update() {
        val version = _newer.value ?: return
        // Already downloading or downloaded: show it instead of downloading it again.
        if (_step.value == UpdateStep.Opened) return openDownloads()
        download(version)
    }

    /** Only when asked for in so many words (the first download failed or was deleted). */
    fun downloadAgain() {
        _newer.value?.let(::download)
    }

    private fun download(version: NewVersion) {
        openPage(version.apkUrl ?: version.url)
        _step.value = UpdateStep.Opened
        _prompt.value = false
        viewModelScope.launch { prefs.setUpdateDownloadStarted(version.version) }
    }

    /** The phone's Downloads, where the file is waiting to be tapped and installed. */
    fun openDownloads() {
        _prompt.value = false
        val opened = runCatching {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) openPage(_newer.value?.url ?: RELEASES)
    }

    private fun openPage(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != 200) return null
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Every meyouww release is a pre-release, which /releases/latest leaves out. */
        const val LIST = "https://api.github.com/repos/69nightmare/meyouww/releases?per_page=30"
        const val RELEASES = "https://github.com/69nightmare/meyouww/releases"

        /** The last version without the "What's new" pop-up. */
        const val FIRST_TRACKED = "0.13.0"
    }
}

/** After an update: Whity and everything that changed since the version this phone had. */
@Composable
fun WhatsNewPrompt(viewModel: UpdateViewModel) {
    val notes by viewModel.whatsNew.collectAsStateWithLifecycle()
    val list = notes ?: return
    Dialog(onDismissRequest = viewModel::whatsNewSeen) {
        CrayonCard(modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(), seed = 2027) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Sticker(WhitySticker.PartyChampion, size = 110.dp, decorative = true)
                Text(stringResource(R.string.whats_new_title, list.first().version), style = MaterialTheme.typography.titleLarge)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp),
            ) {
                list.forEach { section ->
                    if (list.size > 1) {
                        Text("v" + section.version, style = MaterialTheme.typography.labelLarge, color = MeyouwwTheme.crayon.textSoft, modifier = Modifier.padding(top = 4.dp))
                    }
                    section.bullets.forEach { note ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyMedium)
                            Text(note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            CrayonButton(stringResource(R.string.whats_new_ok), onClick = viewModel::whatsNewSeen, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/** The small card on Home while a newer version exists (after "Later" on the pop-up). */
@Composable
fun UpdateBanner(viewModel: UpdateViewModel, modifier: Modifier = Modifier) {
    val newer by viewModel.newer.collectAsStateWithLifecycle()
    val version = newer ?: return
    val step by viewModel.step.collectAsStateWithLifecycle()
    val started = step == UpdateStep.Opened
    CrayonCard(modifier = modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.green, seed = 91) {
        Text(stringResource(R.string.update_title, version.version), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (started) R.string.update_opened else R.string.update_body), style = MaterialTheme.typography.bodyMedium)
        CrayonButton(
            text = stringResource(if (started) R.string.update_open_downloads else R.string.update_get),
            onClick = if (started) viewModel::openDownloads else viewModel::show,
            kind = ButtonKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        if (started) {
            TextButton(onClick = viewModel::downloadAgain, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.update_download_again))
            }
        }
    }
}

/** The pop-up: Whity, what the update brings, and one button that updates the app. */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel) {
    val show by viewModel.prompt.collectAsStateWithLifecycle()
    val newer by viewModel.newer.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val version = newer ?: return
    if (!show) return
    Dialog(onDismissRequest = viewModel::later) {
        CrayonCard(modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(), seed = 2026) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Sticker(WhitySticker.PartyConfetti, size = 110.dp, decorative = true)
                Text(stringResource(R.string.update_prompt_title, version.version), style = MaterialTheme.typography.titleLarge)
            }
            if (version.notes.isNotEmpty()) {
                Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                ) {
                    // Every version since the installed one, newest first.
                    version.notes.forEach { section ->
                        if (version.notes.size > 1) {
                            Text("v" + section.version, style = MaterialTheme.typography.labelLarge, color = MeyouwwTheme.crayon.textSoft, modifier = Modifier.padding(top = 4.dp))
                        }
                        section.bullets.forEach { note ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("•", style = MaterialTheme.typography.bodyMedium)
                                Text(note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (step == UpdateStep.Opened) {
                Text(
                    stringResource(R.string.update_opened),
                    style = MaterialTheme.typography.bodySmall,
                    color = MeyouwwTheme.crayon.textSoft,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                CrayonButton(stringResource(R.string.update_later), onClick = viewModel::later, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
                CrayonButton(
                    stringResource(if (step == UpdateStep.Opened) R.string.update_open_downloads else R.string.update_now),
                    onClick = viewModel::update,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
