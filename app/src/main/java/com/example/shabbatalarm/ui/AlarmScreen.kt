package com.example.shabbatalarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.shabbatalarm.R
import com.example.shabbatalarm.alarm.AlarmRepository
import com.example.shabbatalarm.alarm.AlarmScheduler
import com.example.shabbatalarm.alarm.ScheduledAlarm
import com.example.shabbatalarm.alarm.ShabbatReminderScheduler
import com.example.shabbatalarm.widget.ShabbatAlarmWidget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    val scheduler = remember { AlarmScheduler(context) }
    val reminderScheduler = remember { ShabbatReminderScheduler(context) }
    val repository = remember { AlarmRepository(context) }
    val now = remember { Calendar.getInstance() }

    val timePickerState = rememberTimePickerState(
        initialHour = now.get(Calendar.HOUR_OF_DAY),
        initialMinute = now.get(Calendar.MINUTE),
        is24Hour = true
    )

    var alarms by remember { mutableStateOf(repository.getActiveAlarms()) }
    var showShabbatTimes by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var durationSeconds by rememberSaveable { mutableStateOf(repository.getDurationSeconds()) }
    var isBatteryOptimized by rememberSaveable { mutableStateOf(false) }
    var repeatWeekly by rememberSaveable { mutableStateOf(repository.getRepeatWeekly()) }
    var vibrationEnabled by rememberSaveable { mutableStateOf(repository.getVibrationEnabled()) }
    var alarmToneUri by rememberSaveable { mutableStateOf(repository.getAlarmToneUri()) }
    var reminderEnabled by rememberSaveable { mutableStateOf(repository.getPreShabbatReminderEnabled()) }
    var defaultCityIndex by rememberSaveable { mutableStateOf(repository.getDefaultCityIndex()) }

    val tomorrowSuffix = stringResource(R.string.tomorrow_suffix)
    val allowExactAlarmToast = stringResource(R.string.allow_exact_alarm_toast)
    val maxAlarmsToast = stringResource(R.string.max_alarms_reached, AlarmRepository.MAX_ALARMS)

    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    val batterySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isBatteryOptimized = !isIgnoringBatteryOptimizations(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Refresh alarms list + battery state whenever the screen resumes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alarms = repository.getActiveAlarms()
                isBatteryOptimized = !isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLit = alarms.isNotEmpty()
    val topTint by animateColorAsState(
        targetValue = if (isLit)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            Color.Transparent,
        animationSpec = tween(durationMillis = 800),
        label = "backgroundTint"
    )
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(topTint, Color.Transparent),
        startY = 0f,
        endY = 600f
    )

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .verticalScroll(scrollState, enabled = alarms.size > 1)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showShabbatTimes = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.cd_shabbat_times),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isBatteryOptimized) {
            BatteryOptimizationCard(
                onFixClick = {
                    batterySettingsLauncher.launch(
                        buildIgnoreBatteryOptIntent(context.packageName)
                    )
                }
            )
        }

        AnimatedCandle(
            isLit = isLit,
            modifier = Modifier.size(width = 115.dp, height = 100.dp)
        )

        Text(
            text = stringResource(R.string.screen_title),
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = stringResource(R.string.screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val timePickerColors = TimePickerDefaults.colors(
            timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        TimePicker(state = timePickerState, colors = timePickerColors)

        Button(
            onClick = {
                if (alarms.size >= AlarmRepository.MAX_ALARMS) {
                    Toast.makeText(context, maxAlarmsToast, Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (!scheduler.canScheduleExactAlarms()) {
                    Toast.makeText(context, allowExactAlarmToast, Toast.LENGTH_LONG).show()
                    exactAlarmSettingsLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    )
                    return@Button
                }
                val triggerAt = scheduler.computeNextTrigger(
                    timePickerState.hour,
                    timePickerState.minute
                )
                val newAlarm = repository.addAlarm(triggerAt, repeatWeekly)
                scheduler.schedule(newAlarm)
                alarms = repository.getActiveAlarms()
                ShabbatAlarmWidget.updateAll(context)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.set_alarm),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (alarms.isNotEmpty()) {
            Text(
                text = stringResource(R.string.alarms_list_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            alarms
                .sortedBy { it.triggerMillis }
                .forEach { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        label = formatTriggerTime(alarm.triggerMillis, tomorrowSuffix),
                        onCancel = {
                            scheduler.cancel(alarm.id)
                            repository.removeAlarm(alarm.id)
                            alarms = repository.getActiveAlarms()
                            ShabbatAlarmWidget.updateAll(context)
                        }
                    )
                }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showShabbatTimes) {
        ShabbatTimesDialog(
            defaultCityIndex = defaultCityIndex,
            onCityChange = {
                defaultCityIndex = it
                repository.setDefaultCityIndex(it)
            },
            onDismiss = { showShabbatTimes = false }
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentDurationSeconds = durationSeconds,
            repeatWeekly = repeatWeekly,
            vibrationEnabled = vibrationEnabled,
            currentToneUri = alarmToneUri,
            reminderEnabled = reminderEnabled,
            onDurationChange = {
                durationSeconds = it
                repository.setDurationSeconds(it)
            },
            onRepeatChange = {
                repeatWeekly = it
                repository.setRepeatWeekly(it)
            },
            onVibrationChange = {
                vibrationEnabled = it
                repository.setVibrationEnabled(it)
            },
            onToneChange = {
                alarmToneUri = it
                repository.setAlarmToneUri(it)
            },
            onReminderEnabledChange = {
                reminderEnabled = it
                repository.setPreShabbatReminderEnabled(it)
                if (it) reminderScheduler.scheduleNext() else reminderScheduler.cancel()
            },
            onShareApp = { ApkSharer.share(context, shareScope) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: ScheduledAlarm,
    label: String,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                if (alarm.repeatWeekly) {
                    Text(
                        text = stringResource(R.string.weekly_badge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_cancel_alarm),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun buildIgnoreBatteryOptIntent(packageName: String): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }

private fun formatTriggerTime(triggerAtMillis: Long, tomorrowSuffix: String): String {
    val target = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
    val today = Calendar.getInstance()
    val dayDiff = target.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR) +
            (target.get(Calendar.YEAR) - today.get(Calendar.YEAR)) * 365

    val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(triggerAtMillis))
    return when {
        dayDiff == 0 -> time
        dayDiff == 1 -> "$time ($tomorrowSuffix)"
        dayDiff in 2..6 -> {
            val day = SimpleDateFormat("EEEE", Locale("iw", "IL")).format(Date(triggerAtMillis))
            "$time · $day"
        }
        else -> {
            val date = SimpleDateFormat("d/M", Locale("iw", "IL")).format(Date(triggerAtMillis))
            "$time · $date"
        }
    }
}
