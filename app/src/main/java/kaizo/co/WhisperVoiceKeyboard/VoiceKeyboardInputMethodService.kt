package kaizo.co.WhisperVoiceKeyboard

import android.content.Intent
import kaizo.co.WhisperVoiceKeyboard.models.languageFor
import kaizo.co.WhisperVoiceKeyboard.models.ModelStore
import kaizo.co.WhisperVoiceKeyboard.models.ModelEntry
import kaizo.co.WhisperVoiceKeyboard.models.LANGUAGE_KEY
import kaizo.co.WhisperVoiceKeyboard.models.TRANSLATE_KEY
import kaizo.co.WhisperVoiceKeyboard.ui.main.ENTER_HINT_KEY
import kaizo.co.WhisperVoiceKeyboard.ui.main.VOCABULARY_KEY
import kaizo.co.WhisperVoiceKeyboard.models.RECENT_TRANSLATE_MAX
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kaizo.co.WhisperVoiceKeyboard.audio.Chunker
import com.transcribecpp.LangIdContext
import kaizo.co.WhisperVoiceKeyboard.models.LANG_ID_FILE
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "VoiceKeyboardInputMethodService"

// how much text either side of the cursor identifies an edit, and how many edits are kept
private const val CONTEXT_CHARS = 12
private const val MAX_EDITS = 8

