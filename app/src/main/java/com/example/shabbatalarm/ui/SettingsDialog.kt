package com.example.shabbatalarm.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.shabbatalarm.R
import com.example.shabbatalarm.alarm.AlarmRepository
import com.example.shabbatalarm.alarm.AlarmTone
import com.example.shabbatalarm.alarm.AlarmTones

private enum class SettingsSubView { MAIN, SOUND, REMINDER }

@Composable
fun SettingsDialog(
    currentDurationSeconds: Int,
    repeatWeekly: Boolean,
    vibrationEnabled: Boolean,
    currentToneUri: String?,
    reminderEnabled: Boolean,
    onDurationChange: (Int) -> Unit,
    onRepeatChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onToneChange: (String?) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onShareApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tones by remember { mutableStateOf(AlarmTones.loadAvailable(context)) }
    val preview = remember { TonePreview(context, scope) }

    var subView by rememberSaveable { mutableStateOf(SettingsSubView.MAIN) }

    val systemDefaultLabel = stringResource(R.string.settings_alarm_sound_default)
    val defaultCustomTitle = stringResource(R.string.custom_tone_default_title)
    val maxToneToast = stringResource(
        R.string.max_custom_tones_reached, AlarmRepository.MAX_CUSTOM_TONES
    )
    val effectiveSelectedUri = currentToneUri ?: tones.firstOrNull()?.uri?.toString()
    val currentToneTitle = tones.firstOrNull { it.uri.toString() == effectiveSelectedUri }
        ?.title ?: systemDefaultLabel

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Keep access across app restarts (best-effort — some providers reject it).
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val displayName = queryDisplayName(context, uri) ?: defaultCustomTitle
            val added = AlarmRepository(context).addCustomTone(uri.toString(), displayName)
            if (!added) {
                Toast.makeText(context, maxToneToast, Toast.LENGTH_LONG).show()
            } else {
                tones = AlarmTones.loadAvailable(context)
                onToneChange(uri.toString())
                preview.play(uri)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { preview.release() }
    }

    Dialog(onDismissRequest = {
        preview.release()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                when (subView) {
                    SettingsSubView.SOUND -> AlarmSoundPickerView(
                        tones = tones,
                        selectedUri = effectiveSelectedUri,
                        onSelect = { tone ->
                            preview.play(tone.uri)
                            onToneChange(tone.uri.toString())
                        },
                        onAddCustomClick = {
                            preview.release()
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        },
                        onRemoveCustom = { tone ->
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    tone.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                            AlarmRepository(context).removeCustomTone(tone.uri.toString())
                            // If user removed the currently-selected tone, reset.
                            if (tone.uri.toString() == effectiveSelectedUri) {
                                onToneChange(null)
                            }
                            tones = AlarmTones.loadAvailable(context)
                        },
                        onBack = {
                            preview.release()
                            subView = SettingsSubView.MAIN
                        },
                        onDismiss = {
                            preview.release()
                            onDismiss()
                        }
                    )
                    SettingsSubView.REMINDER -> ReminderPickerView(
                        enabled = reminderEnabled,
                        onEnabledChange = onReminderEnabledChange,
                        onBack = { subView = SettingsSubView.MAIN },
                        onDismiss = onDismiss
                    )
                    SettingsSubView.MAIN -> MainSettingsView(
                        currentDurationSeconds = currentDurationSeconds,
                        repeatWeekly = repeatWeekly,
                        vibrationEnabled = vibrationEnabled,
                        currentToneTitle = currentToneTitle,
                        reminderEnabled = reminderEnabled,
                        onDurationChange = onDurationChange,
                        onRepeatChange = onRepeatChange,
                        onVibrationChange = onVibrationChange,
                        onOpenSoundPicker = { subView = SettingsSubView.SOUND },
                        onOpenReminderPicker = { subView = SettingsSubView.REMINDER },
                        onShareApp = onShareApp,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun MainSettingsView(
    currentDurationSeconds: Int,
    repeatWeekly: Boolean,
    vibrationEnabled: Boolean,
    currentToneTitle: String,
    reminderEnabled: Boolean,
    onDurationChange: (Int) -> Unit,
    onRepeatChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onOpenSoundPicker: () -> Unit,
    onOpenReminderPicker: () -> Unit,
    onShareApp: () -> Unit,
    onDismiss: () -> Unit
) {
    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(20.dp))

    // ── Alarm sound (opens sub-view) ───────────────────────────────────────
    NavigationRow(
        title = stringResource(R.string.settings_alarm_sound),
        subtitle = currentToneTitle,
        onClick = onOpenSoundPicker
    )

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Spacer(modifier = Modifier.height(16.dp))

    // ── Alarm duration ─────────────────────────────────────────────────────
    Text(
        text = stringResource(R.string.settings_duration, currentDurationSeconds),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Slider(
        value = currentDurationSeconds.toFloat(),
        onValueChange = { onDurationChange(it.toInt()) },
        valueRange = AlarmRepository.MIN_DURATION_SECONDS.toFloat()
                ..AlarmRepository.MAX_DURATION_SECONDS.toFloat(),
        steps = ((AlarmRepository.MAX_DURATION_SECONDS
                - AlarmRepository.MIN_DURATION_SECONDS)
                / AlarmRepository.DURATION_STEP_SECONDS) - 1,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    // ── Repeat weekly ──────────────────────────────────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_repeat_weekly),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_repeat_weekly_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = repeatWeekly, onCheckedChange = onRepeatChange)
    }

    Spacer(modifier = Modifier.height(12.dp))

    // ── Vibration ─────────────────────────────────────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_vibration),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_vibration_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = vibrationEnabled, onCheckedChange = onVibrationChange)
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Spacer(modifier = Modifier.height(12.dp))

    // ── Pre-Shabbat reminder (opens sub-view) ──────────────────────────────
    val reminderSubtitle = if (reminderEnabled) {
        stringResource(R.string.settings_reminder_enabled_label)
    } else {
        stringResource(R.string.settings_reminder_disabled)
    }
    NavigationRow(
        title = stringResource(R.string.settings_reminder),
        subtitle = reminderSubtitle,
        onClick = onOpenReminderPicker
    )

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Spacer(modifier = Modifier.height(8.dp))

    // ── Share APK ─────────────────────────────────────────────────────────
    NavigationRow(
        title = stringResource(R.string.settings_share_app),
        subtitle = stringResource(R.string.settings_share_app_desc),
        onClick = onShareApp
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.close))
        }
    }
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlarmSoundPickerView(
    tones: List<AlarmTone>,
    selectedUri: String?,
    onSelect: (AlarmTone) -> Unit,
    onAddCustomClick: () -> Unit,
    onRemoveCustom: (AlarmTone) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.settings_alarm_sound),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_alarm_sound_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))

    Column(
        modifier = Modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Add-from-phone action row (always at the top).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddCustomClick)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_add_custom_tone),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_add_custom_tone_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        tones.forEach { tone ->
            ToneRow(
                tone = tone,
                selected = tone.uri.toString() == selectedUri,
                onSelect = { onSelect(tone) },
                onRemove = if (tone.isCustom) {
                    { onRemoveCustom(tone) }
                } else null
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.close))
        }
    }
}

/** Queries the system for the human-readable name of a content URI. */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull()
}

@Composable
private fun ReminderPickerView(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.settings_reminder),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_reminder_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(20.dp))

    // Enable switch only — city is fixed to Jerusalem.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_reminder_enable),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.close))
        }
    }
}

@Composable
private fun ToneRow(
    tone: AlarmTone,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tone.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_custom_tone),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
