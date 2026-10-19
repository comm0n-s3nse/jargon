package kaizo.co.WhisperVoiceKeyboard

import android.content.Intent
import kaizo.co.WhisperVoiceKeyboard.models.languageFor
import kaizo.co.WhisperVoiceKeyboard.models.ModelStore
import kaizo.co.WhisperVoiceKeyboard.models.ModelEntry
import kaizo.co.WhisperVoiceKeyboard.models.LANGUAGE_KEY
import kaizo.co.WhisperVoiceKeyboard.models.TRANSLATE_KEY
import kaizo.co.WhisperVoiceKeyboard.models.recentTranslateTargets
import kaizo.co.WhisperVoiceKeyboard.models.rememberTranslateTarget
import kaizo.co.WhisperVoiceKeyboard.models.ACTIVE_MODEL_KEY
import kaizo.co.WhisperVoiceKeyboard.engine.WhisperEngine
import kaizo.co.WhisperVoiceKeyboard.engine.SpeechEngine
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.delay
import kaizo.co.WhisperVoiceKeyboard.tune.pickThreads
import kaizo.co.WhisperVoiceKeyboard.tune.Threads
import android.os.SystemClock
import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.whispercpp.whisper.WhisperContext
import kaizo.co.WhisperVoiceKeyboard.audio.trimSilence
import kaizo.co.WhisperVoiceKeyboard.media.decodeWaveFile
import kaizo.co.WhisperVoiceKeyboard.recorder.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "VoiceKeyboardInputMethodService"

// how much text either side of the cursor identifies an edit, and how many edits are kept
private const val CONTEXT_CHARS = 12
private const val MAX_EDITS = 3

