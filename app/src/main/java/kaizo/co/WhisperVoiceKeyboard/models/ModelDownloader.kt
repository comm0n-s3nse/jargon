package kaizo.co.WhisperVoiceKeyboard.models

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import kaizo.co.WhisperVoiceKeyboard.R

/**
 * Downloads a catalog model with Android's own download manager, which keeps going, resumes and
 * shows its notification even if this app is closed. When the file arrives it is copied into the
 * app's model folder while its SHA-256 is checked against the one the catalog pins; a file that
 * does not match is discarded. This is the only thing in the app that uses the network, and it only
 * happens after the person taps Download on a screen that says where from.
 */
class ModelDownloader(private val context: Context, private val store: ModelStore) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences(
        context.getString(R.string.preference_file_key), Context.MODE_PRIVATE
    )

    sealed interface State {
        data object Idle : State
        data class Running(val percent: Int) : State
        data object Done : State
        data class Failed(val reason: String) : State
    }

    private fun key(m: CatalogModel) = "download_${m.id}"
    private fun idFor(m: CatalogModel) = prefs.getLong(key(m), -1L)

    fun start(m: CatalogModel) {
        val url = requireNotNull(m.url) { "${m.name} is not downloadable" }
        require(url.startsWith("https://")) { "models are only downloaded over https" }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(m.name)
            .setDescription(context.getString(R.string.model_header))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "downloads", m.file + ".download")
        prefs.edit().putLong(key(m), dm.enqueue(request)).apply()
    }

    fun cancel(m: CatalogModel) {
        val id = idFor(m)
        if (id >= 0) dm.remove(id)
        prefs.edit().remove(key(m)).apply()
    }

    fun poll(m: CatalogModel): State {
        val id = idFor(m)
        if (id < 0) return State.Idle
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) {
                // removed from the notification shade or the Downloads app
                prefs.edit().remove(key(m)).apply()
                return State.Idle
            }
            return when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> State.Done
                DownloadManager.STATUS_FAILED -> State.Failed(
                    "error " + c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                )
                else -> {
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    State.Running(if (total > 0) (done * 100 / total).toInt() else 0)
                }
            }
        }
    }

    /** Moves a finished download into the model folder, checking its checksum. Throws with a readable message. */
    suspend fun finish(m: CatalogModel, onProgress: (copied: Long, total: Long) -> Unit): ModelEntry {
        val id = idFor(m)
        if (id < 0) throw ModelImportException("That download is no longer there.")
        val pfd: ParcelFileDescriptor = dm.openDownloadedFile(id)
        try {
            val entry = ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                store.installVerified(input, m.file, requireNotNull(m.sha256), m.sizeBytes, onProgress)
            }
            return entry
        } finally {
            cancel(m) // removes the download manager's copy, and our record of it
        }
    }
}
