package com.elara.launcher

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The font the user picked, copied into the app's private storage so it outlives the file it came
 * from. One at a time; [LauncherSettings.fontVersion] says which copy is current.
 */
object CustomFont {
    /** Picker types: fonts often come labelled as plain binary, so that is allowed too and checked here instead. */
    val MimeTypes = arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt", "application/octet-stream")

    private fun file(context: Context) = File(context.filesDir, "font")

    /** Copies [uri] in and checks Android can read it as a font; its file name (maybe blank), or null when it isn't a usable font. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val temp = File(context.filesDir, "font.new")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> copyCapped(input, output) }
            } ?: return@withContext null
            if (!copied || Typeface.Builder(temp).build() == null) return@withContext null
            if (!temp.renameTo(file(context))) return@withContext null
            cached = null
            displayName(context, uri)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } finally {
            temp.delete()
        }
    }

    fun clear(context: Context) {
        file(context).delete()
        cached = null
    }

    // Kept for the process: the home screen, settings and wallpaper screens share one parsed font.
    // shortcut: parsed on the main thread on first use (a few ms for a typical TTF); move off it if large fonts stutter startup.
    private var cached: Pair<Long, FontFamily?>? = null

    /** The picked font for [version], or null for the system font (none picked, or the file is gone). */
    fun family(context: Context, version: Long): FontFamily? {
        if (version == 0L) return null
        cached?.let { (v, family) -> if (v == version) return family }
        val f = file(context)
        val family = if (f.exists()) Typeface.Builder(f).build()?.let { FontFamily(it) } else null
        cached = version to family
        return family
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.substringBeforeLast('.').orEmpty()

    // False when the file is larger than any reasonable font, so a wrong pick can't fill the storage.
    private fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream): Boolean {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return true
            total += n
            if (total > MaxBytes) return false
            output.write(buffer, 0, n)
        }
    }

    private const val MaxBytes = 32L * 1024 * 1024
}
