package kaizo.co.WhisperVoiceKeyboard

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import kotlin.math.abs
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kaizo.co.WhisperVoiceKeyboard.ui.theme.KaiboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

// a press shorter than this is a tap, longer is a hold (word-delete, press-to-talk)
private const val HOLD_GATE_MS = 240L
private const val DOUBLE_TAP_MS = 320L // a second tap within this opens the agent popup instead
// the "live" key: how fast it fills when a hold arms or a tap-recording starts, and empties after
private const val FILL_IN_MS = 120
private const val FILL_OUT_MS = 160
private const val PULSE_MS = 150 // the one edge pulse at arming
private const val ROLL_DP = 28 // how far an icon travels when one rolls out and another rolls in

// Sizes that matter to a thumb are in millimeters, not dp. A phone's "display size" setting
// rescales dp, which shrank the keyboard and pushed it against the system nav buttons.
private const val BUTTON_HEIGHT_MM = 6.5f
private const val DEAD_SPACE_MM = 2.7f
private const val UNDO_DRAG_MM = 11f
private const val UNDO_DRAG_MAX_MM = 22f
private const val CANCEL_DRAG_MM = 10f

// backspace, enter and the cancel key share one width, so they cannot drift apart
private const val SIDE_KEY_WIDTH_MM = 9f

// how far to pull enter up before releasing opens settings
private const val LANGUAGE_DRAG_MM = 8f // slide left on the mic: quick language list
private const val KEYBOARD_FLICK_MM = 8f // flick up on enter: previous keyboard
private const val HOLD_FOR_SETTINGS_MS = 300L // a still finger on enter this long opens settings
private const val HINT_SIZE_DP = 10
private const val HINT_OFFSET_X_DP = 9 // from the icon's corner out toward the key's corner
private const val HINT_OFFSET_Y_DP = 6
private const val HINT_ALPHA = 0.55f

private val CancelRed = Color(0xFFD32F2F)
private val UndoRed = CancelRed // the backspace key once an undo is armed: the same red as cancel
private val LiveBlue = Color(0xFF4F7DF3) // the mic key while it is live (armed hold, or recording)

@Composable
private fun mmToPx(mm: Float): Float =
    mm / 25.4f * LocalContext.current.resources.displayMetrics.ydpi

@Composable
private fun mmToDp(mm: Float): Dp = with(LocalDensity.current) { mmToPx(mm).toDp() }

