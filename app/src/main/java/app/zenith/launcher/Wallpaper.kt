package app.zenith.launcher

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Change the wallpaper: pick a photo with the system photo picker (no storage permission), then
 * place it in [WallpaperActivity] and choose home screen, lock screen or both. Before Android 9
 * (no ImageDecoder to turn photos upright) it goes to the system's crop-and-set screen instead.
 * The live-wallpaper-only SET_WALLPAPER pickers some OEMs ship (Samsung) are not used.
 */
@Composable
fun rememberWallpaperPicker(): () -> Unit {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.startActivity(Intent(context, WallpaperActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } else {
            cropAndSet(context, uri)
        }
    }
    return { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Placing a photo of [width] by [height] on a screen of [screenWidth] by [screenHeight], all in pixels. */
class WallpaperFit(val width: Int, val height: Int, val screenWidth: Int, val screenHeight: Int) {
    /** The scale at which the photo just covers the screen; zoom goes from it to [MaxZoom] times it. */
    val cover = maxOf(screenWidth.toFloat() / width, screenHeight.toFloat() / height)

    /** Where the photo's top-left corner sits on screen at [scale], centred. */
    fun centred(scale: Float) = Pair((screenWidth - width * scale) / 2, (screenHeight - height * scale) / 2)

    fun clampScale(scale: Float) = scale.coerceIn(cover, cover * MaxZoom)

    /** [x], [y] (top-left corner) kept so the photo at [scale] still covers the screen. */
    fun clamp(x: Float, y: Float, scale: Float) = Pair(
        x.coerceIn(minOf(0f, screenWidth - width * scale), 0f),
        y.coerceIn(minOf(0f, screenHeight - height * scale), 0f),
    )

    /** The part of the photo on screen, as left, top, right, bottom in photo pixels. */
    fun visible(x: Float, y: Float, scale: Float): List<Int> = listOf(
        (-x / scale).toInt().coerceIn(0, width),
        (-y / scale).toInt().coerceIn(0, height),
        ((screenWidth - x) / scale).toInt().coerceIn(0, width),
        ((screenHeight - y) / scale).toInt().coerceIn(0, height),
    )

    companion object {
        const val MaxZoom = 4f

        /**
         * The largest power-of-two sample that keeps the photo at least screen-sized. Compares short
         * sides and long sides, so a photo still to be turned upright gets the same answer.
         */
        fun sampleSize(width: Int, height: Int, screenWidth: Int, screenHeight: Int): Int {
            val short = minOf(width, height)
            val long = maxOf(width, height)
            val screenShort = minOf(screenWidth, screenHeight)
            val screenLong = maxOf(screenWidth, screenHeight)
            var sample = 1
            while (short / (sample * 2) >= screenShort && long / (sample * 2) >= screenLong) sample *= 2
            return sample
        }
    }
}

/** The system's crop-and-set screen, or the "Set as" chooser galleries use. */
private fun cropAndSet(context: Context, uri: Uri) {
    val crop = try {
        WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri)
    } catch (_: IllegalArgumentException) {
        null // No system cropper for this image or device.
    }
    val intent = crop ?: Intent.createChooser(
        Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, "image/*").putExtra("mimeType", "image/*"),
        context.getString(R.string.settings_wallpaper),
    )
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
}
