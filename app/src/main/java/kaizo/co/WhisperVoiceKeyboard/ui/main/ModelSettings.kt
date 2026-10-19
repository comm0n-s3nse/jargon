package kaizo.co.WhisperVoiceKeyboard.ui.main

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kaizo.co.WhisperVoiceKeyboard.models.LANGUAGES
import kaizo.co.WhisperVoiceKeyboard.models.LANGUAGE_KEY
import kaizo.co.WhisperVoiceKeyboard.models.ModelFormat
import kaizo.co.WhisperVoiceKeyboard.models.ModelImportException
import kaizo.co.WhisperVoiceKeyboard.models.ModelStore
import kotlinx.coroutines.launch

/** Which speech model the keyboard runs, adding or removing models, and the spoken language. */
@Composable
fun ModelSettings() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key), Context.MODE_PRIVATE
        )
    }
    val store = remember { ModelStore(context) }
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf(store.list()) }
    var activeId by remember { mutableStateOf(store.active(prefs).id) }
    var language by remember { mutableStateOf(prefs.getString(LANGUAGE_KEY, "en") ?: "en") }
    var menuOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val added = store.import(uri) { copied, total ->
                    progress = if (total > 0) "Copying… ${copied * 100 / total}%"
                    else "Copying… ${copied shr 20} MB"
                }
                models = store.list()
                note = "Added ${added.label}. Select it to use it."
            } catch (e: ModelImportException) {
                note = e.message
            } catch (e: Exception) {
                note = "Couldn't add that file: ${e.localizedMessage}"
            } finally {
                progress = null
            }
        }
    }
    val active = models.firstOrNull { it.id == activeId } ?: models.first()

    Column {
        Text(
            stringResource(R.string.model_header),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp)
        )
        models.forEach { model ->
            ListItem(
                headlineContent = {
                    Text(
                        model.label +
                            if (model.builtIn) " (${stringResource(R.string.model_built_in)})" else ""
                    )
                },
                supportingContent = {
                    Text(
                        "${model.sizeBytes shr 20} MB · " + when {
                            !store.runnable(model) -> stringResource(R.string.model_unsupported)
                            model.format == ModelFormat.GGUF -> stringResource(R.string.model_parakeet)
                            model.englishOnly -> stringResource(R.string.model_english_only)
                            else -> stringResource(R.string.model_multilingual)
                        }
                    )
                },
                leadingContent = { RadioButton(selected = model.id == active.id, onClick = null) },
                trailingContent = {
                    if (!model.builtIn) {
                        TextButton(onClick = {
                            if (model.id == active.id) {
                                prefs.edit().remove(ACTIVE_MODEL_KEY).apply()
                                activeId = models.first { it.builtIn }.id
                            }
                            store.remove(model)
                            models = store.list()
                        }) { Text(stringResource(R.string.model_remove)) }
                    }
                },
                modifier = Modifier.clickable(enabled = store.runnable(model)) {
                    prefs.edit().putString(ACTIVE_MODEL_KEY, model.id).apply()
                    activeId = model.id
                    note = context.getString(R.string.model_switch_note)
                }
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.model_add)) },
            supportingContent = { Text(progress ?: stringResource(R.string.model_add_hint)) },
            leadingContent = { Icon(Icons.Outlined.Add, null) },
            modifier = Modifier.clickable(enabled = progress == null) {
                picker.launch(arrayOf("*/*"))
            }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.language_option)) },
            supportingContent = {
                Text(
                    when {
                        active.format == ModelFormat.GGUF -> stringResource(R.string.language_model_decides)
                        active.englishOnly -> stringResource(R.string.language_english_only)
                        else -> LANGUAGES.firstOrNull { it.first == language }?.second ?: language
                    }
                )
            },
            leadingContent = { Icon(Icons.Outlined.Language, null) },
            trailingContent = {
                Box {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        LANGUAGES.forEach { (code, label) ->
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
                enabled = !active.englishOnly && active.format != ModelFormat.GGUF
            ) { menuOpen = true }
        )
        note?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
    }
}
