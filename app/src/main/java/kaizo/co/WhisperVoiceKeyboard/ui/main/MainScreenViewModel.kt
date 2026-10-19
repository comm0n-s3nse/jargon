package kaizo.co.WhisperVoiceKeyboard.ui.main

import kotlinx.coroutines.CoroutineScope
import kaizo.co.WhisperVoiceKeyboard.models.languageFor
import kaizo.co.WhisperVoiceKeyboard.models.ModelStore
import kaizo.co.WhisperVoiceKeyboard.models.ModelEntry
import kaizo.co.WhisperVoiceKeyboard.models.ModelEngine
import kaizo.co.WhisperVoiceKeyboard.models.LANGUAGE_KEY
import kaizo.co.WhisperVoiceKeyboard.tune.Threads
import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercpp.whisper.WhisperContext
import kaizo.co.WhisperVoiceKeyboard.R
import kaizo.co.WhisperVoiceKeyboard.media.decodeShortArray
import kaizo.co.WhisperVoiceKeyboard.media.decodeWaveFile
import kaizo.co.WhisperVoiceKeyboard.recorder.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "MainScreenViewModel"

class MainScreenViewModel(private val application: Application) : ViewModel() {
    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set
    private val sharedPref = application.getSharedPreferences(
        application.getString(R.string.preference_file_key), Context.MODE_PRIVATE
    )
    private val maxThreads = Runtime.getRuntime().availableProcessors()
    private var recorder: Recorder = Recorder()
    private var whisperContext: WhisperContext? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null
    private var currentJob: Job? = null
    private var loadedModel: ModelEntry? = null

    init {
        viewModelScope.launch { printSystemInfo() }
        // the model loads when first needed, so opening settings does not keep a second copy
        canTranscribe = true
    }

    private suspend fun printSystemInfo() {
        printMessage(String.format("System Info: %s\n", WhisperContext.getSystemInfo()))
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
    }

    // loads the model chosen in settings, replacing the one in memory if that changed
    private suspend fun ensureModel() = withContext(Dispatchers.IO) {
        val store = ModelStore(application)
        // the test screen's benchmarks are whisper-only; a model from another engine falls back to the built-in
        val chosen = store.active(sharedPref) ?: throw IllegalStateException(application.getString(R.string.no_model_hint))
        val wanted = if (chosen.engine != ModelEngine.WHISPER_CPP) {
            store.list().firstOrNull { it.builtIn && it.engine == ModelEngine.WHISPER_CPP } ?: chosen
        } else chosen
        if (whisperContext != null && loadedModel?.id == wanted.id) return@withContext
        printMessage("Loading model ${wanted.fileName}...\n")
        whisperContext?.release()
        whisperContext = null
        whisperContext = store.openWhisper(wanted)
        loadedModel = wanted
        printMessage("Loaded model ${wanted.fileName}.\n")
    }

    fun benchmark() = viewModelScope.launch {
        val nThreads =
            Threads.effective(sharedPref, application.getString(R.string.num_threads), maxThreads, ModelStore(application).active(sharedPref)?.id ?: "")
        runBenchmark(nThreads)
    }

    private suspend fun runBenchmark(nthreads: Int) {
        if (!canTranscribe) {
            return
        }

        canTranscribe = false
        ensureModel()

        printMessage("Running benchmark. This will take minutes...\n")
        whisperContext?.benchMemory(nthreads)?.let { printMessage(it) }
        printMessage("\n")
        whisperContext?.benchGgmlMulMat(nthreads)?.let { printMessage(it) }

        canTranscribe = true
    }


    private suspend fun readAudioSamples(file: File): FloatArray = withContext(Dispatchers.IO) {
        stopPlayback()
        startPlayback(file)
        return@withContext decodeWaveFile(file)
    }

    private suspend fun stopPlayback() = withContext(Dispatchers.Main) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun startPlayback(file: File) = withContext(Dispatchers.Main) {
        mediaPlayer = MediaPlayer.create(application, file.absolutePath.toUri())
        mediaPlayer?.start()
    }

    private suspend fun transcribeAudio(data: FloatArray) {
        val nThreads =
            Threads.effective(sharedPref, application.getString(R.string.num_threads), maxThreads, loadedModel?.id)
        printMessage("Transcribing data...\n")
        val start = System.currentTimeMillis()
        val language = languageFor(
            loadedModel?.fileName.orEmpty(), sharedPref.getString(LANGUAGE_KEY, "en") ?: "en"
        )
        val text = whisperContext?.transcribeData(data, numThreads = nThreads, language = language)
        val elapsed = System.currentTimeMillis() - start
        printMessage("Done ($elapsed ms): \n$text\n")
    }

    private suspend fun transcribeFile(file: File) {
        if (!canTranscribe) {
            return
        }

        canTranscribe = false

        try {
            currentJob?.cancel()
            printMessage("Reading wave samples... ")
            val data = readAudioSamples(file)
            printMessage("${data.size / (16000 / 1000)} ms\n")
            transcribeAudio(data)
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }

        canTranscribe = true
    }

    private val onError = { e: Exception ->
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                printMessage("${e.localizedMessage}\n")
                isRecording = false
            }
        }
    }

    private val transcriptionCallback = { shortData: ShortArray ->
        if (currentJob == null) {
            currentJob = viewModelScope.launch {
                printMessage(shortData.size.toString())
                // convert ShortArray to FloatArray for transcription
                val floatData = decodeShortArray(shortData, 1)
                transcribeAudio(floatData)
                currentJob = null
            }
        }

    }

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                recorder.stopRecording()
                isRecording = false
                recordedFile?.let { transcribeFile(it) }
            } else {
                stopPlayback()
                ensureModel()
                val file = getTempFileForRecording()
                recorder.startRecording(file, onError, transcriptionCallback)
                isRecording = true
                recordedFile = file
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

    override fun onCleared() {
        // This runs on the main thread, so it must not wait for anything. The original blocked
        // here in runBlocking and then called stopPlayback(), which needs the main thread to run:
        // a deadlock that froze the app until Android gave up on it five seconds later, every time
        // the settings screen closed while the keyboard kept the app alive.
        val context = whisperContext
        whisperContext = null
        mediaPlayer?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // never started
            }
            it.release()
        }
        mediaPlayer = null
        // the recorder thread and the model outlive the screen, so free them, off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            recorder.stopRecording()
            context?.release()
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
}
