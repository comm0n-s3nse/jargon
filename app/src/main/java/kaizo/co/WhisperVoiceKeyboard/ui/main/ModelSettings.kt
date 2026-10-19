package kaizo.co.WhisperVoiceKeyboard.ui.main

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kaizo.co.WhisperVoiceKeyboard.R
import kaizo.co.WhisperVoiceKeyboard.models.ACTIVE_MODEL_KEY
import kaizo.co.WhisperVoiceKeyboard.models.CATALOG
import kaizo.co.WhisperVoiceKeyboard.models.CatalogModel
import kaizo.co.WhisperVoiceKeyboard.models.CpuFeatures
import kaizo.co.WhisperVoiceKeyboard.models.LANGUAGES
import kaizo.co.WhisperVoiceKeyboard.models.rememberTranslateTarget
import kaizo.co.WhisperVoiceKeyboard.models.LANGUAGE_KEY
import kaizo.co.WhisperVoiceKeyboard.models.ModelDownloader
import kaizo.co.WhisperVoiceKeyboard.models.ModelEntry
import kaizo.co.WhisperVoiceKeyboard.models.ModelEngine
import kaizo.co.WhisperVoiceKeyboard.models.TRANSLATE_KEY
import kaizo.co.WhisperVoiceKeyboard.models.ModelImportException
import kaizo.co.WhisperVoiceKeyboard.models.ModelStore
import kaizo.co.WhisperVoiceKeyboard.models.catalogFor
import kaizo.co.WhisperVoiceKeyboard.models.downloadSizeLabel
import kaizo.co.WhisperVoiceKeyboard.models.ramLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// A text button keeps 12dp of padding inside its edges, which left Download and Remove about 2 mm
// short of the plain "built-in" label above them. Dropping the end padding lines their text up.
private val FlushEnd = PaddingValues(start = 12.dp, top = 8.dp, end = 0.dp, bottom = 8.dp)

/**
 * Which speech model the keyboard runs: the recommended ones (installed or downloadable), any the
 * user added by hand, the spoken language, and adding a file.
 */
