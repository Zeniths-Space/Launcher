package com.elara.launcher

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AnswerAccent = Color(0xFF7C4DFF)
private val AnswerAccentText = Color(0xFFB69CFF)
private val AnswerMuted = Color(0xFFB4B4BA)

/** Puts a search answer on the clipboard. Android 13+ shows its own confirmation. */
internal fun copyAnswer(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.answer_label), text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.answer_copied, Toast.LENGTH_SHORT).show()
    }
}

private fun AnswerKind.icon() = when (this) {
    AnswerKind.Maths -> Icons.Calculate
    AnswerKind.Date -> Icons.Event
    AnswerKind.Currency -> Icons.Money
    else -> Icons.Ruler
}

private fun AnswerKind.label() = when (this) {
    AnswerKind.Maths -> R.string.answer_label
    AnswerKind.Length -> R.string.answer_length
    AnswerKind.Mass -> R.string.answer_mass
    AnswerKind.Temperature -> R.string.answer_temperature
    AnswerKind.Volume -> R.string.answer_volume
    AnswerKind.Speed -> R.string.answer_speed
    AnswerKind.Area -> R.string.answer_area
    AnswerKind.Date -> R.string.answer_date
    AnswerKind.Currency -> R.string.answer_currency
}

/**
 * The answer to a search (a sum, a unit, a date or an amount of money): a glass card over the
 * wallpaper with the result and Copy, then a button for the kind: Open in Calculator for a sum,
 * Swap for a conversion ([onQuery] gets the turned-round query). A currency without a rate asks for
 * one ([onSetRate]); one the user set can be changed. Extra results go in a group under it.
 */