@SuppressLint("ViewConstructor")
class VoiceKeyboardView(private val service: VoiceKeyboardInputMethodService) :
    AbstractComposeView(service) {
    private val padding = 2.dp
    private val minSize = 12.dp
    private val contentPad = 12.dp
    private val shape = RoundedCornerShape(size = 8.dp)

    @Composable
    override fun Content() {
        KaiboardTheme {
            val buttonHeight = mmToDp(BUTTON_HEIGHT_MM)
            val keyWidth = mmToDp(SIDE_KEY_WIDTH_MM)
            // true while a finger is down on a mic press that started the recording; the
            // cancel button would only get in the way, since dragging up cancels a hold
            var micHeld by remember { mutableStateOf(false) }
            // set the moment a release stops or throws away the recording, until the service has really
            // stopped; without it the cancel (X) button shows for a frame between release and the stop
            var stopping by remember { mutableStateOf(false) }
            LaunchedEffect(service.isRecording) { if (!service.isRecording) stopping = false }
            Row(modifier = Modifier.padding(bottom = mmToDp(DEAD_SPACE_MM))) {
                RecordButton(
                    enabled = service.canTranscribe,
                    isRecording = service.isRecording,
                    status = service.statusText,
                    onClick = service::toggleRecord,
                    onCancel = {
                        stopping = true
                        service.cancelRecord()
                    },
                    onStopping = { stopping = true },
                    onHoldChange = { micHeld = it },
                    quickLanguages = service::quickLanguages,
                    quickCurrent = service::quickLanguageCurrent,
                    onQuickPick = service::pickQuickLanguage,
                    level = { service.micLevel },
                    onDoubleTap = service::showAgentPopup,
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(padding)
                        .heightIn(min = buttonHeight),
                    shape = shape,
                )
                if (service.isRecording && !micHeld && !stopping) {
                    CancelButton(
                        onClick = {
                            Tutorial.saw(Tutorial.Event.CANCEL_BUTTON)
                            service.cancelRecord()
                        },
                        modifier = Modifier
                            .padding(padding)
                            .width(keyWidth)
                            .heightIn(min = buttonHeight),
                        shape = shape,
                        contentPadding = PaddingValues(horizontal = contentPad),
                    )
                }
                DeleteButton(
                    onPress = service::beginDeleteRun,
                    onDelete = service::backspace,
                    onDeleteWord = service::deleteWord,
                    onUndo = service::undoAfterBackspaceGesture,
                    onRedo = service::redoAfterBackspaceGesture,
                    buttonHeight = buttonHeight,
                    keyWidth = keyWidth,
                    modifier = Modifier.padding(padding),
                    shape = shape,
                    contentPadding = PaddingValues(horizontal = contentPad),
                )
                EnterButton(
                    onEnter = service::enter,
                    onSettings = service::openSettings,
                    onPreviousKeyboard = service::switchToPreviousKeyboard,
                    showHint = service.enterHint,
                    buttonHeight = buttonHeight,
                    keyWidth = keyWidth,
                    modifier = Modifier.padding(padding),
                    shape = shape,
                    contentPadding = PaddingValues(horizontal = contentPad),
                )
            }
            // a line over the strip for a moment: the offline agent is not here yet
            service.popup?.let { text ->
                Box(
                    contentAlignment = Alignment.Center,
                    // the root is sized by the strip, so filling it covers the keys exactly
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(bottom = mmToDp(DEAD_SPACE_MM))
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

@Composable
private fun CancelButton(
    onClick: () -> Unit, modifier: Modifier, shape: Shape, contentPadding: PaddingValues,
) {
    Button(onClick = onClick, modifier = modifier, shape = shape, contentPadding = contentPadding) {
        Icon(Icons.Outlined.Clear, stringResource(R.string.cancel_recording))
    }
}

// Tap deletes one character; holding repeats characters, then speeds up to whole words.
// Dragging left (toward the mic, one direction only) undoes: the key stays put and tints toward
// red while the undo arrow rolls in over the backspace, like enter's gear; past the line it turns
// solid red and ticks, and letting go undoes the previous edit.
@Composable
private fun DeleteButton(
    onPress: () -> Unit, onDelete: () -> Unit, onDeleteWord: () -> Unit,
    onUndo: (repeated: Boolean) -> Unit, onRedo: () -> Unit, buttonHeight: Dp, keyWidth: Dp,
    modifier: Modifier, shape: Shape, contentPadding: PaddingValues
) {
    val interactionSource = remember { MutableInteractionSource() }
    val undoDistancePx = mmToPx(UNDO_DRAG_MM)
    val maxDragPx = mmToPx(UNDO_DRAG_MAX_MM)
    val rollPx = with(LocalDensity.current) { ROLL_DP.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    var dragX by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var deletes by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactionSource) {
        // collectLatest cancels the hold loop when the release arrives
        interactionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                onPress()
                deletes = 1
                onDelete()
                delay(HOLD_GATE_MS)
                var repeats = 0
                while (true) {
                    deletes++
                    if (repeats < 10) {
                        onDelete()
                        delay(50)
                    } else {
                        onDeleteWord()
                        delay(200)
                    }
                    repeats++
                }
            }
        }
    }
    val progress = (abs(dragX) / undoDistancePx).coerceIn(0f, 1f)
    val right = dragX > 0f // drag right: redo
    // the key tints toward red as it goes, and is solid red once the undo is armed
    val armedColor = if (right) scheme.tertiary else UndoRed
    val armedInk = if (right) scheme.onTertiary else Color.White
    val container = if (armed) armedColor else lerp(scheme.primary, armedColor, progress * 0.8f)
    val content = if (armed) armedInk else lerp(scheme.onPrimary, armedInk, progress * 0.8f)
    Box(modifier = modifier) {
        Button(
            onClick = {},
            interactionSource = interactionSource,
            modifier = Modifier
                .width(keyWidth)
                .heightIn(min = buttonHeight)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX <= -undoDistancePx) {
                                onUndo(deletes > 1)
                                Tutorial.saw(Tutorial.Event.UNDO)
                            } else if (dragX >= undoDistancePx) {
                                onRedo()
                            }
                            dragX = 0f
                            armed = false
                        },
                        onDragCancel = {
                            dragX = 0f
                            armed = false
                        },
                    ) { change, amount ->
                        change.consume()
                        dragX = (dragX + amount).coerceIn(-maxDragPx, maxDragPx)
                        val nowArmed = abs(dragX) >= undoDistancePx
                        if (nowArmed && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        armed = nowArmed
                    }
                },
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        ) {
            // the backspace rolls out to the left as the undo arrow rolls in from the right
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.delete_button),
                    modifier = Modifier.graphicsLayer {
                        translationX = (if (right) progress else -progress) * rollPx
                        alpha = 1f - progress
                    }
                )
                // the undo arrow rolls in from the right on a left drag, the redo arrow from the left on a right one
                Icon(
                    if (right) Icons.AutoMirrored.Outlined.Redo else Icons.AutoMirrored.Outlined.Undo,
                    stringResource(if (right) R.string.redo_button else R.string.undo_button),
                    modifier = Modifier.graphicsLayer {
                        translationX = (if (right) -(1f - progress) else (1f - progress)) * rollPx
                        alpha = progress
                    }
                )
            }
        }
    }
}

