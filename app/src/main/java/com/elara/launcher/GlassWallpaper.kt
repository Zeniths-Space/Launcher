package com.elara.launcher

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The app drawer's frosted glass: a small, blurred, colour-boosted copy of the home wallpaper, kept in the app's
 * own storage and drawn behind the drawer. Android lets an app read the wallpaper only with all-files access, so
 * the copy is made when Elara sets the wallpaper, or, once that access is allowed (asked once, from the drawer),
 * read from Android whenever the wallpaper changes.
 *
 * Whether the copy still matches is told by the wallpaper's colours, which Android gives without a permission.
 */
object GlassWallpaper {
    /** The blurred wallpaper, null while there is none. */
    val image = MutableStateFlow<ImageBitmap?>(null)
    /** True when there is no copy, or the wallpaper has changed since it was made. */
    val stale = MutableStateFlow(false)

    private const val FileName = "glass_wallpaper.png"
    private const val Prefs = "glass_wallpaper"
    private const val ColorsKey = "colors"
    private const val SavedAtKey = "saved_at"
    private const val AskedKey = "asked"
    /** Width of the stored copy in px; it is blurred anyway, so a small one is enough and cheap to draw. */
    private const val Width = 144
    private const val BlurRadius = 6
    /** Colours a little richer, as light through glass. */
    private const val Saturation = 1.35f
    /** Right after Elara sets a wallpaper, Android may still report the old one's colours for a moment. */
    private const val SettleMs = 15_000L

    /**
     * Reads the stored copy, if any, and checks it against the wallpaper ([width] x [height]: the screen). Called only
     * while the blur is on; [unload] frees it when turned off. Call off the main thread.
     */
    fun load(context: Context, width: Int, height: Int) {
        val file = File(context.filesDir, FileName)
        if (image.value == null && file.exists()) image.value = BitmapFactory.decodeFile(file.path)?.asImageBitmap()
        check(context, width, height)
    }

    /** Frees the copy in memory while the blur is off; the file stays for when it is turned on again. */
    fun unload() {
        image.value = null
        stale.value = false
    }

    /** Whether Android lets Elara read the wallpaper: all-files access (Android 11+), else storage read. */
    fun hasAccess(context: Context) =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Whether the drawer already asked for access; it asks once. */
    fun asked(context: Context) = prefs(context).getBoolean(AskedKey, false)

    fun setAsked(context: Context) = prefs(context).edit().putBoolean(AskedKey, true).apply()

