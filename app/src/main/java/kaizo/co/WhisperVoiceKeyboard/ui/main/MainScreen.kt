package kaizo.co.WhisperVoiceKeyboard.ui.main

import kaizo.co.WhisperVoiceKeyboard.models.ModelStore
import androidx.compose.material.icons.outlined.Info
import kaizo.co.WhisperVoiceKeyboard.SpokenText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Palette
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import kaizo.co.WhisperVoiceKeyboard.tune.Threads
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kaizo.co.WhisperVoiceKeyboard.R

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    var showAbout by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
        return
    }
    if (showAppearance) {
        BackHandler { showAppearance = false }
        AppearanceScreen(onBack = { showAppearance = false })
        return
    }
    MainScreen(
        canTranscribe = viewModel.canTranscribe,
        isRecording = viewModel.isRecording,
        messageLog = viewModel.dataLog,
        onBenchmarkTapped = viewModel::benchmark,
        onRecordTapped = viewModel::toggleRecord,
        onAboutTapped = { showAbout = true },
        onAppearanceTapped = { showAppearance = true },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    canTranscribe: Boolean,
    isRecording: Boolean,
    messageLog: String,
    onBenchmarkTapped: () -> Unit,
    onRecordTapped: () -> Unit,
    onAboutTapped: () -> Unit,
    onAppearanceTapped: () -> Unit,
) {
    val sharedPref = LocalContext.current.getSharedPreferences(
        stringResource(R.string.preference_file_key), Context.MODE_PRIVATE
    )
    val maxThreads = Runtime.getRuntime().availableProcessors()
    val threadsStr = stringResource(R.string.num_threads)
    val casualMode = stringResource(R.string.casual_mode)
    val pauseMediaStr = stringResource(R.string.pause_media)
    val appContext = LocalContext.current
    val activeModelId = remember { ModelStore(appContext).active(sharedPref).id }
    var nThreads by remember {
        mutableFloatStateOf(Threads.effective(sharedPref, threadsStr, maxThreads, activeModelId).toFloat())
    }
    var threadsManual by remember { mutableStateOf(sharedPref.contains(threadsStr)) }
    var tunedThreads by remember { mutableIntStateOf(Threads.tuned(sharedPref, activeModelId)) }
    var lowercase by remember {
        mutableStateOf(
            sharedPref.getBoolean(casualMode, false)
        )
    }
    var pauseMedia by remember {
        mutableStateOf(
            sharedPref.getBoolean(pauseMediaStr, false)
        )
    }

    val setup = rememberSetupState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    stringResource(R.string.settings),
                    fontSize = 30.sp, fontWeight = FontWeight.Bold
                )
            })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding)
        ) {
            // Setup shows only what still needs doing, and disappears once all of it is done.
            // About then leads the page, followed by the system keyboard list.
            if (setup.anyMissing) item { SectionHeader(stringResource(R.string.setup_header)) }
            if (!setup.keyboardEnabled) item { InputMethodButton(enabled = false) }
            if (!setup.micGranted) item { PermissionButton() }
            if (!setup.hapticsOn) item { HapticsButton() }
            item { AboutButton(onAboutTapped) }
            if (setup.keyboardEnabled) item { InputMethodButton(enabled = true) }
            item { ModelSettings() }
            item { SectionHeader(stringResource(R.string.behavior_header)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pause_media_option)) },
                    leadingContent = { Icon(Icons.Outlined.Pause, null) },
                    supportingContent = {
                        Text(stringResource(R.string.pause_media_description))
                    },
                    trailingContent = {
                        Switch(checked = pauseMedia, onCheckedChange = {
                            pauseMedia = it
                            with(sharedPref.edit()) {
                                putBoolean(pauseMediaStr, it)
                                apply()
                            }
                        })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.casual_option)) },
                    leadingContent = { Icon(Icons.Outlined.Mood, null) },
                    supportingContent = {
                        Text(stringResource(R.string.casual_description))
                    },
                    trailingContent = {
                        Switch(checked = lowercase, onCheckedChange = {
                            lowercase = it
                            with(sharedPref.edit()) {
                                putBoolean(casualMode, it)
                                apply()
                            }
                        })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { SpokenPunctuationItem(sharedPref) }
            item { SpokenEmojiItem(sharedPref) }
            item { AppearanceButton(onAppearanceTapped) }
            item { SectionHeader(stringResource(R.string.advanced_header), bp = 4.dp) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.num_threads_option)) },
                    leadingContent = { Icon(Icons.Outlined.Memory, null) },
                    supportingContent = {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Slider(
                                    value = nThreads,
                                    onValueChange = { nThreads = it },
                                    onValueChangeFinished = {
                                        with(sharedPref.edit()) {
                                            putInt(threadsStr, nThreads.toInt())
                                            apply()
                                        }
                                        threadsManual = true
                                    },
                                    // subtract 2 bc the start and end count as steps
                                    steps = maxThreads - 2,
                                    valueRange = 1f..maxThreads.toFloat(),
                                    modifier = Modifier.weight(1f, true)
                                )
                                Text(
                                    nThreads.toInt().toString(),
                                    modifier = Modifier.padding(4.dp),
                                    fontSize = 16.sp,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    when {
                                        threadsManual -> stringResource(R.string.threads_manual)
                                        tunedThreads > 0 ->
                                            stringResource(R.string.threads_auto, tunedThreads)
                                        else -> stringResource(R.string.threads_pending)
                                    },
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f, true)
                                )
                                TextButton(onClick = {
                                    with(sharedPref.edit()) {
                                        remove(threadsStr)
                                        Threads.forgetTuned(sharedPref, this)
                                        remove(Threads.TUNING_STARTED)
                                        apply()
                                    }
                                    threadsManual = false
                                    tunedThreads = 0
                                    nThreads = Threads.effective(sharedPref, threadsStr, maxThreads, activeModelId).toFloat()
                                }) { Text(stringResource(R.string.threads_reset)) }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp),
                )
            }
            item { SectionHeader(stringResource(R.string.test_header)) }
            item {
                BenchmarkButton(enabled = canTranscribe, onClick = onBenchmarkTapped)
            }
            item {
                RecordButton(
                    enabled = canTranscribe, isRecording = isRecording, onClick = onRecordTapped
                )
            }
            item { MessageLog(messageLog) }
        }
    }
}