@Composable
fun ModelSettings() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key), Context.MODE_PRIVATE
        )
    }
    val store = remember { ModelStore(context) }
    val downloader = remember { ModelDownloader(context, store) }
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf(store.list()) }
    var activeId by remember { mutableStateOf(store.active(prefs).id) }
    var language by remember { mutableStateOf(prefs.getString(LANGUAGE_KEY, "en") ?: "en") }
    var menuOpen by remember { mutableStateOf(false) }
    var translateTo by remember { mutableStateOf(prefs.getString(TRANSLATE_KEY, "") ?: "") }
    var translateMenuOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var importProgress by remember { mutableStateOf<String?>(null) }
    var confirmAdd by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<CatalogModel?>(null) }
    var confirmInternet by remember { mutableStateOf<CatalogModel?>(null) }
    // what to show on a model's row while it downloads, or after it failed
    var rowText by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    val finishing = remember { mutableSetOf<String>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val added = store.import(uri) { copied, total ->
                    importProgress = if (total > 0) "Copying… ${copied * 100 / total}%"
                    else "Copying… ${copied shr 20} MB"
                }
                models = store.list()
                note = "Added ${added.label}. Select it to use it."
            } catch (e: ModelImportException) {
                note = e.message
            } catch (e: Exception) {
                note = "Couldn't add that file: ${e.localizedMessage}"
            } finally {
                importProgress = null
            }
        }
    }

    // Downloads run in the system's download manager, so they carry on without this screen. While
    // it is open, follow them and, when one arrives, check it and move it into place.
    LaunchedEffect(Unit) {
        while (true) {
            val text = mutableMapOf<String, String>()
            val running = mutableSetOf<String>()
            for (m in CATALOG) {
                if (!m.downloadable || models.any { it.fileName == m.file } || m.id in finishing) continue
                when (val st = downloader.poll(m)) {
                    is ModelDownloader.State.Idle -> {}
                    is ModelDownloader.State.Running -> {
                        text[m.id] = context.getString(R.string.model_downloading, st.percent)
                        running += m.id
                    }
                    is ModelDownloader.State.Failed -> {
                        downloader.cancel(m)
                        note = context.getString(R.string.model_download_failed, m.name, st.reason)
                    }
                    is ModelDownloader.State.Done -> {
                        finishing += m.id
                        scope.launch {
                            try {
                                downloader.finish(m) { copied, total ->
                                    rowText = rowText + (m.id to context.getString(
                                        R.string.model_checking, if (total > 0) (copied * 100 / total).toInt() else 0
                                    ))
                                }
                                models = store.list()
                                note = context.getString(R.string.model_download_done, m.name)
                            } catch (e: ModelImportException) {
                                note = e.message
                            } catch (e: Exception) {
                                note = context.getString(R.string.model_download_failed, m.name, e.localizedMessage ?: "")
                            } finally {
                                finishing -= m.id
                                rowText = rowText - m.id
                                busy = busy - m.id
                            }
                        }
                    }
                }
            }
            for (id in finishing) text[id] = rowText[id] ?: context.getString(R.string.model_checking, 0)
            rowText = text
            busy = running + finishing
            delay(700)
        }
    }

    val active = models.firstOrNull { it.id == activeId } ?: models.first()
    val installed = models.associateBy { it.fileName }
    // A translating model (Canary) cannot detect what is spoken, so "auto" is not offered for it, and
    // both lists show only the languages the model's own file says it supports.
    val spoken = LANGUAGES.filter { (code, _) ->
        if (active.canTranslate && code == "auto") false
        else active.languages.isEmpty() || code == "auto" || code in active.languages
    }
    val written = LANGUAGES.filter { (code, _) ->
        code != "auto" && (active.languages.isEmpty() || code in active.languages)
    }

    fun select(model: ModelEntry) {
        prefs.edit().putString(ACTIVE_MODEL_KEY, model.id).apply()
        activeId = model.id
        note = context.getString(R.string.model_switch_note)
    }

    fun remove(model: ModelEntry) {
        if (model.id == active.id) {
            prefs.edit().remove(ACTIVE_MODEL_KEY).apply()
            activeId = models.first { it.builtIn }.id
        }
        store.remove(model)
        models = store.list()
    }

    Column {
        Text(
            stringResource(R.string.model_header),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp)
        )

        // the recommended models, installed or not
        CATALOG.forEach { c ->
            val entry = installed[c.file]
            val supported = !c.needsGguf || CpuFeatures.supportsGguf()
            val headline = "${c.name} · ${c.by}"
            if (entry != null) {
                ListItem(
                    headlineContent = { Text(headline) },
                    supportingContent = {
                        Text(
                            if (supported) "${ramLabel(c.ramMb)} · ${c.summary}"
                            else stringResource(R.string.model_unsupported)
                        )
                    },
                    leadingContent = { RadioButton(selected = entry.id == active.id, onClick = null) },
                    trailingContent = {
                        if (entry.builtIn) {
                            Text(stringResource(R.string.model_built_in), fontSize = 14.sp)
                        } else {
                            TextButton(onClick = { remove(entry) }, contentPadding = FlushEnd) { Text(stringResource(R.string.model_remove)) }
                        }
                    },
                    modifier = Modifier.clickable(enabled = supported) { select(entry) }
                )
            } else {
                ListItem(
                    headlineContent = { Text(headline) },
                    supportingContent = {
                        Text(
                            rowText[c.id] ?: when {
                                !c.downloadable -> c.summary
                                !supported -> stringResource(R.string.model_unsupported)
                                else -> "${ramLabel(c.ramMb)} · ${c.summary}"
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Download, null) },
                    trailingContent = {
                        when {
                            c.id in busy && c.id !in finishing ->
                                TextButton(onClick = {
                                    downloader.cancel(c)
                                    rowText = rowText - c.id
                                }, contentPadding = FlushEnd) { Text(stringResource(R.string.model_cancel)) }
                            c.id in finishing -> {}
                            !c.downloadable ->
                                Text(stringResource(R.string.model_coming_soon), fontSize = 14.sp)
                            !supported -> {}
                            else -> TextButton(onClick = { confirm = c }, contentPadding = FlushEnd) {
                                Text(stringResource(R.string.model_download))
                            }
                        }
                    },
                    modifier = Modifier.clickable(enabled = c.downloadable && supported && c.id !in busy) {
                        confirm = c
                    }
                )
            }
        }

        // models added by hand that are not one of the recommended ones
        models.filter { catalogFor(it.fileName) == null }.forEach { model ->
            val supported = store.runnable(model)
            ListItem(
                headlineContent = { Text(model.label) },
                supportingContent = {
                    Text(
                        "${model.sizeBytes shr 20} MB · " + when {
                            !supported -> stringResource(R.string.model_unsupported)
                            model.engine == ModelEngine.PARAKEET_CPP -> stringResource(R.string.model_parakeet)
                            model.engine == ModelEngine.TRANSCRIBE_CPP ->
                                model.arch?.replaceFirstChar { it.uppercase() } ?: "GGUF"
                            model.englishOnly -> stringResource(R.string.model_english_only)
                            else -> stringResource(R.string.model_multilingual)
                        }
                    )
                },
                leadingContent = { RadioButton(selected = model.id == active.id, onClick = null) },
                trailingContent = {
                    if (!model.builtIn) {
                        TextButton(onClick = { remove(model) }, contentPadding = FlushEnd) { Text(stringResource(R.string.model_remove)) }
                    }
                },
                modifier = Modifier.clickable(enabled = supported) { select(model) }
            )
        }

        ListItem(
            headlineContent = {
                Text(stringResource(if (active.canTranslate) R.string.language_spoken else R.string.language_option))
            },
            supportingContent = {
                Text(
                    when {
                        active.engine == ModelEngine.PARAKEET_CPP -> stringResource(R.string.language_model_decides)
                        active.englishOnly -> stringResource(R.string.language_english_only)
                        // "auto" means English for a model that cannot detect the language
                        else -> LANGUAGES.firstOrNull { it.first == (if (active.canTranslate && language == "auto") "en" else language) }?.second ?: language
                    }
                )
            },
            leadingContent = { Icon(Icons.Outlined.Language, null) },
            trailingContent = {
                Box {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        spoken.forEach { (code, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                language = code
                                prefs.edit().putString(LANGUAGE_KEY, code).apply()
                                menuOpen = false
                            })
                        }
                    }
                }
            },
            modifier = Modifier.clickable(
                enabled = !active.englishOnly && active.engine != ModelEngine.PARAKEET_CPP
            ) { menuOpen = true }
        )

        // only for models that can translate speech (Canary): which language the words come out in
        if (active.canTranslate) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.translate_option)) },
                supportingContent = {
                    Text(
                        LANGUAGES.firstOrNull { it.first == translateTo && translateTo.isNotEmpty() }?.second
                            ?: stringResource(R.string.translate_off)
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Translate, null) },
                trailingContent = {
                    Box {
                        DropdownMenu(expanded = translateMenuOpen, onDismissRequest = { translateMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.translate_off)) },
                                onClick = {
                                    translateTo = ""
                                    prefs.edit().remove(TRANSLATE_KEY).apply()
                                    translateMenuOpen = false
                                }
                            )
                            written.forEach { (code, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    translateTo = code
                                    prefs.edit().putString(TRANSLATE_KEY, code).apply()
                                    rememberTranslateTarget(prefs, code)
                                    translateMenuOpen = false
                                })
                            }
                        }
                    }
                },
                modifier = Modifier.clickable { translateMenuOpen = true }
            )
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.model_add)) },
            // slim on purpose: adding a file is for advanced use, and the details are in the dialog
            supportingContent = importProgress?.let { { Text(it) } },
            leadingContent = { Icon(Icons.Outlined.Add, null) },
            modifier = Modifier.clickable(enabled = importProgress == null) { confirmAdd = true }
        )
        note?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
    }

    if (confirmAdd) {
        AlertDialog(
            onDismissRequest = { confirmAdd = false },
            title = { Text(stringResource(R.string.model_add)) },
            text = { Text(stringResource(R.string.model_add_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAdd = false
                    picker.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.model_add_choose)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAdd = false }) { Text(stringResource(R.string.model_cancel)) }
            }
        )
    }

    // step 1: what the model is
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.name) },
            text = {
                Column {
                    Text(c.about)
                    Text(
                        stringResource(R.string.download_facts, downloadSizeLabel(c.sizeBytes), ramLabel(c.ramMb).removeSuffix(" RAM")),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    c.note?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                    Text(
                        stringResource(R.string.download_credit, c.credit),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    confirmInternet = c
                }) { Text(stringResource(R.string.model_download)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.model_cancel)) }
            }
        )
    }

    // step 2: the one place the app asks to use the internet, before anything is sent
    confirmInternet?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmInternet = null },
            title = { Text(stringResource(R.string.net_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.net_body, c.name, downloadSizeLabel(c.sizeBytes), Uri.parse(c.url).host ?: ""
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmInternet = null
                    note = null
                    try {
                        downloader.start(c)
                    } catch (e: Exception) {
                        note = context.getString(R.string.model_download_failed, c.name, e.localizedMessage ?: "")
                    }
                }) { Text(stringResource(R.string.net_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmInternet = null }) { Text(stringResource(R.string.model_cancel)) }
            }
        )
    }
}
