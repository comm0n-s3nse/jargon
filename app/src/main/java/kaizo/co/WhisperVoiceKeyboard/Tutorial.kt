package kaizo.co.WhisperVoiceKeyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The gesture tutorial's progress. The keyboard and the settings activity run in one process, so
 * the keys report what the finger did here and the tutorial screen watches [step]. Off (and free)
 * unless the screen is open.
 */
object Tutorial {
    enum class Event { TAP_START, TAP_STOP, CANCEL_BUTTON, HOLD_START, HOLD_STOP, HOLD_CANCEL, UNDO, FLICK_KEYBOARD, HOLD_SETTINGS }

    /** Each step is a short sequence of events; a wrong event starts the sequence over. */
    enum class Step(val needs: List<Event>) {
        TAP(listOf(Event.TAP_START, Event.TAP_STOP)),
        TAP_CANCEL(listOf(Event.TAP_START, Event.CANCEL_BUTTON)),
        HOLD(listOf(Event.HOLD_START, Event.HOLD_STOP)),
        HOLD_CANCEL(listOf(Event.HOLD_START, Event.HOLD_CANCEL)),
        UNDO(listOf(Event.UNDO)),
        FLICK(listOf(Event.FLICK_KEYBOARD)),
        SETTINGS(listOf(Event.HOLD_SETTINGS)),
        DONE(emptyList());

        val next get() = entries.getOrElse(ordinal + 1) { DONE }
    }

    var active by mutableStateOf(false)
        private set
    var step by mutableStateOf(Step.TAP)
        private set
    private var progress = 0

    fun start() {
        active = true
        step = Step.TAP
        progress = 0
    }

    fun stop() {
        active = false
    }

    /** The FLICK step is finished by the tutorial screen itself, once the keyboard is back. */
    fun advance() {
        if (active) {
            step = step.next
            progress = 0
        }
    }

    fun saw(event: Event) {
        if (!active || step == Step.DONE) return
        val needs = step.needs
        when (event) {
            needs[progress] -> {
                progress++
                if (progress == needs.size) advance()
            }
            needs[0] -> progress = 1
            else -> progress = 0
        }
    }
}