class VoiceKeyboardInputMethodService : InputMethodService(), LifecycleOwner,
    SavedStateRegistryOwner {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // handle audio manager
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(LOG_TAG, "loss of focus")
                toggleRecord()
            }
        }
    }

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        ).setAcceptsDelayedFocusGain(false).setOnAudioFocusChangeListener(focusChangeListener)
            .build()

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        synchronized(editLock) {
            edits.clear()
            runEdit = null
        }
        // picks up a model or "Auto" chosen in settings while the service is already running
        scope.launch { if (!isRecording) prepare() }
    }

    // an edit only stays undoable while the text around the cursor is still what it left,
    // so a manual edit or cursor move drops the stack
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        synchronized(editLock) {
            edits.lastOrNull()?.let { if (!stillApplies(it)) edits.clear() }
        }
    }

    override fun onCreateInputView(): View {
        val view = VoiceKeyboardView(this)
        window!!.window!!.decorView.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        return view
    }

    // Lifecycle Methods
    private var lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private fun handleLifecycleEvent(event: Lifecycle.Event) =
        lifecycleRegistry.handleLifecycleEvent(event)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryCtrl.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        modelsPath = File(application.filesDir, "models")
        samplesPath = File(application.filesDir, "samples")
        sharedPref = application.getSharedPreferences(
            getString(R.string.preference_file_key), Context.MODE_PRIVATE
        )

        scope.launch {
            printSystemInfo()
            loadData()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // This runs on the main thread, so it must not wait: a transcription pass in progress can
        // hold the engine for seconds, and the system gives the main thread five before it calls
        // the app unresponsive. Detach what needs freeing and let it finish on its own.
        val leaving = engine
        val wasRecording = isRecording
        engine = null
        job.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            if (wasRecording) recorder.stopRecording()
            leaving?.release()
        }
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    // SaveStateRegistry Methods
    private val savedStateRegistryCtrl = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryCtrl.savedStateRegistry

    // copy whispercppdemo MainScreenViewModel implementation
    private var engine: SpeechEngine? = null
    private var loadedModel: ModelEntry? = null
    private var recordedFile: File? = null
    private var recorder: Recorder = Recorder()
    var canTranscribe by mutableStateOf(false)
        private set
    var isRecording by mutableStateOf(false)
        private set
    private var modelsPath: File? = null
    private var samplesPath: File? = null
    private var sharedPref: SharedPreferences? = null
    private val maxThreads = Runtime.getRuntime().availableProcessors()
    // shown on the mic button while the keyboard times itself ("Tuning… 2/6")
    var statusText by mutableStateOf<String?>(null)
        private set
    private var busy = false // loading a model or tuning; the mic waits

    private var leading: String = ""
    private var trailing: String = ""

    // text highlighted when recording started; the dictation replaces it
    private var replaced: String? = null

    // true: next utterance starts a sentence, false: continues one, null: leave the model's case alone
    private var sentenceStart: Boolean? = null

    // Undo stack, newest last: dictations (with the spaces they carry and any highlight they
    // replaced) and runs of deletions. Each edit remembers the text around the cursor as it
    // stood right after it, and only applies while that still holds.
    private class Edit(
        val inserted: String, var deleted: String, var before: String, var after: String
    )

    private val editLock = Any()
    private val edits = ArrayList<Edit>()
    private var runEdit: Edit? = null

    private fun contextNow(): Pair<String, String> {
        val ic = currentInputConnection
        return Pair(
            ic?.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty(),
            ic?.getTextAfterCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        )
    }

    private fun stillApplies(edit: Edit) = contextNow() == Pair(edit.before, edit.after)

    // nothing typed into a password or email field is kept, not even in memory
    private fun keepsNoHistory(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: 0
        return isPasswordField(inputType) || isEmailField(inputType)
    }

    // call once the dictation is in the field
    private fun pushEdit(inserted: String, deleted: String) {
        if (keepsNoHistory()) return
        synchronized(editLock) {
            val (before, after) = contextNow()
            edits.add(Edit(inserted, deleted, before, after))
            if (edits.size > MAX_EDITS) edits.removeAt(0)
        }
    }

    // call at the start of a backspace press: its deletions become one edit
    fun beginDeleteRun() = synchronized(editLock) { runEdit = null }

    private fun recordDeletion(text: String) {
        if (keepsNoHistory()) return
        synchronized(editLock) {
            val (before, after) = contextNow()
            val run = runEdit
            if (run != null && edits.lastOrNull() === run) {
                run.deleted = text + run.deleted
                run.before = before
                run.after = after
            } else {
                val edit = Edit("", text, before, after)
                edits.add(edit)
                runEdit = edit
                if (edits.size > MAX_EDITS) edits.removeAt(0)
            }
        }
    }

    // undoes the newest edit if the field still looks the way it left it
    private fun undoOne(): Boolean = synchronized(editLock) {
        val ic = currentInputConnection ?: return false
        val edit = edits.lastOrNull() ?: return false
        if (!stillApplies(edit)) {
            edits.clear()
            return false
        }
        if (edit.inserted.isNotEmpty()) {
            ic.deleteSurroundingTextInCodePoints(
                edit.inserted.codePointCount(0, edit.inserted.length), 0
            )
        }
        if (edit.deleted.isNotEmpty()) ic.commitText(edit.deleted, 1)
        edits.removeAt(edits.lastIndex)
        true
    }

    // Drag-left on backspace. The touch itself deleted a character, so a plain drag takes that
    // back and then undoes the edit before it. If the touch had turned into a hold (repeated),
    // the drag undoes just that held run.
    fun undoAfterBackspaceGesture(repeated: Boolean) {
        synchronized(editLock) {
            val own = runEdit
            if (own != null && edits.lastOrNull() === own) undoOne()
            if (!repeated) undoOne()
            runEdit = null
        }
    }

    private fun threads(): Int = sharedPref?.let {
        Threads.effective(it, application.getString(R.string.num_threads), maxThreads, loadedModel?.id)
    } ?: maxThreads

    // 3 s of speech-like sound; whisper's cost barely depends on the content
    private fun tuningSample(): FloatArray {
        val rng = java.util.Random(7)
        return FloatArray(16000 * 3) { i ->
            val t = i / 16000f
            val syllables = 0.5f + 0.5f * sin(2 * PI * 3 * t).toFloat()
            syllables * (0.15f * sin(2 * PI * 180 * t).toFloat() +
                0.08f * sin(2 * PI * 540 * t).toFloat()) + 0.01f * (rng.nextFloat() - 0.5f)
        }
    }

    // Loads the selected model if it is not the one in memory, then tunes the thread count if
    // that has not been done for it. Cheap when there is nothing to do, so it runs each time
    // the keyboard opens and picks up changes made in settings.
    private suspend fun prepare() {
        val prefs = sharedPref ?: return
        if (busy) return
        val store = ModelStore(application)
        val wanted = store.active(prefs)
        val needsLoad = engine == null || loadedModel?.id != wanted.id
        val userKey = application.getString(R.string.num_threads)
        if (!needsLoad && !Threads.needsTuning(prefs, userKey, wanted.id)) return
        busy = true
        canTranscribe = false
        try {
            if (needsLoad) loadModel(store, wanted)
            tuneIfNeeded()
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            statusText = null
        } finally {
            busy = false
            canTranscribe = engine != null
        }
    }

    // One model in memory at a time: the old one is released before the new one loads.
    private suspend fun loadModel(store: ModelStore, wanted: ModelEntry) =
        withContext(Dispatchers.IO) {
            printMessage("Loading model ${wanted.fileName}...\n")
            statusText = getString(R.string.model_loading)
            engine?.release()
            engine = null
            loadedModel = null
            var entry = wanted
            var note: String? = null
            val context = try {
                store.open(entry)
            } catch (e: Exception) {
                if (entry.builtIn) throw e
                // a model that will not load must not leave the keyboard dead
                Log.w(LOG_TAG, e)
                sharedPref?.edit()?.remove(ACTIVE_MODEL_KEY)?.apply()
                entry = store.list().first { it.builtIn }
                note = getString(R.string.model_fallback)
                store.open(entry)
            }
            engine = context
            loadedModel = entry
            statusText = note
            printMessage("Loaded model ${entry.fileName}.\n")
        }

    // First use on a phone with this model (or after "Auto" in settings): time a few thread
    // counts and keep the fastest. The mic button says what is happening while it runs.
    private suspend fun tuneIfNeeded() {
        val prefs = sharedPref ?: return
        val speech = engine ?: return
        val id = loadedModel?.id ?: return
        if (!Threads.needsTuning(prefs, application.getString(R.string.num_threads), id)) return
        if (prefs.getBoolean(Threads.TUNING_STARTED, false)) {
            // the last tune never finished: the process was killed or crashed partway. Do not
            // try again, which could loop; use the cautious default instead.
            val safe = Threads.safeDefault(maxThreads)
            Threads.saveTuned(prefs.edit(), id, safe).remove(Threads.TUNING_STARTED).apply()
            printMessage("Tuning was interrupted last time; using $safe threads\n")
            return
        }
        prefs.edit().putBoolean(Threads.TUNING_STARTED, true).commit()
        try {
            val sample = tuningSample()
            val started = SystemClock.elapsedRealtime()
            statusText = getString(R.string.tuning_start)
            speech.transcribe(sample, 4, "en") // warm-up, so the first trial is not penalized
            val best = pickThreads(
                Threads.fastCores(maxThreads),
                { done, total -> statusText = getString(R.string.tuning_progress, done, total) }
            ) { t ->
                var fastest = Long.MAX_VALUE
                for (run in 1..2) {
                    val t0 = SystemClock.elapsedRealtime()
                    speech.transcribe(sample, t, "en")
                    val ms = SystemClock.elapsedRealtime() - t0
                    fastest = minOf(fastest, ms)
                    if (ms > 2000) break // slow phone: one run per setting keeps the wait short
                }
                printMessage("tune: $t threads -> $fastest ms\n")
                fastest
            }
            Threads.saveTuned(prefs.edit(), id, best).remove(Threads.TUNING_STARTED).apply()
            printMessage("Tuned to $best threads in ${SystemClock.elapsedRealtime() - started} ms\n")
            statusText = getString(R.string.tuned_done, best)
            scope.launch {
                delay(3000)
                if (!busy) statusText = null
            }
        } catch (e: Exception) {
            // an untuned keyboard still works, with the default thread count
            Log.w(LOG_TAG, e)
            prefs.edit().remove(Threads.TUNING_STARTED).apply()
            statusText = null
        }
    }

    // the language to translate into, only when the loaded model can and the person chose one
    private fun translateTo(): String? {
        if (engine?.canTranslate != true) return null
        return sharedPref?.getString(TRANSLATE_KEY, "")?.takeIf { it.isNotEmpty() }
    }

    // Quick language list for the mic's left slide: "same as spoken", then the recent targets.
    // Empty when the model cannot translate, which turns the slide off.
    fun quickLanguages(): List<Pair<String, String>> {
        if (engine?.canTranslate != true) return emptyList()
        val prefs = sharedPref ?: return emptyList()
        return listOf("" to application.getString(R.string.quick_same)) +
            recentTranslateTargets(prefs).map { it to it.uppercase() }
    }

    fun quickLanguageCurrent(): String = sharedPref?.getString(TRANSLATE_KEY, "").orEmpty()

    fun pickQuickLanguage(code: String) {
        val prefs = sharedPref ?: return
        if (code.isEmpty()) prefs.edit().remove(TRANSLATE_KEY).apply()
        else {
            prefs.edit().putString(TRANSLATE_KEY, code).apply()
            rememberTranslateTarget(prefs, code)
        }
    }

    // slid left on enter: back to the keyboard used before this one
    @Suppress("DEPRECATION")
    fun switchToPreviousKeyboard() {
        val switched = try {
            switchToPreviousInputMethod() || switchToNextInputMethod(false)
        } catch (e: Exception) {
            false
        }
        if (!switched) {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
        }
    }

    private fun language(): String {
        val file = loadedModel?.fileName ?: return "en"
        return languageFor(file, sharedPref?.getString(LANGUAGE_KEY, "en") ?: "en")
    }

    private fun checkBoolPref(resource: Int): Boolean? {
        return sharedPref?.getBoolean(
            application.getString(resource), false
        )
    }

    private fun printSystemInfo() {
        printMessage(String.format("System Info: %s\n", WhisperContext.getSystemInfo()))
    }

    private suspend fun loadData() {
        printMessage("Loading data...\n")
        try {
            copyAssets()
            prepare()
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    private fun printMessage(msg: String) {
        Log.d(LOG_TAG, msg)
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        modelsPath?.mkdirs()
        samplesPath?.mkdirs()
        //application.copyData("models", modelsPath, ::printMessage)
        //samplesPath?.let { application.copyData("samples", it, ::printMessage) }
        printMessage("All data copied to working directory.\n")
    }

    private suspend fun transcribeFile(file: File) {
        if (!canTranscribe) {
            return
        }
        canTranscribe = false
        try {
            printMessage("Reading wave samples... ")
            val data = decodeWaveFile(file)
            // whisper makes up text from silence, so it only sees the part with speech in it
            val speech = trimSilence(data)
            if (speech == null) {
                printMessage("No speech heard\n")
                canTranscribe = true
                return
            }
            printMessage("Trimmed ${(data.size - speech.size) / (16000 / 1000)} ms of silence\n")
            transcribeAudio(speech)
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    private suspend fun transcribeAudio(data: FloatArray) {
        try {
            printMessage("${data.size / (16000 / 1000)} ms\n")
            val nThreads = threads()
            printMessage("Transcribing data...\n")
            val start = System.currentTimeMillis()
            var text = engine?.transcribe(data, nThreads, language(), translateTo())?.trim()
            printMessage(text.toString())
            // remove special tokens like [MUSIC] or [BLANK_AUDIO]
            // bizarrely, it sometimes also does *music* or (music) so handle these case-s too
            // there's another case with flanking ♪ but it seems slightly different
            text = text?.replace(Regex("""\[[-_a-zA-Z0-9 ]*]"""), "")
            text = text?.replace(Regex("""\*[-_a-zA-Z0-9 ]*\*"""), "")
            text = text?.replace(Regex("""\([-_a-zA-Z0-9 ]*\)"""), "")
            // swapping words inside a sentence shouldn't leave the model's closing period behind
            val replacedText = replaced
            if (replacedText != null && replacedText.last() !in ".!?") {
                text = text?.trimEnd('.', ',', ';', ':')
            }
            if (checkBoolPref(R.string.casual_mode) == true) {
                printMessage("keeping it casual...\n")
                text = text?.trim()?.lowercase()
                // remove trailing punctuation but not e.g. ! or ?
                if (text?.length!! > 0 && text[text.lastIndex] in charArrayOf('.', ',', ';')) {
                    // < makes the range exclusive instead of inclusive, dropping the last char
                    text = text.slice(0..<text.lastIndex)
                }
            } else {
                val upper = sentenceStart
                if (upper != null) text = text?.let { matchFirstLetterCase(it, upper) }
            }
            // a search bar or address box wants "Maple Street", not "Maple Street."
            if (replaced == null && isSingleLineField(currentInputEditorInfo?.inputType ?: 0)) {
                text = text?.let { withoutClosingPunctuation(it) }
            }
            // add text only if non-empty
            // avoids adding just a single space for empty messages
            if (text?.length!! > 0) {
                val out = leading + text + trailing
                // text reaches the field only here, after the checkmark, as one commit
                synchronized(editLock) {
                    currentInputConnection.commitText(out, 1)
                    pushEdit(out, replaced.orEmpty())
                }
            }
            val elapsed = System.currentTimeMillis() - start
            printMessage("Done ($elapsed ms): \n$text\n")
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }

        canTranscribe = true
    }

    fun cancelRecord() = scope.launch {
        try {
            if (isRecording) {
                if (checkBoolPref(R.string.pause_media) == true) {
                    focusRequest.let { audioManager.abandonAudioFocusRequest(it) }
                }
                recorder.stopRecording()
                isRecording = false
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    private val onError = { e: Exception ->
        scope.launch {
            withContext(Dispatchers.Main) {
                printMessage("${e.localizedMessage}\n")
                isRecording = false
            }
        }
    }

    fun toggleRecord() = scope.launch {
        try {
            if (isRecording) {
                if (checkBoolPref(R.string.pause_media) == true) {
                    focusRequest.let { audioManager.abandonAudioFocusRequest(it) }
                }
                recorder.stopRecording()
                isRecording = false
                recordedFile?.let { transcribeFile(it) }
            } else {
                if (checkBoolPref(R.string.pause_media) == true) {
                    // Request focus
                    val result = audioManager.requestAudioFocus(focusRequest)
                    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        // Failed to gain audio focus
                        Log.w(LOG_TAG, "Failed to gain audio focus")
                        return@launch
                    }
                }
                leading = ""
                trailing = ""
                sentenceStart = null
                replaced = null
                val file = getTempFileForRecording()
                recorder.startRecording(file, onError)
                isRecording = true
                recordedFile = file
                // spacing and capitalization are decided here, before any preview is composing
                val inputType = currentInputEditorInfo?.inputType ?: 0
                val before = currentInputConnection.getTextBeforeCursor(64, 0)?.toString()
                val selected = currentInputConnection.getSelectedText(0)?.toString()
                if (!selected.isNullOrEmpty()) {
                    // replace the highlight as-is: no added spaces
                    replaced = selected
                } else if (!isEmailField(inputType)) {
                    // spacing is part of the committed string, not a separate commit
                    val charBefore = before?.lastOrNull()
                    if (charBefore != null && (charBefore.isLetterOrDigit() || charBefore in ".,;:!?)]}")) {
                        leading = " "
                    }
                    val charAfter =
                        currentInputConnection.getTextAfterCursor(1, 0)?.firstOrNull()
                    if (charAfter == null || !(charAfter.isWhitespace() || charAfter in ".,;:!?)]}")) {
                        trailing = " "
                    }
                }
                if (before != null && !skipsCapitalization(inputType)) {
                    sentenceStart = startsSentence(before)
                }

            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    private suspend fun getTempFileForRecording() = withContext(Dispatchers.IO) {
        File.createTempFile("recording", "wav")
    }

    fun backspace() {
        val ic = currentInputConnection ?: return
        synchronized(editLock) {
            val selected = ic.getSelectedText(0)?.toString()
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
                recordDeletion(selected)
                return
            }
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.isEmpty()) {
                // field doesn't expose its text; fall back to a key event
                sendKeyPress(KeyEvent.KEYCODE_DEL)
                return
            }
            val last = if (before.length >= 2 && Character.isLowSurrogate(before.last()) &&
                Character.isHighSurrogate(before[before.length - 2])
            ) before.takeLast(2) else before.takeLast(1)
            ic.deleteSurroundingTextInCodePoints(1, 0)
            recordDeletion(last)
        }
    }

    fun deleteWord() {
        val ic = currentInputConnection ?: return
        synchronized(editLock) {
            val selected = ic.getSelectedText(0)?.toString()
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
                recordDeletion(selected)
                return
            }
            val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            if (before.isEmpty()) {
                sendKeyPress(KeyEvent.KEYCODE_DEL)
                return
            }
            // skip whitespace before the cursor, then the word itself
            var i = before.length
            while (i > 0 && before[i - 1].isWhitespace()) i--
            while (i > 0 && !before[i - 1].isWhitespace()) i--
            ic.deleteSurroundingTextInCodePoints(before.codePointCount(i, before.length), 0)
            recordDeletion(before.substring(i))
        }
    }

    // pulled up on enter: the settings screen is the app's launcher activity
    fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun enter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val hasAction = action in intArrayOf(
            EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_NEXT
        )
        if (hasAction && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    // fallback for fields that ignore InputConnection edits
    fun sendKeyPress(key: Int) {
        currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }
}

private fun inputVariation(inputType: Int) = inputType and InputType.TYPE_MASK_VARIATION

private fun isEmailField(inputType: Int) =
    inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        inputVariation(inputType) in intArrayOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )

private fun isPasswordField(inputType: Int): Boolean {
    val variation = inputVariation(inputType)
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> variation in intArrayOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

// email, URI and password fields keep the model's text as spoken
private fun skipsCapitalization(inputType: Int): Boolean =
    isEmailField(inputType) || isPasswordField(inputType) ||
        (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputVariation(inputType) == InputType.TYPE_TEXT_VARIATION_URI)

// sentence start: empty field, after a newline, or after . ! ? (ignoring spaces and tabs)
private fun startsSentence(before: String): Boolean {
    val t = before.trimEnd { it != '\n' && it.isWhitespace() }
    return t.isEmpty() || t.last() == '\n' || t.last() in ".!?"
}

// only the first letter changes; "I", "I'm" and acronyms like "NASA" keep their capital
private fun matchFirstLetterCase(text: String, upper: Boolean): String {
    val i = text.indexOfFirst { !it.isWhitespace() }
    if (i < 0 || !text[i].isLetter()) return text
    val c = text[i]
    val changed = if (upper) {
        c.uppercaseChar()
    } else {
        val next = text.getOrNull(i + 1)
        val keep = next?.isUpperCase() == true || (c == 'I' && next?.isLetter() != true)
        if (keep) c else c.lowercaseChar()
    }
    return text.substring(0, i) + changed + text.substring(i + 1)
}

private suspend fun Context.copyData(
    assetDirName: String, destDir: File, printMessage: suspend (String) -> Unit
) = withContext(Dispatchers.IO) {
    assets.list(assetDirName)?.forEach { name ->
        val assetPath = "$assetDirName/$name"
        Log.v(LOG_TAG, "Processing $assetPath...")
        val destination = File(destDir, name)
        Log.v(LOG_TAG, "Copying $assetPath to $destination...")
        printMessage("Copying $name...\n")
        assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.v(LOG_TAG, "Copied $assetPath to $destination")
    }
}