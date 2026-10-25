package kaizo.co.WhisperVoiceKeyboard.ui.main

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kaizo.co.WhisperVoiceKeyboard.R
import kaizo.co.WhisperVoiceKeyboard.Tutorial
import kaizo.co.WhisperVoiceKeyboard.Tutorial.Step
import kotlinx.coroutines.delay

const val TUTORIAL_DONE_KEY = "tutorial_done"

/**
 * A black page with one instruction at a time and a text box for the keyboard to type into. The
 * keyboard itself is the picture: each step is finished by doing the gesture, which the keys
 * report to [Tutorial]. The last step, holding enter, opens settings and ends it.
 */
@Composable
fun TutorialScreen(onDone: () -> Unit, onLeave: () -> Unit) {
    val context = LocalContext.current
    val step = Tutorial.step
    val focus = remember { FocusRequester() }
    var typed by remember { mutableStateOf("") }
    var ours by remember { mutableStateOf(currentKeyboardIsOurs(context)) }

    // the keyboard appears when the box has focus; after the flick step, watch for it coming back
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(step) {
        if (step == Step.DONE) {
            context.getSharedPreferences(context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)
                .edit().putBoolean(TUTORIAL_DONE_KEY, true).apply()
            onDone()
            return@LaunchedEffect
        }
        // after the flick the other keyboard is up; watch for Jargon coming back
        while (true) {
            val now = currentKeyboardIsOurs(context)
            if (now != ours) {
                ours = now
                if (now) focus.requestFocus()
            }
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Step.entries.filter { it != Step.DONE }.forEach { s ->
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (s.ordinal <= step.ordinal) Color.White else Color(0xFF444444))
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLeave) { Text(stringResource(R.string.tutorial_leave), color = Color(0xFFAAAAAA)) }
        }
        Spacer(Modifier.height(32.dp))
        val (title, hint) = when {
            step == Step.SETTINGS && !ours -> R.string.tut_flick_back to R.string.tut_flick_back_hint
            else -> when (step) {
                Step.TAP -> R.string.tut_tap to R.string.tut_tap_hint
            Step.TAP_CANCEL -> R.string.tut_tap_cancel to R.string.tut_tap_cancel_hint
            Step.HOLD -> R.string.tut_hold to R.string.tut_hold_hint
            Step.HOLD_CANCEL -> R.string.tut_hold_cancel to R.string.tut_hold_cancel_hint
            Step.UNDO -> R.string.tut_undo to R.string.tut_undo_hint
            Step.FLICK -> R.string.tut_flick to R.string.tut_flick_hint
            Step.SETTINGS, Step.DONE -> R.string.tut_settings to R.string.tut_settings_hint
            }
        }
        Text(stringResource(title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(hint), color = Color(0xFFBBBBBB), fontSize = 17.sp, lineHeight = 24.sp)
        if (!ours) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = { showKeyboardPicker(context) }) {
                Text(stringResource(if (step.ordinal >= Step.SETTINGS.ordinal) R.string.tut_bring_back else R.string.tut_switch_to))
            }
        }
        Spacer(Modifier.weight(1f))
        // the box only when the step types into it; on the others it was taken for the task
        val typing = step == Step.TAP || step == Step.HOLD || step == Step.TAP_CANCEL || step == Step.UNDO
        if (typing) OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            placeholder = { Text(stringResource(R.string.tut_box), color = Color(0xFF777777)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            minLines = 3,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (typing && typed.isNotEmpty()) TextButton(onClick = { typed = "" }) { Text(stringResource(R.string.tut_clear)) }
        }
    }
}

private fun currentKeyboardIsOurs(context: Context): Boolean =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.startsWith(context.packageName + "/") == true

private fun showKeyboardPicker(context: Context) {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
}