// Tap: enter. Flick up: the return arrow rolls up out of the key while a keyboard rolls in from
// below; past the line the key tints and ticks, and letting go switches to the previous keyboard.
// Hold (300 ms, finger still): settings. A small gear in the corner says so, until it is turned off
// in Appearance. Everything stays inside the key: an input method cannot draw outside its strip.
@Composable
private fun EnterButton(
    onEnter: () -> Unit, onSettings: () -> Unit, onPreviousKeyboard: () -> Unit, showHint: Boolean,
    buttonHeight: Dp, keyWidth: Dp, modifier: Modifier, shape: Shape, contentPadding: PaddingValues
) {
    val switchDistancePx = mmToPx(KEYBOARD_FLICK_MM)
    val rollPx = with(LocalDensity.current) { ROLL_DP.dp.toPx() }
    val slop = LocalViewConfiguration.current.touchSlop
    val haptic = LocalHapticFeedback.current
    val uiScope = rememberCoroutineScope()
    var dragY by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val up = (-dragY / switchDistancePx).coerceIn(0f, 1f)
    Button(
        onClick = {},
        modifier = modifier
            .width(keyWidth)
            .heightIn(min = buttonHeight)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragY = 0f
                    armed = false
                    var moved = false
                    var held = false
                    // a still finger for the hold time opens settings; any movement first makes it a flick
                    val hold = uiScope.launch {
                        delay(HOLD_FOR_SETTINGS_MS)
                        if (!moved) {
                            held = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Tutorial.saw(Tutorial.Event.HOLD_SETTINGS)
                            onSettings()
                        }
                    }
                    var lastY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (held) {
                            change.consume()
                        } else {
                            val dy = change.position.y - lastY
                            lastY = change.position.y
                            if (!moved && abs(change.position.y - down.position.y) > slop) moved = true
                            if (moved) {
                                change.consume()
                                dragY = (dragY + dy).coerceIn(-switchDistancePx * 1.25f, 0f)
                                val nowArmed = dragY <= -switchDistancePx
                                if (nowArmed && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                armed = nowArmed
                            }
                        }
                        if (!change.pressed) break
                    }
                    hold.cancel()
                    when {
                        held -> {}
                        moved -> if (armed) {
                            Tutorial.saw(Tutorial.Event.FLICK_KEYBOARD)
                            onPreviousKeyboard()
                        }
                        else -> onEnter()
                    }
                    dragY = 0f
                    armed = false
                }
            },
        shape = shape,
        contentPadding = contentPadding,
        colors = if (armed) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        // sized by the 24 dp icon (never fillMaxHeight: the button has no ceiling and would take the screen)
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardReturn,
                stringResource(R.string.return_button),
                modifier = Modifier.graphicsLayer {
                    translationY = -up * rollPx
                    alpha = 1f - up
                }
            )
            Icon(
                Icons.Outlined.Keyboard,
                stringResource(R.string.switch_keyboard_button),
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - up) * rollPx
                    alpha = up
                }
            )
            if (showHint) {
                // the way a phone key shows its second character: small, in the corner, out of the way
                Icon(
                    Icons.Outlined.Settings,
                    stringResource(R.string.settings_button),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = HINT_OFFSET_X_DP.dp, y = (-HINT_OFFSET_Y_DP).dp)
                        .size(HINT_SIZE_DP.dp)
                        .graphicsLayer { alpha = HINT_ALPHA * (1f - up) }
                )
            }
        }
    }
}

// Five bars showing the last 0.4 s of microphone level, newest on the right, so the key
// shows it is hearing you. Real levels, not decoration.
@Composable
private fun LevelBars(level: () -> Float, modifier: Modifier) {
    val history = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(80)
            history.removeAt(0)
            history.add(level())
        }
    }
    val color = LocalContentColor.current
    Canvas(modifier.width(31.dp).height(28.dp)) {
        val w = 3.dp.toPx()
        val pitch = 7.dp.toPx()
        history.forEachIndexed { i, v ->
            val h = (6.dp + 22.dp * v).toPx()
            drawRoundRect(
                color, topLeft = Offset(i * pitch, (size.height - h) / 2),
                size = Size(w, h), cornerRadius = CornerRadius(w / 2)
            )
        }
    }
}

