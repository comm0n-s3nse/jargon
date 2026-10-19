package kaizo.co.WhisperVoiceKeyboard.models

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * Copies [input] to [output] and returns the SHA-256 of what went through, as lowercase hex, so a
 * download can be checked in the same pass that saves it. [onProgress] gets the bytes copied so far.
 */
fun copyWithSha256(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit = {}): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(1 shl 20)
    var copied = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
        output.write(buffer, 0, n)
        copied += n
        onProgress(copied)
    }
    return digest.digest().toHex()
}
