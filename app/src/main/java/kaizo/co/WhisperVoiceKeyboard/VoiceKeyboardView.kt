package kaizo.co.WhisperVoiceKeyboard

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kaizo.co.WhisperVoiceKeyboard.ui.theme.KaiboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

// a press shorter than this is a tap, longer is a hold (word-delete, press-to-talk)
private const val HOLD_GATE_MS = 240L

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
private const val SETTINGS_DRAG_MM = 8f

private val CancelRed = Color(0xFFD32F2F)

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
            // set the moment a hold is thrown away, until the service has really stopped recording;
            // without it the cancel (X) button shows for a blink between release and the stop
            var cancelling by remember { mutableStateOf(false) }
            LaunchedEffect(service.isRecording) { if (!service.isRecording) cancelling = false }
            Row(modifier = Modifier.padding(bottom = mmToDp(DEAD_SPACE_MM))) {
                RecordButton(
                    enabled = service.canTranscribe,
                    isRecording = service.isRecording,
                    status = service.statusText,
                    onClick = service::toggleRecord,
                    onCancel = {
                        cancelling = true
                        service.cancelRecord()
                    },
                    onHoldChange = { micHeld = it },
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(padding)
                        .heightIn(min = buttonHeight),
                    shape = shape,
                )
                if (service.isRecording && !micHeld && !cancelling) {
                    CancelButton(
                        onClick = service::cancelRecord,
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
                    buttonHeight = buttonHeight,
                    keyWidth = keyWidth,
                    modifier = Modifier.padding(padding),
                    shape = shape,
                    contentPadding = PaddingValues(horizontal = contentPad),
                )
                EnterButton(
                    onEnter = service::enter,
                    onSettings = service::openSettings,
                    buttonHeight = buttonHeight,
                    keyWidth = keyWidth,
                    modifier = Modifier.padding(padding),
                    shape = shape,
                    contentPadding = PaddingValues(horizontal = contentPad),
                )
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
// Dragging left (toward the mic, one direction only) undoes: red fills in behind the button
// as it leaves, and letting go past the line undoes the previous edit.
@Composable
private fun DeleteButton(
    onPress: () -> Unit, onDelete: () -> Unit, onDeleteWord: () -> Unit,
    onUndo: (repeated: Boolean) -> Unit, buttonHeight: Dp, keyWidth: Dp,
    modifier: Modifier, shape: Shape, contentPadding: PaddingValues
) {
    val interactionSource = remember { MutableInteractionSource() }
    val undoDistancePx = mmToPx(UNDO_DRAG_MM)
    val maxDragPx = mmToPx(UNDO_DRAG_MAX_MM)
    var dragX by remember { mutableFloatStateOf(0f) }
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
    val progress = (-dragX / undoDistancePx).coerceIn(0f, 1f)
    Box(
        modifier = modifier.drawBehind {
            // what the button leaves behind: red that deepens as the undo gets closer
            if (progress > 0f) {
                drawRoundRect(
                    CancelRed.copy(alpha = 0.2f + 0.7f * progress),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
        }
    ) {
        Button(
            onClick = {},
            interactionSource = interactionSource,
            modifier = Modifier
                .width(keyWidth)
                .heightIn(min = buttonHeight)
                .offset { IntOffset(dragX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX <= -undoDistancePx) onUndo(deletes > 1)
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f },
                    ) { change, amount ->
                        change.consume()
                        dragX = (dragX + amount).coerceIn(-maxDragPx, 0f)
                    }
                },
            shape = shape,
            contentPadding = contentPadding
        ) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.delete_button))
        }
    }
}

// Tap: enter. Pull up: the return arrow rolls up out of the key while a gear rolls in from below;
// past the line the key tints and ticks, and letting go opens settings. Everything stays inside
// the key because an input method cannot draw outside its own strip.
@Composable
private fun EnterButton(
    onEnter: () -> Unit, onSettings: () -> Unit, buttonHeight: Dp, keyWidth: Dp,
    modifier: Modifier, shape: Shape, contentPadding: PaddingValues
) {
    val openDistancePx = mmToPx(SETTINGS_DRAG_MM)
    val rollPx = with(LocalDensity.current) { 28.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var dragY by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val progress = (-dragY / openDistancePx).coerceIn(0f, 1f)
    Button(
        onClick = onEnter,
        modifier = modifier
            .width(keyWidth)
            .heightIn(min = buttonHeight)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY <= -openDistancePx) onSettings()
                        dragY = 0f
                        armed = false
                    },
                    onDragCancel = {
                        dragY = 0f
                        armed = false
                    },
                ) { change, amount ->
                    change.consume()
                    dragY = (dragY + amount).coerceIn(-openDistancePx * 1.25f, 0f)
                    val nowArmed = dragY <= -openDistancePx
                    if (nowArmed && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    armed = nowArmed
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
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardReturn,
                stringResource(R.string.return_button),
                modifier = Modifier.graphicsLayer {
                    translationY = -progress * rollPx
                    alpha = 1f - progress
                }
            )
            Icon(
                Icons.Outlined.Settings,
                stringResource(R.string.settings_button),
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - progress) * rollPx
                    alpha = progress
                }
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
    onCancel: () -> Unit, onHoldChange: (Boolean) -> Unit, modifier: Modifier, shape: Shape
) {
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
    val ringColor = MaterialTheme.colorScheme.onPrimary

    var touch by remember { mutableStateOf<Offset?>(null) }
    var holdMode by remember { mutableStateOf(false) }
    var cancelArmed by remember { mutableStateOf(false) }
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
                val color = if (cancelArmed) CancelRed else ringColor
                if (cancelArmed) drawRect(CancelRed.copy(alpha = 0.35f))
                if (!holdMode) {
                    val reach = hypot(max(p.x, size.width - p.x), max(p.y, size.height - p.y))
                    drawCircle(
                        color.copy(alpha = 0.85f), radius = ring.value * reach, center = p,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                } else {
                    // hollow thick outline, fading off toward the middle
                    val corner = CornerRadius(8.dp.toPx())
                    for ((width, alpha) in listOf(14f to 0.2f, 10f to 0.35f, 6f to 0.95f)) {
                        val w = width.dp.toPx()
                        drawRoundRect(
                            color.copy(alpha = alpha), cornerRadius = corner,
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
                    if (startedOnPress) {
                        onClick()
                        onHoldChange(true)
                    }
                    touch = down.position
                    holdMode = false
                    cancelArmed = false
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
                        // the ring has reached the edges: hold mode starts now
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    var dragUp = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dragUp = (down.position.y - change.position.y).coerceAtLeast(0f)
                        val nowArmed = startedOnPress && dragUp >= cancelDistancePx
                        if (nowArmed && !cancelArmed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cancelArmed = nowArmed
                        if (!change.pressed) break
                    }
                    grow.cancel()
                    val held = SystemClock.uptimeMillis() - pressedAt >= HOLD_GATE_MS
                    when {
                        startedOnPress && dragUp >= cancelDistancePx -> onCancel()
                        !startedOnPress || held -> onClick()
                    }
                    touch = null
                    holdMode = false
                    cancelArmed = false
                    onHoldChange(false)
                }
            },
        shape = shape
    ) {
        if (status != null && !isRecording) {
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
