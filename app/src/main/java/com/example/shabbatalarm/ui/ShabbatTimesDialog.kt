package com.example.shabbatalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.shabbatalarm.R
import com.example.shabbatalarm.alarm.AdvancedZmanim
import com.example.shabbatalarm.alarm.AlarmRepository
import com.example.shabbatalarm.alarm.DvarTorah
import com.example.shabbatalarm.alarm.IsraeliCity
import com.example.shabbatalarm.alarm.ShabbatTimesCalculator

@Composable
fun ShabbatTimesDialog(
    defaultCityIndex: Int,
    onCityChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val result = remember { ShabbatTimesCalculator.calculateForUpcomingShabbat() }
    val cities = remember { ShabbatTimesCalculator.CITIES }
    val safeIndex = defaultCityIndex.coerceIn(0, cities.lastIndex)
    val advancedZmanim = remember(safeIndex) {
        ShabbatTimesCalculator.computeAdvancedZmanim(cities[safeIndex])
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = 20.dp)) {
                val holiday = result.holidayInfo
                val titleRes = when {
                    holiday?.combinedWithShabbat == true -> R.string.shabbat_times_title_combined
                    holiday != null -> R.string.shabbat_times_title_yom_tov
                    else -> R.string.shabbat_times_title
                }
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.formatFridayDate(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (holiday != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.shabbat_times_holiday_prefix, holiday.hebrewName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = stringResource(R.string.shabbat_tab_times),
                                fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = stringResource(R.string.shabbat_tab_zmanim),
                                fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = stringResource(R.string.shabbat_tab_dvar_torah),
                                fontWeight = if (selectedTab == 2) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> EntryExitContent(times = result.times)
                    1 -> AdvancedZmanimContent(
                        zmanim = advancedZmanim,
                        cities = cities,
                        selectedCityIndex = safeIndex,
                        onCityChange = onCityChange
                    )
                    2 -> DvarTorahContent(fridayLabel = result.formatFridayDate())
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryExitContent(times: List<com.example.shabbatalarm.alarm.ShabbatTimes>) {
    Column {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.shabbat_times_column_city),
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.shabbat_times_column_entry),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.shabbat_times_column_exit),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        times.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.4f)) {
                    Text(
                        text = entry.city.nameHe,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.city.nameEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = entry.candleLighting,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = entry.havdalah,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (index != times.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.shabbat_times_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdvancedZmanimContent(
    zmanim: AdvancedZmanim,
    cities: List<IsraeliCity>,
    selectedCityIndex: Int,
    onCityChange: (Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        // Clickable city header with dropdown
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = zmanim.city.nameHe,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = zmanim.city.nameEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                cities.forEachIndexed { index, city ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = city.nameHe,
                                color = if (index == selectedCityIndex)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (index == selectedCityIndex)
                                    FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            if (index != selectedCityIndex) onCityChange(index)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.zmanim_city_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        ZmanimRow(stringResource(R.string.zmanim_sof_zman_shma), zmanim.sofZmanShma)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ZmanimRow(stringResource(R.string.zmanim_sof_zman_tfila), zmanim.sofZmanTfila)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ZmanimRow(stringResource(R.string.zmanim_chatzos), zmanim.chatzos)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ZmanimRow(stringResource(R.string.zmanim_mincha_gedola), zmanim.minchaGedola)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ZmanimRow(stringResource(R.string.zmanim_mincha_ketana), zmanim.minchaKetana)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ZmanimRow(stringResource(R.string.zmanim_plag_hamincha), zmanim.plagHamincha)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ZmanimRow(stringResource(R.string.zmanim_shekia), zmanim.shekia)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.zmanim_method_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/**
 * Tab body for the user-written Dvar Torah. Three states:
 *  - **Empty**: no entry saved → CTA to write one.
 *  - **Stale**: an entry exists but its fridayKey is older than the upcoming
 *    Friday → small banner suggesting to refresh; old text still readable.
 *  - **Fresh**: entry matches upcoming Friday → straight readout.
 *
 *  In all states, an edit / write button toggles into a textfield + save flow.
 *  The entry is saved against the *upcoming* Friday's key, so tapping save
 *  during a stale state implicitly "rolls forward" the entry to this week.
 */
@Composable
private fun DvarTorahContent(fridayLabel: String) {
    val context = LocalContext.current
    val repo = remember { AlarmRepository(context) }
    val upcomingKey = remember { ShabbatTimesCalculator.upcomingFridayKey() }

    var saved by remember { mutableStateOf(repo.getDvarTorah()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    val isStale = saved != null && saved!!.fridayKey != upcomingKey

    Column(
        modifier = Modifier.heightIn(min = 200.dp, max = 460.dp)
    ) {
        // Header: "לשבת <date>" with edit / write button on the trailing side.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dvar_torah_for_friday, fridayLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!editing) {
                IconButton(onClick = {
                    draft = saved?.text.orEmpty()
                    editing = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(
                            if (saved == null) R.string.dvar_torah_write
                            else R.string.dvar_torah_edit
                        ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isStale && !editing) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dvar_torah_stale_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (editing) {
            DvarTorahEditor(
                draft = draft,
                onDraftChange = { draft = it },
                onSave = {
                    repo.setDvarTorah(draft, upcomingKey)
                    saved = repo.getDvarTorah()
                    editing = false
                },
                onCancel = {
                    draft = ""
                    editing = false
                }
            )
        } else {
            DvarTorahReadout(
                saved = saved,
                onWrite = {
                    draft = ""
                    editing = true
                },
                onClearRequest = { showClearConfirm = true }
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            text = { Text(stringResource(R.string.dvar_torah_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    repo.clearDvarTorah()
                    saved = null
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.dvar_torah_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.dvar_torah_cancel))
                }
            }
        )
    }
}

@Composable
private fun DvarTorahReadout(
    saved: DvarTorah?,
    onWrite: () -> Unit,
    onClearRequest: () -> Unit
) {
    if (saved == null) {
        // Empty state.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.dvar_torah_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.dvar_torah_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onWrite) {
                Text(stringResource(R.string.dvar_torah_write))
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = saved.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onClearRequest) {
                Text(
                    text = stringResource(R.string.dvar_torah_clear),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DvarTorahEditor(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = {
                Text(stringResource(R.string.dvar_torah_placeholder))
            },
            minLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.dvar_torah_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = draft.isNotBlank()
            ) {
                Text(stringResource(R.string.dvar_torah_save))
            }
        }
    }
}

@Composable
private fun ZmanimRow(label: String, time: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = time,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}
