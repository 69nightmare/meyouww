package com.meyouww.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhitySticker
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject

/** A newer meyouww on GitHub: its version, what it brings and where its APK is. */
data class NewVersion(
    val version: String,
    val url: String,
    val notes: List<String> = emptyList(),
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
    for (line in lines.drop(start + 1)) {
        if (line.startsWith("#")) break
        val clean = line.replace("**", "").replace("`", "").trimEnd()
        when {
            clean.isBlank() -> Unit
            clean.trimStart().startsWith("- ") -> bullets += clean.trimStart().removePrefix("- ").trim()
            bullets.isNotEmpty() -> bullets[bullets.lastIndex] = bullets.last() + " " + clean.trim()
        }
    }
    return bullets
}

/** The newest release above [installed] among GitHub's releases (pre-releases included, drafts not). */
internal fun newestRelease(releases: JsonArray, installed: String): NewVersion? {
    val candidates = releases.mapNotNull { it as? JsonObject }
        .filter { it["draft"]?.jsonPrimitive?.booleanOrNull != true }
        .mapNotNull { r -> r["tag_name"]?.jsonPrimitive?.content?.let { it to r } }
    val (tag, release) = candidates.fold(null as Pair<String, JsonObject>?) { best, c ->
        if (best == null || isNewer(c.first, best.first)) c else best
    } ?: return null
    if (!isNewer(tag, installed)) return null
    val assets = release["assets"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
    fun asset(suffix: String) = assets.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(suffix) == true }
        ?.get("browser_download_url")?.jsonPrimitive?.content
    return NewVersion(
        version = tag.removePrefix("v"),
        url = release["html_url"]?.jsonPrimitive?.content ?: UpdateViewModel.RELEASES,
        notes = whatsNew(release["body"]?.jsonPrimitive?.content),
        apkUrl = asset(".apk"),
        sha256Url = asset(".apk.sha256"),
    )
}

/** Where the in-app update is. */
sealed interface UpdateStep {
    data object Idle : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    data object Checking : UpdateStep
    data class Ready(val file: File) : UpdateStep
    data object NeedsPermission : UpdateStep
    data object Failed : UpdateStep
    data object BadFile : UpdateStep
}

/**
 * meyouww is installed from GitHub releases, so an old copy on one phone is easy to miss
 * (and makes new features look broken). Checks once per start; offers a pop-up that
 * downloads the APK, checks its SHA-256 against the release, and opens Android's installer.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _newer = MutableStateFlow<NewVersion?>(null)
    val newer: StateFlow<NewVersion?> = _newer.asStateFlow()

    private val _prompt = MutableStateFlow(false)

    /** The pop-up: shown once per app start when there is something newer. */
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    private val _step = MutableStateFlow<UpdateStep>(UpdateStep.Idle)
    val step: StateFlow<UpdateStep> = _step.asStateFlow()

    init {
        viewModelScope.launch {
            _newer.value = check()
            if (_newer.value != null) _prompt.value = true
        }
    }

    fun later() {
        _prompt.value = false
    }

    fun show() {
        _prompt.value = true
    }

    private suspend fun check(): NewVersion? = withContext(Dispatchers.IO) {
        runCatching {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return@runCatching null
            val body = get(LIST) ?: return@runCatching null
            newestRelease(Json.parseToJsonElement(body).jsonArray, installed)
        }.getOrNull()
    }

    /** Downloads, checks and hands the APK to Android's installer. */
    fun update() {
        val version = _newer.value ?: return
        val apkUrl = version.apkUrl ?: return openPage(version.url)
        if (_step.value is UpdateStep.Downloading || _step.value == UpdateStep.Checking) return
        viewModelScope.launch {
            val file = File(File(context.cacheDir, "updates").apply { mkdirs() }, "meyouww-${version.version}.apk")
            val ok = withContext(Dispatchers.IO) { runCatching { download(apkUrl, file) }.getOrDefault(false) }
            if (!ok) {
                _step.value = UpdateStep.Failed
                return@launch
            }
            _step.value = UpdateStep.Checking
            val expected = version.sha256Url?.let { url -> withContext(Dispatchers.IO) { runCatching { get(url) }.getOrNull() } }
                ?.trim()?.substringBefore(' ')?.lowercase()
            val actual = withContext(Dispatchers.IO) { sha256(file) }
            if (expected != null && expected != actual) {
                file.delete()
                _step.value = UpdateStep.BadFile
                return@launch
            }
            _step.value = UpdateStep.Ready(file)
            install(file)
        }
    }

    /** Opens Android's installer, or first the "Install unknown apps" switch for meyouww. */
    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _step.value = UpdateStep.NeedsPermission
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        _step.value = UpdateStep.Ready(file)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openPage(_newer.value?.url ?: RELEASES)
        }
    }

    /** After coming back from the "Install unknown apps" switch. */
    fun retryInstall() {
        val ready = _newer.value ?: return
        val file = File(File(context.cacheDir, "updates"), "meyouww-${ready.version}.apk")
        if (file.exists()) install(file) else update()
    }

    private fun openPage(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun download(url: String, target: File): Boolean {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != 200) return false
            val total = connection.contentLengthLong.takeIf { it > 0 }
            var done = 0L
            _step.value = UpdateStep.Downloading(0f)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) _step.update { UpdateStep.Downloading((done.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
            return total == null || done == total
        } finally {
            connection.disconnect()
        }
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Every meyouww release is a pre-release, which /releases/latest leaves out. */
        const val LIST = "https://api.github.com/repos/69nightmare/meyouww/releases?per_page=10"
        const val RELEASES = "https://github.com/69nightmare/meyouww/releases"
    }
}

