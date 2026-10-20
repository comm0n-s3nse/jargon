package kaizo.co.WhisperVoiceKeyboard.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kaizo.co.WhisperVoiceKeyboard.R
import java.util.Date
import java.text.DateFormat
import kotlinx.coroutines.launch
import kaizo.co.WhisperVoiceKeyboard.Updates
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context

/** One thing this app is built on, with whose it is, under what license, and where to find it. */
private data class Credit(
    val name: String,
    val by: String,
    val license: String,
    val role: String,
    val url: String,
    val licenseFile: String? = null, // under assets/licenses/; null when the work is not in the app
)

// The person who made this fork, and where to find them.
private const val FORK_AUTHOR = "common_sensei"
private const val FORK_GITHUB = "https://github.com/comm0n-s3nse"
private const val FORK_X = "https://x.com/comm0n_s3nse"

// Keep in step with FORK.md and the libraries in app/build.gradle. BSD-3 and MIT require the
// copyright notice and license text to accompany the app, which is what the files are for.
private val CREDITS = listOf(
    Credit(
        "Kaiboard", "Kai (kaisoapbox)", "BSD 3-Clause",
        "This app is a fork of Kaiboard. The keyboard, its interface and the idea are Kai's.",
        "https://github.com/kaisoapbox/kaiboard", "kaiboard-bsd3.txt"
    ),
    Credit(
        "whisper.cpp", "Georgi Gerganov and the ggml authors", "MIT",
        "The speech recognition engine for Whisper models.",
        "https://github.com/ggerganov/whisper.cpp", "whispercpp-mit.txt"
    ),
    Credit(
        "Whisper", "OpenAI", "MIT",
        "The speech recognition model. The built-in model is Whisper tiny.en, converted for whisper.cpp.",
        "https://github.com/openai/whisper", "openai-whisper-mit.txt"
    ),
    Credit(
        "parakeet.cpp", "the parakeet.cpp authors", "MIT",
        "The engine for Parakeet models (.gguf files you add).",
        "https://github.com/mudler/parakeet.cpp", "parakeetcpp-mit.txt"
    ),
    Credit(
        "transcribe.cpp", "the transcribe.cpp authors", "MIT",
        "The engine for Canary and other .gguf models. Its license file also lists the third-party code it carries.",
        "https://github.com/handy-computer/transcribe.cpp", "transcribecpp-mit.txt"
    ),
    Credit(
        "ggml", "the ggml authors", "MIT",
        "The tensor library inside both engines.",
        "https://github.com/ggml-org/ggml", "ggml-mit.txt"
    ),
    Credit(
        "Android libraries", "Google and JetBrains", "Apache 2.0",
        "AndroidX, Jetpack Compose, Material 3 and its icons, Kotlin, kotlinx.coroutines, and Accompanist.",
        "https://www.apache.org/licenses/LICENSE-2.0", "apache-2.0.txt"
    ),
    Credit(
        "Canary 1B (a model you can add)", "NVIDIA, converted by handy-computer", "CC BY 4.0",
        "Not included in the app. Credit: \"Canary-1B-v2 by NVIDIA\", converted for transcribe.cpp.",
        "https://huggingface.co/handy-computer/canary-1b-v2-gguf"
    ),
    Credit(
        "Fluid Speech Mini (a model you can add)", "FluidVoice Labs", "CC BY 4.0",
        "Not included in the app. Credit: \"Fluid Speech Mini by FluidVoice\", built on NVIDIA Parakeet TDT 0.6B v3 (CC BY 4.0).",
        "https://huggingface.co/FluidVoice-Labs/fluid-speech-mini-gguf"
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.about_back))
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.about_summary),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item { VersionRow(version) }
            item { UpdateRow(version) }
            item { People() }
            items(CREDITS) { credit -> CreditItem(credit) }
        }
    }
}

// "Jargon 1.0 · release notes": the installed version, and what changed in it, on GitHub.
@Composable
private fun VersionRow(version: String) {
    val uriHandler = LocalUriHandler.current
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_version_name, stringResource(R.string.app_name), version)) },
        supportingContent = { Text(stringResource(R.string.about_release_notes)) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.NavigateNext, null) },
        modifier = Modifier.clickable { uriHandler.openUri("${Updates.RELEASES}/tag/v$version") }
    )
}

// Asks GitHub on demand; the result stays until the next check. Installing is a tap on the release page.
@Composable
private fun UpdateRow(version: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(context.getString(R.string.preference_file_key), Context.MODE_PRIVATE) }
    var checking by remember { mutableStateOf(false) }
    var newer by remember { mutableStateOf(Updates.available(prefs, version)) }
    var status by remember { mutableStateOf<String?>(null) }
    val checkedAt = prefs.getLong(Updates.AT_KEY, 0)
    ListItem(
        headlineContent = {
            Text(
                when {
                    newer != null -> stringResource(R.string.update_available, newer!!.tag.removePrefix("v"))
                    else -> stringResource(R.string.update_check)
                }
            )
        },
        supportingContent = {
            Text(
                status ?: when {
                    checking -> stringResource(R.string.update_checking)
                    newer != null -> stringResource(R.string.update_get)
                    checkedAt > 0 -> stringResource(R.string.update_none, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(checkedAt)))
                    else -> stringResource(R.string.update_never)
                }
            )
        },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.NavigateNext, null) },
        modifier = Modifier.clickable(enabled = !checking) {
            val n = newer
            if (n != null) {
                uriHandler.openUri(n.url)
            } else {
                checking = true
                status = null
                scope.launch {
                    try {
                        Updates.check(prefs)
                        newer = Updates.available(prefs, version)
                        if (newer == null) status = context.getString(R.string.update_none, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date()))
                    } catch (e: Exception) {
                        status = context.getString(R.string.update_failed, e.localizedMessage ?: "")
                    } finally {
                        checking = false
                    }
                }
            }
        }
    )
}

// The people behind the app, in the order the work happened.
@Composable
private fun People() {
    val uriHandler = LocalUriHandler.current
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_original)) },
            supportingContent = { Text("Kai (kaisoapbox) - " + stringResource(R.string.support_text)) },
            trailingContent = { Icon(Icons.AutoMirrored.Outlined.NavigateNext, null) },
            modifier = Modifier.clickable { uriHandler.openUri("https://kaisoapbox.com") }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_extended)) },
            supportingContent = { Text(FORK_AUTHOR) },
            trailingContent = {
                Row {
                    IconButton(onClick = { uriHandler.openUri(FORK_GITHUB) }) {
                        Icon(painterResource(R.drawable.ic_github), "GitHub")
                    }
                    IconButton(onClick = { uriHandler.openUri(FORK_X) }) {
                        Icon(painterResource(R.drawable.ic_x), "X")
                    }
                }
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_enhanced)) },
            supportingContent = { Text("Claude (Anthropic)") },
        )
    }
}

@Composable
private fun CreditItem(credit: Credit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
    val licenseText = remember(credit.licenseFile, expanded) {
        if (expanded && credit.licenseFile != null) {
            try {
                context.assets.open("licenses/${credit.licenseFile}").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }
    Column {
        ListItem(
            headlineContent = { Text(credit.name) },
            supportingContent = { Text("${credit.by} · ${credit.license}") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        )
        if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(credit.role, modifier = Modifier.padding(bottom = 4.dp))
                Row {
                    TextButton(onClick = { uriHandler.openUri(credit.url) }) {
                        Text(stringResource(R.string.about_website))
                    }
                }
                if (licenseText.isNotEmpty()) {
                    SelectionContainer {
                        Text(licenseText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