// Tap: start now, tap again to stop. Hold past the gate: stop and transcribe on release.
// Hold and drag up about a centimeter: release throws the recording away.
// While held, a ring grows from the thumb out to the button's edges; when it gets there the
// button is in hold mode and shows a thick hollow outline.
@Composable
private fun RecordButton(
    enabled: Boolean, isRecording: Boolean, status: String?, onClick: () -> Unit,
    onCancel: () -> Unit, onStopping: () -> Unit, onHoldChange: (Boolean) -> Unit,
    quickLanguages: () -> List<Pair<String, String>>, quickCurrent: () -> String,
    onQuickPick: (String) -> Unit, level: () -> Float, onDoubleTap: () -> Unit, modifier: Modifier, shape: Shape
) {
    var lastTapAt by remember { mutableStateOf(0L) }
    // handle showing Record on the first render and not Transcribing...
    var firstRender by remember { mutableStateOf(true) }
    // handle stopwatch timer
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startedAt = SystemClock.elapsedRealtime()
            seconds = 0
            while (true) {
                delay(250)
                seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
            }
        }
    }

    val recordingNow by rememberUpdatedState(isRecording)
    val enabledNow by rememberUpdatedState(enabled)
    val cancelDistancePx = mmToPx(CANCEL_DRAG_MM)
    val languageDistancePx = mmToPx(LANGUAGE_DRAG_MM)
    val scheme = MaterialTheme.colorScheme

    var touch by remember { mutableStateOf<Offset?>(null) }
    var holdMode by remember { mutableStateOf(false) }
    var cancelArmed by remember { mutableStateOf(false) }
    // true from a starting press until the hold arms or the finger lifts: the key fills only after
    var charging by remember { mutableStateOf(false) }
    val fill = remember { Animatable(0f) } // 0: resting key, 1: the filled live key
    val pulse = remember { Animatable(0f) } // the edge pulse at arming, 1 fading to 0
    val live = isRecording && !charging
    LaunchedEffect(live) {
        fill.animateTo(if (live) 1f else 0f, tween(if (live) FILL_IN_MS else FILL_OUT_MS))
    }
    LaunchedEffect(holdMode) {
        if (holdMode) { pulse.snapTo(1f); pulse.animateTo(0f, tween(PULSE_MS)) }
    }
    // the resting key follows the theme; the live key is one fixed blue with white on it, so the
    // live state looks the same on every phone (the theme's container blue came out dull and dark)
    val liveBg = LiveBlue
    val liveFg = Color.White
    val container = if (cancelArmed) CancelRed else lerp(scheme.primary, liveBg, fill.value)
    val content = if (cancelArmed) Color.White else lerp(scheme.onPrimary, liveFg, fill.value)
    val ringColor = scheme.onPrimary
    // the quick language list, drawn inside the button while a left slide is in progress
    var menu by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var menuCurrent by remember { mutableStateOf("") }
    var hover by remember { mutableIntStateOf(-1) }
    val ring = remember { Animatable(0f) }
    val uiScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Button(
        onClick = {}, enabled = enabled,
        modifier = modifier
            .clip(shape)
            .drawWithContent {
                drawContent()
                val p = touch ?: return@drawWithContent
                val color = if (cancelArmed) Color.White else ringColor
                if (!holdMode) {
                    val reach = hypot(max(p.x, size.width - p.x), max(p.y, size.height - p.y))
                    drawCircle(
                        color.copy(alpha = 0.85f), radius = ring.value * reach, center = p,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                } else {
                    // held: the key is filled (see colors), with one pulse at the edge as it arms
                    val corner = CornerRadius(8.dp.toPx())
                    val outline = content
                    for ((width, alpha) in listOf(3f to pulse.value)) {
                        val w = width.dp.toPx()
                        drawRoundRect(
                            outline.copy(alpha = alpha), cornerRadius = corner,
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            style = Stroke(width = w)
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // observe only (initial pass, nothing consumed), so the button keeps its own press look
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!enabledNow) return@awaitEachGesture
                    val startedOnPress = !recordingNow
                    val pressedAt = SystemClock.uptimeMillis()
                    // a second tap right after a tap: not a recording, the agent popup (stops the tap's recording)
                    if (recordingNow && pressedAt - lastTapAt < DOUBLE_TAP_MS) {
                        lastTapAt = 0L
                        onCancel()
                        onDoubleTap()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                        }
                        return@awaitEachGesture
                    }
                    if (startedOnPress) {
                        onClick()
                        onHoldChange(true)
                    }
                    touch = down.position
                    holdMode = false
                    cancelArmed = false
                    charging = startedOnPress
                    val grow = uiScope.launch {
                        // timed by the frame clock itself: animateTo follows the system "animator duration
                        // scale", and with animations switched off the ring never showed
                        ring.snapTo(0f)
                        val start = withFrameNanos { it }
                        while (true) {
                            val t = (withFrameNanos { it } - start) / 1_000_000f / HOLD_GATE_MS
                            ring.snapTo(t.coerceAtMost(1f))
                            if (t >= 1f) break
                        }
                        holdMode = true
                        charging = false
                        Tutorial.saw(Tutorial.Event.HOLD_START)
                        // the ring has reached the edges: hold mode starts now
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    var dragUp = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dragUp = (down.position.y - change.position.y).coerceAtLeast(0f)
                        val dragLeft = (down.position.x - change.position.x).coerceAtLeast(0f)
                        if (menu == null && startedOnPress && dragLeft >= languageDistancePx && dragLeft > dragUp) {
                            val items = quickLanguages()
                            if (items.isNotEmpty()) {
                                // choosing a language replaces recording: throw away what just started
                                grow.cancel()
                                touch = null
                                holdMode = false
                                cancelArmed = false
                                menu = items
                                menuCurrent = quickCurrent()
                                hover = -1
                                onCancel()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                        val items = menu
                        if (items != null) {
                            val i = if (change.position.x in 0f..size.width.toFloat())
                                (change.position.x / size.width * items.size).toInt().coerceIn(0, items.size - 1)
                            else -1
                            if (i != hover) {
                                hover = i
                                if (i >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (!change.pressed) break
                            continue
                        }
                        val nowArmed = startedOnPress && dragUp >= cancelDistancePx
                        if (nowArmed && !cancelArmed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cancelArmed = nowArmed
                        if (!change.pressed) break
                    }
                    grow.cancel()
                    val picking = menu
                    if (picking != null) {
                        if (hover in picking.indices) onQuickPick(picking[hover].first)
                        menu = null
                        hover = -1
                        touch = null
                        onHoldChange(false)
                        return@awaitEachGesture
                    }
                    val held = SystemClock.uptimeMillis() - pressedAt >= HOLD_GATE_MS
                    when {
                        startedOnPress && dragUp >= cancelDistancePx -> {
                            Tutorial.saw(Tutorial.Event.HOLD_CANCEL)
                            onCancel()
                        }
                        !startedOnPress -> {
                            Tutorial.saw(Tutorial.Event.TAP_STOP)
                            onStopping()
                            onClick()
                        }
                        held -> {
                            Tutorial.saw(Tutorial.Event.HOLD_STOP)
                            onStopping()
                            onClick()
                        }
                        else -> {
                            lastTapAt = SystemClock.uptimeMillis()
                            Tutorial.saw(Tutorial.Event.TAP_START) // a tap: the recording carries on
                        }
                    }
                    touch = null
                    holdMode = false
                    charging = false
                    cancelArmed = false
                    onHoldChange(false)
                }
            },
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        val items = menu
        if (items != null) {
            items.forEachIndexed { i, (code, label) ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                i == hover -> MaterialTheme.colorScheme.tertiary
                                code == menuCurrent -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                else -> Color.Transparent
                            }
                        )
                ) {
                    Text(
                        label, fontSize = 16.sp, maxLines = 1,
                        color = if (i == hover) MaterialTheme.colorScheme.onTertiary else Color.Unspecified
                    )
                }
            }
        } else if (status != null && !isRecording) {
            Text(status, fontSize = 16.sp)
        } else if (!enabled && firstRender) {
            Icon(
                Icons.Outlined.KeyboardVoice, stringResource(R.string.start_recording)
            )
        } else if (!enabled) {
            firstRender = false
            Text(stringResource(R.string.transcribing), fontSize = 16.sp)
        } else if (isRecording) {
            firstRender = false
            Icon(Icons.Outlined.Check, stringResource(R.string.stop_recording))
            LevelBars(level, Modifier.padding(start = 12.dp, end = 4.dp))
            Text(
                " %d".format(seconds / 60) + ":%02d".format(
                    seconds % 60
                ), fontSize = 16.sp
            )
        } else {
            Icon(
                Icons.Outlined.KeyboardVoice, stringResource(R.string.start_recording)
            )
        }
    }
}
