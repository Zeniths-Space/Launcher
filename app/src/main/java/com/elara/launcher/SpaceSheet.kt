package com.elara.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** The Elara form's palette (approved 2026-11-08): AMOLED in dark mode, white in light. */
private class FormColors(val bg: Color, val field: Color, val raised: Color, val text: Color, val muted: Color, val label: Color, val error: Color)

private val Brand = Color(0xFF7C4DFF)
private val DarkForm = FormColors(Color.Black, Color(0xFF141414), Color(0xFF1F1F1F), Color.White, Color(0xFF9A9A9F), Color(0xFFB69CFF), Color(0xFFFF8A8A))
private val LightForm = FormColors(Color.White, Color(0xFFF2F0F7), Color(0xFFE6E2EF), Color(0xFF18181B), Color(0xFF6B6B76), Brand, Color(0xFFD93A3A))
private val Cookie = polarShape(lobes = 8, depth = 0.1f)

internal fun SpaceIcon.vector(): ImageVector = when (this) {
    SpaceIcon.Home -> Icons.Home
    SpaceIcon.Work -> Icons.Work
    SpaceIcon.Night -> Icons.DarkMode
    SpaceIcon.School -> Icons.School
    SpaceIcon.Fitness -> Icons.Fitness
    SpaceIcon.Travel -> Icons.Travel
    SpaceIcon.Focus -> Icons.Focus
    SpaceIcon.Weekend -> Icons.Sun
}

internal fun SpaceIcon.label() = when (this) {
    SpaceIcon.Home -> R.string.space_icon_home
    SpaceIcon.Work -> R.string.space_icon_work
    SpaceIcon.Night -> R.string.space_icon_night
    SpaceIcon.School -> R.string.space_icon_school
    SpaceIcon.Fitness -> R.string.space_icon_fitness
    SpaceIcon.Travel -> R.string.space_icon_travel
    SpaceIcon.Focus -> R.string.space_icon_focus
    SpaceIcon.Weekend -> R.string.space_icon_weekend
}

/**
 * The form to add a space ([editing] null) or change one: name, icon, what it starts with (new
 * only), and a schedule (days and times; none for the default space, on whenever no other is).
 * An Elara form sheet: closes from its button or a tap outside, asking first if anything was typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpaceSheet(
    editing: Space?,
    isDefault: Boolean,
    onSave: (Space, copy: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val f = if (LocalDark.current) DarkForm else LightForm
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var icon by remember { mutableStateOf(editing?.icon ?: SpaceIcon.Work) }
    var copy by remember { mutableStateOf(true) }
    var days by remember { mutableIntStateOf(editing?.days ?: 0b0111110) }
    var from by remember { mutableIntStateOf(editing?.from ?: 9 * 60) }
    var to by remember { mutableIntStateOf(editing?.to ?: 17 * 60) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }
    val changed = name != editing?.name.orEmpty() || (editing != null && (icon != editing.icon || days != editing.days || from != editing.from || to != editing.to))
    val tryClose = { if (changed && name.isNotBlank()) confirmDiscard = true else dismiss() }
    val valid = name.isNotBlank() && (isDefault || days == 0 || from != to)

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = f.raised,
            title = { Text(stringResource(R.string.space_discard)) },
            text = { Text(stringResource(R.string.space_discard_body)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; dismiss() }) { Text(stringResource(R.string.space_discard_yes), color = f.label) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.space_keep_editing), color = f.label) } },
        )
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = f.raised,
            title = { Text(stringResource(R.string.space_delete_title, editing?.name.orEmpty())) },
            text = { Text(stringResource(R.string.space_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(); dismiss() }) { Text(stringResource(R.string.space_delete), color = f.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.rename_cancel), color = f.label) } },
        )
    }

    ModalBottomSheet(
        onDismissRequest = tryClose,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = f.bg,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        // At most 90% of the screen, so there is always something above to tap to close.
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().navigationBarsPadding()) {
            Header(
                f,
                icon.vector(),
                stringResource(if (editing == null) R.string.space_new else R.string.space_edit),
                stringResource(R.string.space_form_subtitle),
                onClose = tryClose,
            )
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                NameBox(name, { name = it.take(24) }, f)

                SectionTitle(stringResource(R.string.space_icon), f)
                IconDropdown(icon, f) { icon = it }

                if (editing == null) {
                    SectionTitle(stringResource(R.string.space_starts_with), f)
                    Segmented(stringResource(R.string.space_copy), stringResource(R.string.space_empty), secondSelected = !copy, f) { copy = !it }
                    Text(
                        stringResource(if (copy) R.string.space_copy_note else R.string.space_empty_note),
                        fontSize = 13.sp,
                        color = f.muted,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                    )
                }

                SectionTitle(stringResource(R.string.space_schedule), f)
                if (isDefault) {
                    Text(
                        stringResource(R.string.space_default_schedule),
                        fontSize = 15.sp,
                        color = f.muted,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(f.field).padding(18.dp),
                    )
                } else {
                    ScheduleBox(days, from, to, f, onDays = { days = it }, onFrom = { from = it }, onTo = { to = it })
                    if (days != 0 && from == to) {
                        Text(stringResource(R.string.space_same_times), fontSize = 13.sp, color = f.error, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                    }
                }

                if (onDelete != null) {
                    Text(
                        stringResource(R.string.space_delete),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = f.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(f.field)
                            .clickable(role = Role.Button) { confirmDelete = true }
                            .padding(vertical = 16.dp),
                    )
                }
                Spacer(Modifier.heightIn(min = 16.dp))
            }
            Text(
                stringResource(R.string.space_save),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (valid) Color.White else f.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (valid) Brand else f.field)
                    .clickable(enabled = valid, role = Role.Button) {
                        val base = editing ?: Space(0, "")
                        onSave(base.copy(name = name.trim(), icon = icon, days = if (isDefault) 0 else days, from = from, to = to), copy)
                        dismiss()
                    }
                    .padding(vertical = 18.dp),
            )
        }
    }
}

@Composable
private fun Header(f: FormColors, icon: ImageVector, title: String, subtitle: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(Cookie).background(Brand.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = f.label, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = f.text, modifier = Modifier.semantics { heading() })
            Text(subtitle, fontSize = 14.sp, color = f.muted, textAlign = TextAlign.Center)
        }
        val close = stringResource(R.string.space_close)
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(f.raised).clickable(onClick = onClose).semantics { contentDescription = close; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Close, contentDescription = null, tint = f.text) }
    }
}

@Composable
private fun SectionTitle(text: String, f: FormColors) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = f.label, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp).semantics { heading() })
}

/** The name, large, in a box with a faint purple edge (the form's main field). */
@Composable
private fun NameBox(value: String, onChange: (String) -> Unit, f: FormColors) {
    val label = stringResource(R.string.space_name)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brand.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, Brand.copy(alpha = 0.35f)), RoundedCornerShape(26.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = f.label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, color = f.text),
            cursorBrush = SolidColor(f.label),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(stringResource(R.string.space_name_hint), fontSize = 28.sp, fontWeight = FontWeight.Medium, color = f.muted.copy(alpha = 0.6f))
                    inner()
                }
            },
        )
    }
}

