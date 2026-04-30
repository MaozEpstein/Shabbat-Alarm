package com.example.shabbatalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.shabbatalarm.R
import com.example.shabbatalarm.alarm.AlarmRepository

/**
 * Full-screen editor for the pre-Shabbat reminder. Lets the user customize the
 * notification body text and the trigger offset (minutes before Jerusalem
 * candle lighting).
 *
 * @param initialCustomText The currently-saved custom text, or null if the user
 *  has never customized (i.e. is using the default string resource).
 * @param initialOffsetMinutes The currently-saved offset in minutes.
 * @param onSave Called with the new (customText, offsetMinutes). customText is
 *  null when the user explicitly reset to default OR cleared the field.
 */
@Composable
fun ReminderEditorScreen(
    initialCustomText: String?,
    initialOffsetMinutes: Int,
    onSave: (customText: String?, offsetMinutes: Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // Initial editor text: user's custom string if set, otherwise the rendered
    // default for the current offset.
    val initialEditorText = remember(initialCustomText, initialOffsetMinutes) {
        initialCustomText
            ?: context.getString(R.string.reminder_notification_text, initialOffsetMinutes)
    }

    var text by rememberSaveable { mutableStateOf(initialEditorText) }
    var offsetMinutes by rememberSaveable { mutableStateOf(initialOffsetMinutes) }
    // True iff the user has not (or has reverted) any custom text — the body
    // should track the offset slider automatically. Any manual edit flips this
    // to false so the user's wording is preserved across slider tweaks.
    var textIsDefault by rememberSaveable { mutableStateOf(initialCustomText == null) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
            ) {
                // ── Top bar ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.reminder_editor_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )
                    TextButton(onClick = {
                        val finalText = if (textIsDefault) null else text.trim().takeIf { it.isNotEmpty() }
                        onSave(finalText, offsetMinutes)
                    }) {
                        Text(stringResource(R.string.reminder_editor_save))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // ── Scrollable body ────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Offset slider
                    Text(
                        text = stringResource(
                            R.string.reminder_editor_offset_label, offsetMinutes
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.reminder_editor_offset_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val minMin = AlarmRepository.MIN_REMINDER_OFFSET_MINUTES
                    val maxMin = AlarmRepository.MAX_REMINDER_OFFSET_MINUTES
                    val step = AlarmRepository.REMINDER_OFFSET_STEP_MINUTES
                    val steps = (maxMin - minMin) / step - 1
                    Slider(
                        value = offsetMinutes.toFloat(),
                        onValueChange = {
                            // Round to nearest step (defensive against float drift).
                            val rounded = (Math.round(it / step.toFloat()) * step)
                                .coerceIn(minMin, maxMin)
                            if (rounded != offsetMinutes) {
                                offsetMinutes = rounded
                                // If text is still tracking the default, regenerate
                                // it for the new offset. Custom wording is left alone.
                                if (textIsDefault) {
                                    text = context.getString(
                                        R.string.reminder_notification_text, rounded
                                    )
                                }
                            }
                        },
                        valueRange = minMin.toFloat()..maxMin.toFloat(),
                        steps = steps,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.reminder_editor_offset_min_label, minMin),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.reminder_editor_offset_min_label, maxMin),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Text editor
                    Text(
                        text = stringResource(R.string.reminder_editor_text_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            // Any manual edit means the user wants their own wording.
                            // If they happen to type the exact default, treat that as
                            // "back to default" so the text follows the slider again.
                            val defaultForOffset = context.getString(
                                R.string.reminder_notification_text, offsetMinutes
                            )
                            textIsDefault = it.trim() == defaultForOffset || it.isBlank()
                        },
                        placeholder = {
                            Text(stringResource(R.string.reminder_editor_text_placeholder))
                        },
                        minLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            text = context.getString(
                                R.string.reminder_notification_text, offsetMinutes
                            )
                            textIsDefault = true
                        }) {
                            Text(stringResource(R.string.reminder_editor_reset_default))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Preview card
                    Text(
                        text = stringResource(R.string.reminder_editor_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.reminder_notification_title_combined),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val previewBody = text.ifBlank {
                                context.getString(
                                    R.string.reminder_notification_text, offsetMinutes
                                )
                            }
                            Text(
                                text = previewBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val finalText = if (textIsDefault) null else text.trim().takeIf { it.isNotEmpty() }
                            onSave(finalText, offsetMinutes)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reminder_editor_save))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
