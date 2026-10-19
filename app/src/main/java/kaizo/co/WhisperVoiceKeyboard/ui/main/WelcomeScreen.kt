package kaizo.co.WhisperVoiceKeyboard.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kaizo.co.WhisperVoiceKeyboard.R
import kaizo.co.WhisperVoiceKeyboard.models.ModelStore
import kotlinx.coroutines.delay

/**
 * The first screen of a copy that ships without a speech model (the F-Droid build): a short
 * welcome, then the model list to download one from. The keyboard cannot type until a model is
 * installed, so this stays until one is, then offers Continue.
 */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ModelStore(context) }
    var hasModel by remember { mutableStateOf(store.list().isNotEmpty()) }
    // downloads finish in the system's download manager; look again now and then
    LaunchedEffect(Unit) {
        while (!hasModel) {
            delay(1000)
            hasModel = store.list().isNotEmpty()
        }
    }
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.welcome_title, stringResource(R.string.app_name)),
                    fontSize = 28.sp, fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.welcome_private), modifier = Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.welcome_no_model), modifier = Modifier.padding(top = 12.dp))
            }
            ModelSettings()
            if (hasModel) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) { Text(stringResource(R.string.welcome_continue)) }
            }
        }
    }
}
