package kaizo.co.WhisperVoiceKeyboard.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kaizo.co.WhisperVoiceKeyboard.R

const val VOCABULARY_KEY = "vocabulary" // one term per line

/**
 * Names and terms the models should know: a person's name, a product, a place. Whisper hears
 * them as a prompt; transcribe.cpp hands them to the families that take a vocabulary. Stored as
 * one term per line, in the order given, which is also the order of priority.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(context.getString(R.string.preference_file_key), Context.MODE_PRIVATE) }
    var terms by remember { mutableStateOf(prefs.getString(VOCABULARY_KEY, null)?.lines()?.filter { it.isNotBlank() }.orEmpty()) }
    var draft by remember { mutableStateOf("") }
    fun save(list: List<String>) {
        terms = list
        prefs.edit().putString(VOCABULARY_KEY, list.joinToString("\n")).apply()
    }
    fun add() {
        val t = draft.trim()
        if (t.isNotEmpty() && terms.none { it.equals(t, ignoreCase = true) }) save(terms + t)
        draft = ""
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vocabulary_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.about_back))
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(stringResource(R.string.vocabulary_description), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.vocabulary_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { add() }, enabled = draft.isNotBlank()) { Text(stringResource(R.string.vocabulary_add)) }
            }
            LazyColumn {
                items(terms) { term ->
                    ListItem(
                        headlineContent = { Text(term) },
                        trailingContent = {
                            IconButton(onClick = { save(terms - term) }) {
                                Icon(Icons.Outlined.Clear, stringResource(R.string.vocabulary_remove))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