/** The small card on Home while a newer version exists (after "Later" on the pop-up). */
@Composable
fun UpdateBanner(viewModel: UpdateViewModel, modifier: Modifier = Modifier) {
    val newer by viewModel.newer.collectAsStateWithLifecycle()
    val version = newer ?: return
    CrayonCard(modifier = modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.green, seed = 91) {
        Text(stringResource(R.string.update_title, version.version), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.update_body), style = MaterialTheme.typography.bodyMedium)
        CrayonButton(
            text = stringResource(R.string.update_get),
            onClick = viewModel::show,
            kind = ButtonKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
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
    val busy = step is UpdateStep.Downloading || step == UpdateStep.Checking
    Dialog(onDismissRequest = { if (!busy) viewModel.later() }) {
        CrayonCard(modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(), seed = 2026) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Sticker(WhitySticker.PartyConfetti, size = 110.dp, decorative = true)
                Text(stringResource(R.string.update_prompt_title, version.version), style = MaterialTheme.typography.titleLarge)
            }
            if (version.notes.isNotEmpty()) {
                Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                ) {
                    version.notes.forEach { note ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyMedium)
                            Text(note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            val status = when (val s = step) {
                is UpdateStep.Downloading -> stringResource(R.string.update_downloading, (s.progress * 100).toInt())
                UpdateStep.Checking -> stringResource(R.string.update_checking)
                UpdateStep.NeedsPermission -> stringResource(R.string.update_allow_body)
                UpdateStep.Failed -> stringResource(R.string.update_failed)
                UpdateStep.BadFile -> stringResource(R.string.update_bad_file)
                else -> null
            }
            (step as? UpdateStep.Downloading)?.let { LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MeyouwwTheme.crayon.textSoft, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                CrayonButton(stringResource(R.string.update_later), onClick = viewModel::later, kind = ButtonKind.Quiet, enabled = !busy, modifier = Modifier.weight(1f))
                val label = when (step) {
                    UpdateStep.NeedsPermission, is UpdateStep.Ready -> R.string.update_install
                    else -> R.string.update_now
                }
                CrayonButton(
                    stringResource(label),
                    onClick = { if (step == UpdateStep.NeedsPermission || step is UpdateStep.Ready) viewModel.retryInstall() else viewModel.update() },
                    loading = busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