@Composable
private fun SectionHeader(header: String, tp: Dp = 16.dp, bp: Dp = 0.dp) {
    Text(
        header,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = tp, bottom = bp, start = 16.dp, end = 0.dp)
    )
}


@Composable
private fun MessageLog(log: String) {
    SelectionContainer(modifier = Modifier.padding(16.dp)) { Text(log) }
}

/** What the page checks again every time it comes back to the front (after a trip to system settings). */
private class SetupState(val keyboardEnabled: Boolean, val micGranted: Boolean, val hapticsOn: Boolean) {
    val anyMissing get() = !keyboardEnabled || !micGranted || !hapticsOn
}

@Composable
private fun rememberSetupState(): SetupState {
    val context = LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    fun read() = SetupState(
        keyboardEnabled = try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.enabledInputMethodList.any { it.packageName == context.packageName }
        } catch (e: Exception) {
            true // unknown: say nothing rather than nag
        },
        micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        // the buzz on record, hold and cancel follows the system's touch feedback switch
        hapticsOn = try {
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        } catch (e: Exception) {
            true
        },
    )
    var state by remember { mutableStateOf(read()) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state = read()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
private fun HapticsButton() {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(stringResource(R.string.haptics_button)) },
        leadingContent = { Icon(Icons.Outlined.Vibration, null) },
        supportingContent = { Text(stringResource(R.string.haptics_description)) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.NavigateNext, null) },
        modifier = Modifier.clickable(onClick = {
            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        })
    )
}

