package com.meyouww.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** A newer meyouww on GitHub: its version and where to get it. */
data class NewVersion(val version: String, val url: String)

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
 * meyouww is installed from GitHub releases, so an old copy on one phone is easy to
 * miss (and makes new features look broken). Checks the latest release once per start.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _newer = MutableStateFlow<NewVersion?>(null)
    val newer: StateFlow<NewVersion?> = _newer.asStateFlow()

    init {
        viewModelScope.launch { _newer.value = check() }
    }

    private suspend fun check(): NewVersion? = withContext(Dispatchers.IO) {
        runCatching {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return@runCatching null
            val connection = URL(LATEST).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode != 200) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = Json.parseToJsonElement(body).jsonObject
                val tag = json["tag_name"]?.jsonPrimitive?.content ?: return@runCatching null
                val url = json["html_url"]?.jsonPrimitive?.content ?: RELEASES
                if (isNewer(tag, installed)) NewVersion(tag.removePrefix("v"), url) else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private companion object {
        const val LATEST = "https://api.github.com/repos/69nightmare/meyouww/releases/latest"
        const val RELEASES = "https://github.com/69nightmare/meyouww/releases"
    }
}

@Composable
fun UpdateBanner(modifier: Modifier = Modifier, viewModel: UpdateViewModel = hiltViewModel()) {
    val newer by viewModel.newer.collectAsStateWithLifecycle()
    val version = newer ?: return
    val context = LocalContext.current
    CrayonCard(modifier = modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.green, seed = 91) {
        Text(stringResource(R.string.update_title, version.version), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.update_body), style = MaterialTheme.typography.bodyMedium)
        CrayonButton(
            text = stringResource(R.string.update_get),
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(version.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            kind = ButtonKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
