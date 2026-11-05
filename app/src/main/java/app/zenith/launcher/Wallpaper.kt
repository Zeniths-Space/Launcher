package app.zenith.launcher

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Change the wallpaper: pick a photo with the system photo picker (no storage permission), then
 * hand it to the system's crop-and-set screen. Works on any device; the live-wallpaper-only
 * SET_WALLPAPER pickers some OEMs ship (Samsung) are not used.
 */
@Composable
fun rememberWallpaperPicker(): () -> Unit {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) setWallpaperFrom(context, uri)
    }
    return { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

private fun setWallpaperFrom(context: Context, uri: Uri) {
    val crop = try {
        WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri)
    } catch (_: IllegalArgumentException) {
        null // No system cropper for this image or device.
    }
    // Fallback: the standard "Set as" chooser that galleries use.
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
