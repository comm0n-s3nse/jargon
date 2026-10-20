package kaizo.co.WhisperVoiceKeyboard

import kaizo.co.WhisperVoiceKeyboard.Tutorial.Event
import kaizo.co.WhisperVoiceKeyboard.Tutorial.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesAndTutorialTest {
    @Test
    fun versionsCompareAsNumbers() {
        assertTrue(Updates.isNewer("v1.1", "1.0"))
        assertTrue(Updates.isNewer("v1.0.1", "1.0"))
        assertTrue(Updates.isNewer("v2.0", "1.9.9"))
        assertFalse(Updates.isNewer("v1.0", "1.0"))
        assertFalse(Updates.isNewer("v0.9", "1.0"))
        assertFalse(Updates.isNewer("junk", "1.0"))
    }

    @Test
    fun theTutorialWalksItsSteps() {
        Tutorial.start()
        assertEquals(Step.TAP, Tutorial.step)
        Tutorial.saw(Event.TAP_START); Tutorial.saw(Event.TAP_STOP)
        assertEquals(Step.TAP_CANCEL, Tutorial.step)
        Tutorial.saw(Event.TAP_START); Tutorial.saw(Event.TAP_STOP) // wrong second half: start over
        assertEquals(Step.TAP_CANCEL, Tutorial.step)
        Tutorial.saw(Event.TAP_START); Tutorial.saw(Event.CANCEL_BUTTON)
        assertEquals(Step.HOLD, Tutorial.step)
        Tutorial.saw(Event.HOLD_START); Tutorial.saw(Event.HOLD_STOP)
        Tutorial.saw(Event.HOLD_START); Tutorial.saw(Event.HOLD_CANCEL)
        Tutorial.saw(Event.UNDO)
        assertEquals(Step.FLICK, Tutorial.step)
        Tutorial.saw(Event.FLICK_KEYBOARD)
        assertEquals(Step.SETTINGS, Tutorial.step)
        Tutorial.saw(Event.HOLD_SETTINGS)
        assertEquals(Step.DONE, Tutorial.step)
        Tutorial.stop()
        Tutorial.saw(Event.TAP_START)
        assertFalse(Tutorial.active)
    }
}
