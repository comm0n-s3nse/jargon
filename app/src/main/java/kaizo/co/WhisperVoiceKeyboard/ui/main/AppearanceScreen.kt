package kaizo.co.WhisperVoiceKeyboard.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kaizo.co.WhisperVoiceKeyboard.R

const val ENTER_HINT_KEY = "enter_hint" // the small gear on the enter key that says "hold me for settings"

// How the keyboard looks. One choice for now; the key profile (Material / Charge) goes here.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences(
        stringResource(R.string.preference_file_key), Context.MODE_PRIVATE
    )
    var hint by remember { mutableStateOf(prefs.getBoolean(ENTER_HINT_KEY, true)) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.about_back))
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.enter_hint_option)) },
                supportingContent = { Text(stringResource(R.string.enter_hint_description)) },
                leadingContent = { Icon(Icons.Outlined.Settings, null) },
                trailingContent = {
                    Switch(checked = hint, onCheckedChange = {
                        hint = it
                        prefs.edit().putBoolean(ENTER_HINT_KEY, it).apply()
                    })
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.coming_soon), fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