/** The icon as a dropdown field: a cookie with it, the label over its name, a chevron; the menu is as wide as the field. */
@Composable
private fun IconDropdown(selected: SpaceIcon, f: FormColors, onPick: (SpaceIcon) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(f.field)
                .clickable { open = true }
                .semantics { role = Role.DropdownList }
                .padding(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(Cookie).background(Brand.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(selected.vector(), contentDescription = null, tint = f.label, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(stringResource(R.string.space_icon_shown), fontSize = 13.sp, color = f.muted)
                Text(stringResource(selected.label()), fontSize = 17.sp, color = f.text, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.ExpandMore, contentDescription = null, tint = f.text)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = f.raised,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.width(with(density) { width.toDp() }).padding(horizontal = 6.dp),
        ) {
            for (option in SpaceIcon.entries) {
                val on = option == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) Brand.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { open = false; onPick(option) }
                        .semantics { this.selected = on }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(option.vector(), contentDescription = null, tint = if (on) f.label else f.text, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(option.label()), fontSize = 16.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) f.label else f.text, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Done, contentDescription = null, tint = f.label, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Two options as one pill, the chosen one filled in purple. */
@Composable
private fun Segmented(first: String, second: String, secondSelected: Boolean, f: FormColors, onPick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(f.field).padding(6.dp)) {
        for ((i, option) in listOf(first, second).withIndex()) {
            val on = (i == 1) == secondSelected
            Text(
                option,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (on) Color.White else f.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (on) Brand else Color.Transparent)
                    .clickable { onPick(i == 1) }
                    .semantics { selected = on; role = Role.Tab }
                    .padding(vertical = 14.dp),
            )
        }
    }
}

/** Days as seven round toggles (Sunday first, as in Nepal), then the times in raised pills, each opening a picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleBox(days: Int, from: Int, to: Int, f: FormColors, onDays: (Int) -> Unit, onFrom: (Int) -> Unit, onTo: (Int) -> Unit) {
    val names = listOf(R.string.day_sun, R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat).map { stringResource(it) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: from, false: to
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(f.field).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            names.forEachIndexed { i, day ->
                val on = days and (1 shl i) != 0
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (on) Brand else f.raised)
                        .clickable { onDays(days xor (1 shl i)) }
                        .semantics { contentDescription = day; selected = on; role = Role.Checkbox },
                    contentAlignment = Alignment.Center,
                ) { Text(day.take(1), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else f.muted) }
            }
        }
        if (days == 0) {
            Text(stringResource(R.string.space_no_days), fontSize = 14.sp, color = f.muted, modifier = Modifier.padding(start = 4.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.space_from), fontSize = 15.sp, color = f.muted, modifier = Modifier.weight(1f).padding(start = 4.dp))
                TimePill(from, f) { picking = true }
                Text(stringResource(R.string.space_to), fontSize = 15.sp, color = f.muted, modifier = Modifier.padding(horizontal = 10.dp))
                TimePill(to, f) { picking = false }
            }
            if (to < from) Text(stringResource(R.string.space_next_day), fontSize = 13.sp, color = f.muted, modifier = Modifier.padding(start = 4.dp))
        }
    }
    picking?.let { isFrom ->
        val at = if (isFrom) from else to
        val state = rememberTimePickerState(at / 60, at % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            containerColor = f.raised,
            confirmButton = {
                TextButton(onClick = {
                    val minutes = state.hour * 60 + state.minute
                    if (isFrom) onFrom(minutes) else onTo(minutes)
                    picking = null
                }) { Text(stringResource(R.string.space_ok), color = f.label) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.rename_cancel), color = f.label) } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun TimePill(minutes: Int, f: FormColors, onClick: () -> Unit) {
    Text(
        Spaces.time(minutes),
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        color = f.label,
        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(f.raised).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