    /**
     * Makes and stores the copy from [source], a picture of the home wallpaper as the screen shows it. Rows above
     * [top] and below [bottom] are filled from the rows next to them. [colors] is the wallpaper's fingerprint, or
     * null to take it on the next check (Elara just set it).
     * Call off the main thread.
     */
    fun save(context: Context, source: Bitmap, colors: String?, top: Int = 0, bottom: Int = source.height) {
        val height = (Width.toLong() * source.height / source.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createBitmap(Width, height, Bitmap.Config.ARGB_8888)
        val scale = Width.toFloat() / source.width
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(Saturation) }) }
        Canvas(small).drawBitmap(source, Rect(0, 0, source.width, source.height), Rect(0, 0, Width, height), paint)
        val pixels = IntArray(Width * height)
        small.getPixels(pixels, 0, Width, 0, 0, Width, height)
        clampRows(pixels, Width, height, (top * scale).toInt() + 1, (bottom * scale).toInt() - 1)
        boxBlur(pixels, Width, height, BlurRadius)
        small.setPixels(pixels, 0, Width, 0, 0, Width, height)
        File(context.filesDir, FileName).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        prefs(context).edit().putString(ColorsKey, colors).putLong(SavedAtKey, SystemClock.elapsedRealtime()).apply()
        image.value = small.asImageBitmap()
        stale.value = false
    }

    /**
     * Compares the copy with the wallpaper now set, and remakes it from Android's wallpaper when it no longer
     * matches and Elara may read it. Call off the main thread.
     */
    fun check(context: Context, width: Int, height: Int) {
        val now = fingerprint(context)
        if (image.value == null) {
            stale.value = !(hasAccess(context) && fromSystem(context, width, height, now))
            return
        }
        if (now == null) return // Android 8.0, or no colours yet: trust the copy.
        val prefs = prefs(context)
        val saved = prefs.getString(ColorsKey, null)
        val savedAt = prefs.getLong(SavedAtKey, 0)
        val settling = SystemClock.elapsedRealtime() - savedAt in 0..SettleMs
        if (saved == null || settling) {
            // Just set by Elara: these are its colours.
            prefs.edit().putString(ColorsKey, now).apply()
            stale.value = false
        } else {
            // shortcut: two wallpapers with the same three main colours count as one; a pixel hash would need the image.
            stale.value = saved != now && !(hasAccess(context) && fromSystem(context, width, height, now))
        }
    }

    /**
     * Makes the copy from Android's home wallpaper, placed as the home screen shows it (filling a [width] x
     * [height] screen, centred). False when it can't be read (no access, a live wallpaper).
     */
    private fun fromSystem(context: Context, width: Int, height: Int, colors: String?): Boolean {
        // A quarter of the screen is plenty for a copy that ends up 144 px wide.
        val w = (width / 4).coerceAtLeast(1)
        val h = (height / 4).coerceAtLeast(1)
        val manager = WallpaperManager.getInstance(context)
        val drawable = try {
            // The file, decoded at a fraction of its size: a full wallpaper is tens of MB to decode.
            sampled(manager, w, h)?.let { BitmapDrawable(context.resources, it) }
                // No file (the built-in default wallpaper): Android decodes it whole.
                ?: if (Build.VERSION.SDK_INT >= 34) manager.getDrawable(WallpaperManager.FLAG_SYSTEM) else manager.drawable
        } catch (_: SecurityException) {
            null
        } ?: return false
        val screen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dw = drawable.intrinsicWidth
        val dh = drawable.intrinsicHeight
        if (dw > 0 && dh > 0) {
            val scale = maxOf(w.toFloat() / dw, h.toFloat() / dh)
            val sw = (dw * scale).toInt()
            val sh = (dh * scale).toInt()
            drawable.setBounds((w - sw) / 2, (h - sh) / 2, (w - sw) / 2 + sw, (h - sh) / 2 + sh)
        } else {
            drawable.setBounds(0, 0, w, h)
        }
        (drawable as? BitmapDrawable)?.paint?.isFilterBitmap = true
        drawable.draw(Canvas(screen))
        save(context, screen, colors)
        return true
    }

    /** The home wallpaper's file decoded at the largest power-of-two step down that still covers [w] x [h]. */
    private fun sampled(manager: WallpaperManager, w: Int, h: Int): Bitmap? {
        val file = manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null
        return file.use {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
            // Back to the start: the bounds pass read the header.
            android.system.Os.lseek(it.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    /** The home wallpaper's main colours, as Android reports them; null before Android 8.1 or while unknown. */
    fun fingerprint(context: Context): String? {
        if (Build.VERSION.SDK_INT < 27) return null
        val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
        return listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor).joinToString(",") { it.toArgb().toString() }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
}

/** Copies row [first] over the rows above it and row [last] over the rows below it. */
internal fun clampRows(pixels: IntArray, width: Int, height: Int, first: Int, last: Int) {
    val top = first.coerceIn(0, height - 1)
    val bottom = last.coerceIn(top, height - 1)
    for (y in 0 until top) System.arraycopy(pixels, top * width, pixels, y * width, width)
    for (y in bottom + 1 until height) System.arraycopy(pixels, bottom * width, pixels, y * width, width)
}

/** Three box-blur passes each way (close to a Gaussian), edges clamped, alpha kept opaque. */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
    val tmp = IntArray(pixels.size)
    repeat(3) {
        blurLine(pixels, tmp, width, height, radius, horizontal = true)
        blurLine(tmp, pixels, width, height, radius, horizontal = false)
    }
}

private fun blurLine(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val span = 2 * radius + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return if (horizontal) src[line * width + c] else src[c * width + line]
        }
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val p = at(i)
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        for (i in 0 until length) {
            val out = (0xFF shl 24) or ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
            if (horizontal) dst[line * width + i] = out else dst[i * width + line] = out
            val add = at(i + radius + 1)
            val drop = at(i - radius)
            r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
            b += (add and 0xFF) - (drop and 0xFF)
        }
    }
}