@Composable
internal fun AnswerCard(answer: Answer, onQuery: (String) -> Unit, onSetRate: (CurrencyRate) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Any calculator app (Elara's or another) that says it is one. Opened by name: such filters
    // often lack CATEGORY_DEFAULT, which an implicit start needs.
    val calculator = remember {
        val find = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
        context.packageManager.queryIntentActivities(find, 0).firstOrNull()?.activityInfo?.let {
            Intent(find).setClassName(it.packageName, it.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    val rate = answer.rate
    var editing by remember(rate?.from, rate?.to) { mutableStateOf(false) }
    val askRate = rate != null && (rate.rate == null || editing)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(OnWallpaper.copy(alpha = 0.10f))
                .border(BorderStroke(1.dp, OnWallpaper.copy(alpha = 0.10f)), RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(AnswerAccent.copy(alpha = 0.30f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(answer.kind.icon(), contentDescription = null, tint = AnswerAccentText, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(answer.kind.label()), color = AnswerAccentText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                if (!askRate) Text(stringResource(R.string.answer_go_copies), color = AnswerMuted, fontSize = 12.sp)
            }
            if (askRate) {
                RateForm(rate!!, answer.expression, onSave = { value ->
                    editing = false
                    onSetRate(rate.copy(rate = value))
                })
                return@Column
            }
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                // One line, shrinking for long results (1/3 has twelve 3s).
                BasicText(
                    answer.value,
                    style = TextStyle(color = OnWallpaper, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 48.sp),
                )
                Text(answer.expression, color = AnswerMuted, fontSize = 15.sp, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { copyAnswer(context, answer.plain) },
                    colors = ButtonDefaults.buttonColors(containerColor = AnswerAccent, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(0.8f).height(44.dp),
                ) {
                    Icon(Icons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.answer_copy), fontSize = 15.sp)
                }
                val second = OnWallpaper.copy(alpha = 0.12f)
                if (answer.kind == AnswerKind.Maths && calculator != null) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(calculator)
                            } catch (_: ActivityNotFoundException) {
                                // Uninstalled since the card appeared: nothing to open.
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = second, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1.2f).height(44.dp),
                    ) { Text(stringResource(R.string.answer_open_calculator), fontSize = 15.sp, maxLines = 1) }
                }
                if (rate != null && !rate.builtIn) {
                    Button(
                        onClick = { editing = true },
                        colors = ButtonDefaults.buttonColors(containerColor = second, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(44.dp),
                    ) { Text(stringResource(R.string.answer_change_rate), fontSize = 15.sp, maxLines = 1) }
                }
                answer.swap?.let { swap ->
                    IconButton(
                        onClick = { onQuery(swap) },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = second, contentColor = Color.White),
                        modifier = Modifier.size(width = 56.dp, height = 44.dp),
                    ) { Icon(Icons.Swap, contentDescription = stringResource(R.string.answer_swap), modifier = Modifier.size(20.dp)) }
                }
            }
        }
        if (answer.also.isNotEmpty() && !askRate) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ResultLabel(stringResource(R.string.answer_also))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(OnWallpaper.copy(alpha = 0.08f))) {
                    answer.also.forEachIndexed { i, (label, value) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = OnWallpaper.copy(alpha = 0.08f))
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, color = Color(0xFFD4D4D8), fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(value, color = OnWallpaper, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** Asks for an exchange rate the launcher can't know offline: "1 USD = [ ] NPR", then Save. */
@Composable
private fun RateForm(rate: CurrencyRate, amount: String, onSave: (Double) -> Unit) {
    var text by remember(rate.from, rate.to) { mutableStateOf(rate.rate?.let { CurrencySearch.money(it, java.util.Locale.ROOT, 4).replace(",", "") }.orEmpty()) }
    val value = text.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    Text(
        stringResource(if (rate.rate == null) R.string.answer_rate_needed else R.string.answer_rate_change, amount, rate.to),
        color = AnswerMuted,
        fontSize = 14.sp,
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(OnWallpaper.copy(alpha = 0.10f)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("1 ${rate.from} =", color = OnWallpaper, fontSize = 17.sp)
        Spacer(Modifier.width(10.dp))
        val label = stringResource(R.string.answer_rate_field, rate.from, rate.to)
        BasicTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) },
            singleLine = true,
            textStyle = TextStyle(color = OnWallpaper, fontSize = 17.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(OnWallpaper),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { value?.let(onSave) }),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        Text(rate.to, color = AnswerMuted, fontSize = 17.sp)
    }
    Button(
        onClick = { value?.let(onSave) },
        enabled = value != null,
        colors = ButtonDefaults.buttonColors(
            containerColor = AnswerAccent,
            contentColor = Color.White,
            disabledContainerColor = OnWallpaper.copy(alpha = 0.10f),
            disabledContentColor = AnswerMuted,
        ),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text(stringResource(R.string.answer_rate_save), fontSize = 15.sp) }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (filled) AnswerAccent else OnWallpaper.copy(alpha = 0.14f),
            contentColor = Color.White,
        ),
        modifier = Modifier.size(44.dp),
    ) { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun Initials(contact: ContactHit, size: Dp, strong: Boolean) {
    Box(
        Modifier.size(size).background(if (strong) AnswerAccent else AnswerAccent.copy(alpha = 0.30f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(contact.initials, color = if (strong) Color.White else AnswerAccentText, fontSize = if (strong) 19.sp else 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The best contact on the glass card: tap for the contact, Call (Go does the same) or Message. */
@Composable
internal fun ContactCard(contact: ContactHit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(OnWallpaper.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, OnWallpaper.copy(alpha = 0.10f)), RoundedCornerShape(28.dp))
            .clickable { ContactSearch.open(context, contact) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Initials(contact, 52.dp, strong = true)
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = OnWallpaper, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.contact_go_calls, contact.number), color = AnswerMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundAction(Icons.Call, stringResource(R.string.contact_call, contact.name), filled = true) { ContactSearch.dial(context, contact) }
        RoundAction(Icons.Message, stringResource(R.string.contact_message, contact.name), filled = false) { ContactSearch.message(context, contact) }
    }
}

/** A section label over search results, in the brand purple. */
@Composable
internal fun ResultLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = AnswerAccentText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = modifier.padding(horizontal = 8.dp))
}

/** The other contacts found, in one rounded group: tap a row for the contact, the phone to call. */
@Composable
internal fun ContactList(contacts: List<ContactHit>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(OnWallpaper.copy(alpha = 0.08f))) {
        contacts.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = OnWallpaper.copy(alpha = 0.08f))
            Row(
                Modifier.fillMaxWidth().clickable { ContactSearch.open(context, c) }.height(60.dp).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Initials(c, 36.dp, strong = false)
                Text(c.name, color = OnWallpaper, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { ContactSearch.dial(context, c) }) {
                    Icon(Icons.Call, contentDescription = stringResource(R.string.contact_call, c.name), tint = AnswerAccentText, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Offers contacts in search, for the first time: Allow asks Android; the cross turns it off (Settings > App drawer brings it back). */
@Composable
internal fun ContactsOffer(onAllow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(OnWallpaper.copy(alpha = 0.08f)).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Person, contentDescription = null, tint = AnswerAccentText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.contacts_offer), color = OnWallpaper, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onAllow) { Text(stringResource(R.string.contacts_allow), color = AnswerAccentText, fontWeight = FontWeight.Medium) }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Close, contentDescription = stringResource(R.string.contacts_offer_dismiss), tint = AnswerMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Apps' shortcuts found by the search, in one rounded group: the shortcut's icon, its name and its app's. */
@Composable
internal fun ShortcutList(shortcuts: List<AppShortcut>, repository: AppRepository, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(OnWallpaper.copy(alpha = 0.08f))) {
        shortcuts.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = OnWallpaper.copy(alpha = 0.08f))
            val icon by produceState<ImageBitmap?>(null, s.key, iconPx) { value = repository.shortcutIcon(s, iconPx) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onStart()
                        repository.startShortcut(s, null)
                    }
                    .height(64.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).background(AnswerAccent.copy(alpha = 0.30f), CircleShape), contentAlignment = Alignment.Center) {
                    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                        ?: Icon(Icons.Add, contentDescription = null, tint = AnswerAccentText, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(s.label, color = OnWallpaper, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.appLabel, color = AnswerMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