// "Offline agent coming soon!" in the languages the models speak; the fallback is English
private val COMING_SOON = mapOf(
    "en" to "Offline agent coming soon!", "pt" to "Agente offline em breve!", "es" to "¡Agente sin conexión, muy pronto!",
    "fr" to "Agent hors ligne, bientôt !", "de" to "Offline-Agent kommt bald!", "it" to "Agente offline in arrivo!",
    "nl" to "Offline agent komt eraan!", "pl" to "Agent offline już wkrótce!", "sl" to "Agent brez povezave kmalu!",
    "sv" to "Offline-agent kommer snart!", "ru" to "Офлайн-агент скоро!", "uk" to "Офлайн-агент незабаром!",
    "cs" to "Offline agent již brzy!", "da" to "Offline-agent kommer snart!", "fi" to "Offline-agentti tulossa pian!",
    "hu" to "Offline ügynök hamarosan!", "ro" to "Agent offline în curând!", "el" to "Σύντομα πράκτορας εκτός σύνδεσης!",
    "tr" to "Çevrimdışı ajan yakında!", "ja" to "オフラインエージェント、近日公開！", "ko" to "오프라인 에이전트 곧 출시!",
    "zh" to "离线助手即将推出！", "ar" to "الوكيل دون اتصال قريبًا!", "hi" to "ऑफ़लाइन एजेंट जल्द ही!",
)

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
        enterHint = sharedPref?.getBoolean(ENTER_HINT_KEY, true) ?: true
        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == ENTER_HINT_KEY) enterHint = p.getBoolean(ENTER_HINT_KEY, true)
        }.also { sharedPref?.registerOnSharedPreferenceChangeListener(it) }

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
        val leavingDetector = langId
        langId = null
        if (leavingDetector != null) scope.launch { try { leavingDetector.release() } catch (e: Exception) { Log.w(LOG_TAG, e) } }
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
    // While a recording runs, finished pieces of it are transcribed in the background (see Chunker),
    // so stopping leaves only the last piece to do. One engine call at a time.
    private val engineLock = Mutex()
    // the language detector, loaded on first need and kept; and what it heard in this recording
    private var langId: LangIdContext? = null
    private var detected: String? = null
    private var chunker: Chunker? = null
    private val chunkTexts: MutableList<Deferred<String?>> = Collections.synchronizedList(mutableListOf())
    private var recorder: Recorder = Recorder()
    var canTranscribe by mutableStateOf(false)
        private set
    var isRecording by mutableStateOf(false)
    var micLevel by mutableStateOf(0f) // how loud the mic is right now, 0 to 1, for the level bars
    var enterHint by mutableStateOf(true) // the small gear on the enter key (Appearance)
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
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
    ) {
        val isDictation get() = inserted.isNotEmpty()
    }

    private val editLock = Any()
    private val edits = ArrayList<Edit>()
    private var runEdit: Edit? = null
    // the dictation the last drag-left took out, for drag-right to put back
    private var redo: Edit? = null

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

    // Takes out the last dictation, whatever backspacing happened since: whatever of its text is
    // still in front of the cursor goes, and what it had replaced comes back. Backspace edits in
    // between are dropped; they are not what a drag on the backspace key means.
    private fun undoDictation(): Boolean = synchronized(editLock) {
        val ic = currentInputConnection ?: return false
        val idx = edits.indexOfLast { it.isDictation }
        if (idx < 0) return false
        val edit = edits[idx]
        val before = ic.getTextBeforeCursor(edit.inserted.length + CONTEXT_CHARS, 0)?.toString().orEmpty()
        // the longest start of the dictation that still ends at the cursor
        var keep = minOf(edit.inserted.length, before.length)
        while (keep > 0 && !before.endsWith(edit.inserted.substring(0, keep))) keep--
        val remaining = edit.inserted.substring(0, keep)
        if (remaining.isBlank()) {
            edits.clear()
            return false
        }
        ic.deleteSurroundingTextInCodePoints(remaining.codePointCount(0, remaining.length), 0)
        if (edit.deleted.isNotEmpty()) ic.commitText(edit.deleted, 1)
        while (edits.size > idx) edits.removeAt(edits.lastIndex)
        redo = Edit(remaining, edit.deleted, "", "")
        true
    }

    // Drag-left on backspace. The touch itself deleted a character, so a plain drag takes that
    // back and then undoes the last dictation. If the touch had turned into a hold (repeated),
    // the drag undoes just that held run.
    fun undoAfterBackspaceGesture(repeated: Boolean) {
        synchronized(editLock) {
            val own = runEdit
            if (own != null && edits.lastOrNull() === own) undoOne()
            if (!repeated) undoDictation()
            runEdit = null
        }
    }

    // Drag-right on backspace: the dictation the last drag-left took out comes back, in place of
    // what it had replaced. The touch's own deleted character is restored first.
    fun redoAfterBackspaceGesture() {
        synchronized(editLock) {
            val own = runEdit
            if (own != null && edits.lastOrNull() === own) undoOne()
            runEdit = null
            val ic = currentInputConnection ?: return
            val r = redo ?: return
            redo = null
            if (r.deleted.isNotEmpty()) {
                val before = ic.getTextBeforeCursor(r.deleted.length, 0)?.toString().orEmpty()
                if (before != r.deleted) return
                ic.deleteSurroundingTextInCodePoints(r.deleted.codePointCount(0, r.deleted.length), 0)
            }
            ic.commitText(r.inserted, 1)
            val (before, after) = contextNow()
            edits.add(Edit(r.inserted, r.deleted, before, after))
            if (edits.size > MAX_EDITS) edits.removeAt(0)
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
        if (wanted == null) {
            // a copy that shipped without a model, before the first download: the key says what to do
            engine?.release()
            engine = null
            loadedModel = null
            canTranscribe = false
            statusText = getString(R.string.no_model_hint)
            return
        }
        if (statusText == getString(R.string.no_model_hint)) statusText = null
        val needsLoad = engine == null || loadedModel?.id != wanted.id
        val userKey = application.getString(R.string.num_threads)
        if (!needsLoad && !Threads.needsTuning(prefs, userKey, wanted.id)) return
        busy = true
        canTranscribe = false
        try {
            if (needsLoad) loadModel(store, wanted)
            tuneIfNeeded()
            preloadDetector(store)
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
                entry = store.list().firstOrNull { it.builtIn } ?: throw e
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
        // "Same" is the language being spoken, so that one is not offered again; English always is
        val spoken = language()
        val targets = (recentTranslateTargets(prefs) + "en").distinct().filter { it != spoken }.take(RECENT_TRANSLATE_MAX)
        return listOf("" to application.getString(R.string.quick_same)) + targets.map { it to it.uppercase() }
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

    // With Auto on a Canary model, the detector is loaded with the model rather than on the first
    // dictation, so that one does not pay for it (about a second on a mid-range phone).
    private suspend fun preloadDetector(store: ModelStore) = withContext(Dispatchers.IO) {
        val speech = engine ?: return@withContext
        if (langId != null || language() != "auto" || speech.canDetectLanguage) return@withContext
        val file = store.installed(LANG_ID_FILE) ?: return@withContext
        try {
            langId = LangIdContext.load(file.absolutePath)
            printMessage("Loaded the language detector.\n")
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
        }
    }

    // "Auto" on a model that cannot tell languages apart: the detector decides once per recording,
    // from the first piece it sees, among the languages the model knows. Without the detector
    // installed, English, as before.
    private suspend fun spokenLanguage(data: FloatArray): String {
        val setting = language()
        val speech = engine ?: return setting
        if (setting != "auto" || speech.canDetectLanguage) return setting
        detected?.let { return it }
        val guess = try {
            val file = ModelStore(application).installed(LANG_ID_FILE)
            if (file == null) null else {
                val det = langId ?: LangIdContext.load(file.absolutePath).also { langId = it }
                det.detect(data, speech.languages)?.also {
                    printMessage("language: ${it.code} (p=${"%.2f".format(it.p)}, in-set ${"%.2f".format(it.allowedMass)})\n")
                }?.code
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            null
        }
        return (guess ?: "en").also { detected = it }
    }

    // Double-tap on the mic: the offline agent is not here yet; the strip says so, in the language
    // the person speaks (the "I speak" setting, English when it is Auto).
    var popup by mutableStateOf<String?>(null)
    fun showAgentPopup() {
        val code = language().takeIf { it != "auto" } ?: "en"
        popup = COMING_SOON[code] ?: COMING_SOON["en"]!!
        scope.launch { delay(2600); popup = null }
    }

    // the names and terms taught in Settings, one per line
    private fun vocabulary(): List<String> =
        sharedPref?.getString(VOCABULARY_KEY, null)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

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

    /** The engine's text for [data], with its special tokens removed; null without an engine. */
    private suspend fun recognize(data: FloatArray): String? {
        val digits = sharedPref?.getBoolean(getString(R.string.digits), true) != false
        val raw = engineLock.withLock { engine?.transcribe(data, threads(), spokenLanguage(data), translateTo(), vocabulary(), digits) }?.trim() ?: return null
        // remove special tokens like [MUSIC] or [BLANK_AUDIO]
        // bizarrely, it sometimes also does *music* or (music) so handle these case-s too
        // there's another case with flanking ♪ but it seems slightly different
        return raw.replace(Regex("""\[[-_a-zA-Z0-9 ]*]"""), "")
            .replace(Regex("""\*[-_a-zA-Z0-9 ]*\*"""), "")
            .replace(Regex("""\([-_a-zA-Z0-9 ]*\)"""), "")
    }

    // a piece of a running recording: transcribed now, joined with the rest when the recording stops
    private fun transcribePiece(samples: FloatArray) {
        chunkTexts += scope.async {
            val speech = trimSilence(samples) ?: return@async null
            try {
                recognize(speech)?.trim()?.takeIf { it.isNotEmpty() }.also { printMessage("piece ${samples.size / 16} ms: $it\n") }
            } catch (e: Exception) { Log.w(LOG_TAG, e); null }
        }
    }

    // When the recording stops: wait for the pieces already in flight, do the last one, join, commit.
    private suspend fun finishRecording() {
        if (!canTranscribe) return
        canTranscribe = false
        try {
            val tail = chunker?.flush()
            chunker = null
            val earlier = synchronized(chunkTexts) { chunkTexts.toList().also { chunkTexts.clear() } }
            val start = System.currentTimeMillis()
            printMessage("${earlier.size} pieces done early, ${(tail?.size ?: 0) / 16} ms left\n")
            val parts = mutableListOf<String>()
            for (d in earlier) d.await()?.let { parts += it }
            val tailSpeech = tail?.let { trimSilence(it) }
            if (tailSpeech != null) recognize(tailSpeech)?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
            if (parts.isEmpty()) {
                printMessage("No speech heard\n")
                canTranscribe = true
                return
            }
            commitRecognized(joinPieces(parts), start)
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
        canTranscribe = true
    }

    private fun discardPieces() {
        chunker = null
        synchronized(chunkTexts) {
            chunkTexts.forEach { it.cancel() }
            chunkTexts.clear()
        }
    }

    private suspend fun transcribeAudio(data: FloatArray) {
        try {
            printMessage("${data.size / (16000 / 1000)} ms\n")
            printMessage("Transcribing data...\n")
            val start = System.currentTimeMillis()
            commitRecognized(recognize(data), start)
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }

        canTranscribe = true
    }

    // spacing, case, spoken marks, then the one commit into the field
    private fun commitRecognized(recognized: String?, start: Long) {
        try {
            var text = recognized
            printMessage(text.toString())
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
            // one word is a name or an answer, not a sentence: "Canary", not "Canary."
            text = text?.let { withoutLoneWordPeriod(it) }
            // a search bar or address box wants "Maple Street", not "Maple Street."
            if (replaced == null && isSingleLineField(currentInputEditorInfo?.inputType ?: 0)) {
                text = text?.let { withoutClosingPunctuation(it) }
            }
            // "question mark", "smiley face": the words people say for what they cannot type
            var attachLeft = false
            if (text != null && text.isNotEmpty()) {
                if (sharedPref?.getBoolean(getString(R.string.spoken_emoji), true) != false) {
                    text = SpokenText.emoji(text)
                }
                if (sharedPref?.getBoolean(getString(R.string.spoken_punctuation), true) != false) {
                    val spoken = SpokenText.punctuation(text, capitalize = checkBoolPref(R.string.casual_mode) != true)
                    text = spoken.text
                    attachLeft = spoken.attachLeft && replaced == null
                    if (spoken.bare) {
                        // "space" or "new line" on its own: exactly that, nothing added around it
                        leading = ""
                        trailing = ""
                    } else if (text.endsWith("\n")) {
                        trailing = ""
                    }
                }
                text = SpokenText.chatCase(text)
                if (sharedPref?.getBoolean(getString(R.string.digits), true) != false) text = Numbers.digits(text)
            }
            // add text only if non-empty
            // avoids adding just a single space for empty messages
            if (text?.length!! > 0) {
                // text reaches the field only here, after the checkmark, as one commit
                synchronized(editLock) {
                    val ic = currentInputConnection
                    var deleted = replaced.orEmpty()
                    if (attachLeft) {
                        // punctuation goes on the word before the cursor: no space in front of it, and the
                        // space the last dictation left behind comes out
                        leading = ""
                        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                        if (before.endsWith(" ") && before.length == 2 && !before[0].isWhitespace()) {
                            ic.deleteSurroundingText(1, 0)
                            deleted = " "
                        }
                    }
                    val out = leading + text + trailing
                    ic.commitText(out, 1)
                    pushEdit(out, deleted)
                }
            }
            val elapsed = System.currentTimeMillis() - start
            printMessage("Done ($elapsed ms): \n$text\n")
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    // Each piece starts with a capital from the model; inside a sentence that is wrong.
    private fun joinPieces(parts: List<String>): String {
        val out = StringBuilder()
        for (p in parts) {
            val piece = if (out.isNotEmpty() && out.last() !in ".!?\n") matchFirstLetterCase(p, upper = false) else p
            if (out.isNotEmpty()) out.append(' ')
            out.append(piece)
        }
        return out.toString()
    }

    fun cancelRecord() = scope.launch {
        try {
            if (isRecording) {
                if (checkBoolPref(R.string.pause_media) == true) {
                    focusRequest.let { audioManager.abandonAudioFocusRequest(it) }
                }
                recorder.stopRecording()
                isRecording = false
                discardPieces()
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
                finishRecording()
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
                micLevel = 0f
                detected = null
                discardPieces()
                val pieces = Chunker { transcribePiece(it) }
                chunker = pieces
                recorder.startRecording(file, onError, onLevel = { micLevel = it }, onAudio = { buf, n -> pieces.add(buf, n) })
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
                // a capital only for a known reason: the start of the field, a newline, or . ! ?
                // before the cursor. A field that will not show its text gets lowercase, not the
                // model's habit of capitalizing everything.
                if (!skipsCapitalization(inputType)) {
                    val first = selected?.firstOrNull { !it.isWhitespace() }
                    sentenceStart = when {
                        // replacing a highlight: the new words take the case the old ones had
                        first != null && first.isLetter() -> first.isUpperCase()
                        else -> before?.let { startsSentence(it) } ?: false
                    }
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