@Composable
private fun InputMethodButton(enabled: Boolean) {
    val context = LocalContext.current
    ListItem(
        headlineContent = {
            Text(stringResource(if (enabled) R.string.keyboards_button else R.string.input_method_button))
        },
        leadingContent = {
            Icon(
                Icons.Outlined.Keyboard, null
            )
        },
        supportingContent = if (enabled) null else {
            { Text(stringResource(R.string.input_method_description)) }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                stringResource(R.string.input_method_button)
            )
        },
        modifier = Modifier.clickable(onClick = {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
    )
}

@Composable
private fun AppearanceButton(onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.appearance_title)) },
        leadingContent = { Icon(Icons.Outlined.Palette, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.NavigateNext, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SpokenPunctuationItem(prefs: android.content.SharedPreferences) {
    val key = stringResource(R.string.spoken_punctuation)
    var on by remember { mutableStateOf(prefs.getBoolean(key, true)) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.spoken_punctuation_option)) },
        supportingContent = { Text(stringResource(R.string.spoken_punctuation_description)) },
        leadingContent = { Icon(Icons.Outlined.QuestionMark, null) },
        trailingContent = {
            Switch(checked = on, onCheckedChange = {
                on = it
                prefs.edit().putBoolean(key, it).apply()
            })
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SpokenEmojiItem(prefs: android.content.SharedPreferences) {
    val key = stringResource(R.string.spoken_emoji)
    var on by remember { mutableStateOf(prefs.getBoolean(key, true)) }
    var list by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.spoken_emoji_option)) },
        supportingContent = { Text(stringResource(R.string.spoken_emoji_description)) },
        leadingContent = { Icon(Icons.Outlined.Mood, null) },
        trailingContent = {
            Switch(checked = on, onCheckedChange = {
                on = it
                prefs.edit().putBoolean(key, it).apply()
            })
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { list = true }
    )
    if (list) {
        AlertDialog(
            onDismissRequest = { list = false },
            title = { Text(stringResource(R.string.spoken_emoji_list_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SpokenText.EMOJI.forEach { (phrase, emoji) ->
                        Text("$emoji  “$phrase”", modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { list = false }) { Text(stringResource(R.string.net_ok)) }
            }
        )
    }
}

@Composable
private fun AboutButton(onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_button)) },
        leadingContent = { Icon(Icons.Outlined.Info, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.NavigateNext, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionButton() {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.permission_button)) },
        leadingContent = {
            Icon(
                Icons.Outlined.Mic, null
            )
        },
        supportingContent = {
            if (micPermissionState.status.isGranted) {
                Text(stringResource(R.string.permission_granted))
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                stringResource(R.string.input_method_button)
            )
        },
        modifier = Modifier.clickable(onClick = {
            if (!micPermissionState.status.isGranted) {
                micPermissionState.launchPermissionRequest()
            }
        })
    )
}


@Composable
private fun BenchmarkButton(enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(
                Icons.Outlined.Speed, stringResource(R.string.start_recording)
            )
        },
        headlineContent = { Text(stringResource(R.string.benchmark)) },
        supportingContent = { Text(stringResource(R.string.benchmark_description)) },
        modifier = Modifier.clickable(onClick = onClick, enabled = enabled)
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RecordButton(enabled: Boolean, isRecording: Boolean, onClick: () -> Unit) {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { granted ->
            if (granted) {
                onClick()
            }
        })
    ListItem(
        leadingContent = {
            Icon(
                if (isRecording) {
                    Icons.Outlined.Stop
                } else {
                    Icons.Outlined.PlayArrow
                }, stringResource(R.string.start_recording)
            )
        },
        headlineContent = {
            Text(
                if (isRecording) {
                    stringResource(R.string.stop_test)
                } else {
                    stringResource(R.string.start_test)
                }
            )
        },
        supportingContent = { Text(stringResource(R.string.test_description)) },
        modifier = Modifier.clickable(onClick = {
            if (micPermissionState.status.isGranted) {
                onClick()
            } else {
                micPermissionState.launchPermissionRequest()
            }
        }, enabled = enabled)
    )
}