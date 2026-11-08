package com.elara.launcher

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
 * How the drawer's backdrop is made from the wallpaper. Each is worked out once, when the wallpaper or the style
 * changes, into one small still picture, so all cost the same to draw: one stretched bitmap while the drawer moves.
 */
enum class BlurStyle(val label: Int, val dim: Float, val sheen: Boolean) {
    /** Even, heavy blur. */
    Gaussian(R.string.blur_gaussian, 0.32f, false),
    /** A light blur under a darker veil: the wallpaper stays recognisable. */
    Background(R.string.blur_background, 0.45f, false),
    /** Heavy blur, richer colours, light catching the top edge. */
    Glass(R.string.blur_glass, 0.22f, true),
    /** Nearly sharp in the middle, blurring towards the edges. */
    Radial(R.string.blur_radial, 0.3f, false),
    /** Nearly sharp at the top, blurring towards the bottom. */
    Gradient(R.string.blur_gradient, 0.3f, false);

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name } ?: Glass
    }
}

/**
 * The app drawer's blurred backdrop: a small copy of the home wallpaper, kept in the app's own storage, made into
 * the chosen [BlurStyle] and drawn behind the drawer. Android lets an app read the wallpaper only with all-files access, so
 * the copy is made when Elara sets the wallpaper, or, once that access is allowed (asked once, from the drawer),
 * read from Android whenever the wallpaper changes.
 *
 * Whether the copy still matches is told by the wallpaper's colours, which Android gives without a permission.
 */
object GlassWallpaper {
    /** The blurred wallpaper, null while there is none. */
    val image = MutableStateFlow<ImageBitmap?>(null)
    /** The small, unblurred copy every style is made from; null while the blur is off or there is none. */
    private var base: Bitmap? = null
    /** The style [image] was made in. */
    private var styledWith: BlurStyle? = null
    /** The style wanted; set by [load]. */
    @Volatile private var style = BlurStyle.Glass
    /** True when there is no copy, or the wallpaper has changed since it was made. */
    val stale = MutableStateFlow(false)

    private const val FileName = "wallpaper_base.png"
    /** Before blur styles, the copy was stored already blurred in glass style; still used until a new one is made. */
    private const val OldFileName = "glass_wallpaper.png"
    private const val Prefs = "glass_wallpaper"
    private const val ColorsKey = "colors"
    private const val SavedAtKey = "saved_at"
    private const val AskedKey = "asked"
    /** Width of the stored copy in px; it is blurred anyway, so a small one is enough and cheap to draw. */
    private const val Width = 144
    /** Right after Elara sets a wallpaper, Android may still report the old one's colours for a moment. */
    private const val SettleMs = 15_000L

    /**
     * Reads the stored copy, if any, makes it into [style], and checks it against the wallpaper ([width] x [height]:
     * the screen). Called only while the blur is on; [unload] frees it when turned off. Call off the main thread.
     */
    fun load(context: Context, width: Int, height: Int, style: BlurStyle) {
        this.style = style
        if (base == null) {
            val file = File(context.filesDir, FileName).takeIf { it.exists() } ?: File(context.filesDir, OldFileName)
            if (file.exists()) base = BitmapFactory.decodeFile(file.path)
        }
        // The old copy is blurred already, so styles that stay clear in places can't: remake it once, if Elara may read the wallpaper.
        if (File(context.filesDir, OldFileName).exists() && hasAccess(context) && fromSystem(context, width, height, fingerprint(context))) return
        restyle()
        check(context, width, height)
    }

    /** Makes [image] from [base] in [style], unless it already is. Some milliseconds on a 144 px copy. */
    @Synchronized
    private fun restyle() {
        val source = base ?: return
        if (styledWith == style && image.value != null) return
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = Bitmap.createBitmap(styled(pixels, w, h, style), w, h, Bitmap.Config.ARGB_8888)
        styledWith = style
        image.value = out.asImageBitmap()
    }

    /** Frees the copies in memory while the blur is off; the file stays for when it is turned on again. */
    @Synchronized
    fun unload() {
        image.value = null
        base = null
        styledWith = null
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
        Canvas(small).drawBitmap(source, Rect(0, 0, source.width, source.height), Rect(0, 0, Width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(Width * height)
        small.getPixels(pixels, 0, Width, 0, 0, Width, height)
        clampRows(pixels, Width, height, (top * scale).toInt() + 1, (bottom * scale).toInt() - 1)
        small.setPixels(pixels, 0, Width, 0, 0, Width, height)
        File(context.filesDir, FileName).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(context.filesDir, OldFileName).delete()
        prefs(context).edit().putString(ColorsKey, colors).putLong(SavedAtKey, SystemClock.elapsedRealtime()).apply()
        synchronized(this) {
            base = small
            styledWith = null
        }
        restyle()
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

/** [pixels] (opaque, [width] x [height]) made into [style]; [pixels] is left as it was. */
internal fun styled(pixels: IntArray, width: Int, height: Int, style: BlurStyle): IntArray {
    val out = pixels.copyOf()
    when (style) {
        BlurStyle.Gaussian -> boxBlur(out, width, height, 6)
        BlurStyle.Background -> boxBlur(out, width, height, 2)
        BlurStyle.Glass -> {
            // Colours a little richer, as light through glass.
            saturate(out, 1.35f)
            boxBlur(out, width, height, 6)
        }
        BlurStyle.Radial, BlurStyle.Gradient -> {
            // Blends a light and a heavy blur: the light one where the picture stays clear.
            val light = pixels.copyOf().also { boxBlur(it, width, height, 1) }
            boxBlur(out, width, height, 10)
            val cx = (width - 1) / 2f
            val cy = (height - 1) / 2f
            val reach = kotlin.math.sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
            for (y in 0 until height) for (x in 0 until width) {
                val t = if (style == BlurStyle.Radial) {
                    kotlin.math.hypot(x - cx, y - cy) / reach
                } else {
                    y / (height - 1).coerceAtLeast(1).toFloat()
                }
                val i = y * width + x
                out[i] = mix(light[i], out[i], t.coerceIn(0f, 1f))
            }
        }
    }
    return out
}

/** [a] to [b] by [t] (0..1), opaque. */
private fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int): Int {
        val x = (a shr shift) and 0xFF
        return (x + (((b shr shift) and 0xFF) - x) * t).toInt()
    }
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Pushes colours away from their grey by [amount] (1 keeps them). */
private fun saturate(pixels: IntArray, amount: Float) {
    for (i in pixels.indices) {
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val grey = 0.213f * r + 0.715f * g + 0.072f * b
        fun ch(c: Int) = (grey + (c - grey) * amount).toInt().coerceIn(0, 255)
        pixels[i] = (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
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
