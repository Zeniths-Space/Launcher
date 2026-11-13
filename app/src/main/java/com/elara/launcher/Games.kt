package com.elara.launcher

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The Games folder: one entry in the drawer, which can sit on the home screen or dock like an app and
 * opens [FolderSheet]. It is a stand-in app ([entry]), so every place that lists or places apps takes it.
 * Its members are the apps Android calls games, less those the user took out, plus those they added.
 */
object Games {
    const val Package = "app.elara.games"
    const val Class = "games"
    val entry = AppEntry("Games", Package, Class, 0)
    val Key: String = entry.key

    @Suppress("DEPRECATION")
    fun isGame(info: ApplicationInfo) =
        info.category == ApplicationInfo.CATEGORY_GAME || info.flags and ApplicationInfo.FLAG_IS_GAME != 0

    fun members(detected: Set<String>, added: Set<String>, removed: Set<String>): Set<String> = (detected - removed) + added
}

/**
 * Folders the user makes in the drawer, stored like home folders ([HomeFolders]). Each shows in the drawer as a stand-in
 * app ([entry]) after Games and before the apps, and opens a [FolderSheet]. Their apps leave the drawer grid.
 */
object DrawerFolders {
    const val Package = "app.elara.drawerfolder"

    fun entry(folder: HomeFolder, defaultName: String) = AppEntry(folder.name.ifEmpty { defaultName }, Package, folder.id.toString(), 0)

    fun idOf(app: AppEntry): Int? = if (app.packageName == Package) app.className.toIntOrNull() else null

    /** Games, then the folders, then [apps] (which may hold the Games entry among them). */
    fun pinFirst(apps: List<AppEntry>, folders: List<AppEntry>): List<AppEntry> =
        apps.filter { it.key == Games.Key } + folders + apps.filter { it.key != Games.Key }

    /** How many of the entries at the start of a [pinFirst] list are folders (Games included). */
    fun pinnedCount(apps: List<AppEntry>) = apps.takeWhile { it.key == Games.Key || it.packageName == Package }.size
}
