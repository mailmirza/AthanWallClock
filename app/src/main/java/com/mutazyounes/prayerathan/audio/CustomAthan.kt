package com.mutazyounes.prayerathan.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * The user's own athan recording. The picked file is copied into app storage,
 * so playback never depends on the original staying put or on a URI grant
 * surviving a reboot.
 */
object CustomAthan {
    const val ID = "custom"
    const val MAX_BYTES = 30L * 1024L * 1024L
    private const val FILE_NAME = "custom_athan"
    private const val TEMP_NAME = "custom_athan.tmp"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).let { it.isFile && it.length() > 0L }

    /** Copies and checks the file. Returns its display name, or null if unusable. */
    fun import(context: Context, uri: Uri): String? {
        val temp = File(context.filesDir, TEMP_NAME)
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return@use false
                        output.write(buffer, 0, read)
                    }
                    total > 0L
                }
            } ?: false
            if (!copied || !isPlayable(temp)) {
                temp.delete()
                return null
            }
            val target = file(context)
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                return null
            }
            return displayName(context, uri) ?: "Your own file"
        } catch (_: Exception) {
            temp.delete()
            return null
        }
    }

    private fun isPlayable(file: File): Boolean {
        val probe = MediaPlayer()
        return try {
            probe.setDataSource(file.absolutePath)
            probe.prepare()
            probe.duration > 0
        } catch (_: Exception) {
            false
        } finally {
            probe.release()
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
