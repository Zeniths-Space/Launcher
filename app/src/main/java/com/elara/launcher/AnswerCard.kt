package com.elara.launcher

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * The answer to a sum typed into the drawer search: a glass card over the wallpaper with the
 * result, Copy, and Open in Calculator when a calculator app is installed; for "a% of b", the
 * amount less and plus that percent under it.
 */
@Composable
internal fun MathAnswerCard(answer: MathAnswer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Any calculator app (Elara's or another) that says it is one. Opened by name: such filters
    // often lack CATEGORY_DEFAULT, which an implicit start needs.
    val calculator = remember {
        val find = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
        context.packageManager.queryIntentActivities(find, 0).firstOrNull()?.activityInfo?.let {
            Intent(find).setClassName(it.packageName, it.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
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
                    Icon(Icons.Calculate, contentDescription = null, tint = AnswerAccentText, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.answer_label), color = AnswerAccentText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.answer_go_copies), color = AnswerMuted, fontSize = 12.sp)
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
                if (calculator != null) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(calculator)
                            } catch (_: ActivityNotFoundException) {
                                // Uninstalled since the card appeared: nothing to open.
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OnWallpaper.copy(alpha = 0.12f), contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1.2f).height(44.dp),
                    ) { Text(stringResource(R.string.answer_open_calculator), fontSize = 15.sp, maxLines = 1) }
                }
            }
        }
        if (answer.also.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.answer_also),
                    color = AnswerAccentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
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
