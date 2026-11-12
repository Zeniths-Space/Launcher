package com.elara.launcher

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The one look of the launcher's bottom sheets: 32dp top corners, and a header of a scalloped
 * icon badge beside the title. The Elara form sheets (spaces) have their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ElaraSheet(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    /** A button at the header's end, if any. */
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opens at its content's height, not half the screen with the rest cut off below.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = if (action == null) 24.dp else 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // In settings, the page's own accent and badge shape.
            val accent = LocalAccent.current ?: Page.Main.accent
            AccentBadge(icon, accent, accent.shape, size = 48)
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = action != null).semantics { heading() },
            )
            action?.invoke()
        }
        content()
    }
}

/**
 * Rows of a sheet as one group, Material 3 Expressive style: tonal cards a hair apart, the group's
 * outer corners round and its inner ones nearly square. Scrolls when taller than the sheet.
 */
@Composable
internal fun SheetGroup(rows: List<SheetRow>) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 20.dp).navigationBarsPadding().clip(RoundedCornerShape(24.dp))) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.size(3.dp))
            SheetRowItem(row)
        }
    }
}

/**
 * One row of a [SheetGroup]: an icon in a round tonal badge, a label, and at the end a switch
 * ([checked], changed in place) or a check ([selected], one of a choice).
 */
internal class SheetRow(
    val icon: ImageVector,
    val label: String,
    val checked: Boolean? = null,
    val selected: Boolean? = null,
    val onClick: () -> Unit,
)

@Composable
private fun SheetRowItem(row: SheetRow) {
    val cs = MaterialTheme.colorScheme
    val on = row.selected == true
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) cs.primary.copy(alpha = 0.22f) else if (LocalPureBlack.current) RaisedOnBlack else cs.surfaceContainerHighest)
            .then(
                when {
                    row.checked != null -> Modifier.toggleable(row.checked, role = Role.Switch) { row.onClick() }
                    row.selected != null -> Modifier.selectable(row.selected, role = Role.RadioButton, onClick = row.onClick)
                    else -> Modifier.clickable(role = Role.Button, onClick = row.onClick)
                },
            )
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(cs.primary.copy(alpha = if (on) 1f else 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(row.icon, contentDescription = null, tint = if (on) cs.onPrimary else cs.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            row.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (on) cs.primary else cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        // The row is the control; the switch only shows its state.
        if (row.checked != null) Switch(row.checked, onCheckedChange = null)
        if (on) Icon(Icons.Done, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
    }
}

/** Raised parts on a black sheet, from the Elara form colours. */
private val RaisedOnBlack = androidx.compose.ui.graphics.Color(0xFF2A2A2E)
