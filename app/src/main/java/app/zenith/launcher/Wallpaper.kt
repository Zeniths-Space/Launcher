package app.zenith.launcher

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Change the wallpaper: pick a photo with the system photo picker (no storage permission), then
 * choose home screen, lock screen or both. The photo is centre-cropped to the screen's shape.
 * Before Android 9 (no ImageDecoder to apply photo rotation) it goes to the system's crop-and-set
 * screen instead, which sets both. The live-wallpaper-only SET_WALLPAPER pickers some OEMs ship
 * (Samsung) are not used.
 */
@Composable
fun rememberWallpaperPicker(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) picked = uri else cropAndSet(context, uri)
    }
    picked?.let { uri ->
        val set = { which: Int ->
            picked = null
            scope.launch {
                val ok = withContext(Dispatchers.IO) { setWallpaper(context, uri, which) }
                Toast.makeText(context, if (ok) R.string.wallpaper_set else R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
            }
            Unit
        }
        AlertDialog(
            onDismissRequest = { picked = null },
            icon = { Icon(Icons.Wallpaper, contentDescription = null) },
            title = { Text(stringResource(R.string.wallpaper_where)) },
            text = {
                Column {
                    TextButton(onClick = { set(WallpaperManager.FLAG_SYSTEM) }) { Text(stringResource(R.string.wallpaper_home)) }
                    TextButton(onClick = { set(WallpaperManager.FLAG_LOCK) }) { Text(stringResource(R.string.wallpaper_lock)) }
                    TextButton(onClick = { set(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }) { Text(stringResource(R.string.wallpaper_both)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picked = null }) { Text(stringResource(R.string.rename_cancel)) } },
            containerColor = if (LocalPureBlack.current) PureBlackCard else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
        )
    }
    return { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Decodes [uri] at about screen size, upright, and sets it on [which] screens. False if it can't. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
private fun setWallpaper(context: Context, uri: Uri, which: Int): Boolean {
    val (sw, sh) = screenSize(context)
    return try {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(WallpaperFit.sampleSize(info.size.width, info.size.height, sw, sh))
        }
        WallpaperManager.getInstance(context).setBitmap(bitmap, WallpaperFit.centerCrop(bitmap.width, bitmap.height, sw, sh).let { (l, t, r, b) -> Rect(l, t, r, b) }, true, which)
        bitmap.recycle()
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false // Wallpaper changes blocked, e.g. by a device policy.
    }
}

private fun screenSize(context: Context): Pair<Int, Int> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }

/** Fitting a photo to the screen. */
object WallpaperFit {
    /**
     * The largest power-of-two sample that keeps the photo at least screen-sized. Compares short
     * sides and long sides, so a photo still to be rotated upright gets the same answer.
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

    /** The middle of a [width] by [height] image with the screen's shape, as left, top, right, bottom. */
    fun centerCrop(width: Int, height: Int, screenWidth: Int, screenHeight: Int): List<Int> {
        // Image wider than the screen's shape: keep full height and cut the sides; else cut top and bottom.
        return if (width.toLong() * screenHeight > height.toLong() * screenWidth) {
            val w = (height.toLong() * screenWidth / screenHeight).toInt()
            listOf((width - w) / 2, 0, (width - w) / 2 + w, height)
        } else {
            val h = (width.toLong() * screenHeight / screenWidth).toInt()
            listOf(0, (height - h) / 2, width, (height - h) / 2 + h)
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
