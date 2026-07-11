package dev.gatsyuk.grindsync.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.datastore.UserPreferencesRepository
import dev.gatsyuk.grindsync.core.model.ThemeMode
import dev.gatsyuk.grindsync.core.model.Sex
import dev.gatsyuk.grindsync.core.model.WeightUnit
import dev.gatsyuk.grindsync.core.model.Weights
import dev.gatsyuk.grindsync.core.model.formatSeconds
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
) : ViewModel() {
    val weightUnit = prefs.weightUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)
    val themeMode = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
    val restTimerSeconds = prefs.restTimerSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 120)
    val sex = prefs.sex
        .stateIn(viewModelScope, SharingStarted.Eagerly, Sex.UNSET)
    val bodyweightFallbackKg = prefs.bodyweightFallbackKg
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launch { prefs.setWeightUnit(unit) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setRestTimerSeconds(seconds: Int) = viewModelScope.launch { prefs.setRestTimerSeconds(seconds) }
    fun setSex(sex: Sex) = viewModelScope.launch { prefs.setSex(sex) }
    fun setBodyweightFallbackKg(kg: Double?) = viewModelScope.launch { prefs.setBodyweightFallbackKg(kg) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val restSeconds by viewModel.restTimerSeconds.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val bodyweightKg by viewModel.bodyweightFallbackKg.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Text("Weight unit", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display only — all data is stored in kg.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    WeightUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = weightUnit == unit,
                            onClick = { viewModel.setWeightUnit(unit) },
                            shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                        ) { Text(unit.name.lowercase()) }
                    }
                }
            }

            Column {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }

            Column {
                Text("Strength profile", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Used only to normalize ranks (IPF GL & strength standards).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Sex.entries.forEachIndexed { index, s ->
                        SegmentedButton(
                            selected = sex == s,
                            onClick = { viewModel.setSex(s) },
                            shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size),
                        ) {
                            Text(
                                when (s) {
                                    Sex.UNSET -> "Not set"
                                    Sex.MALE -> "Male"
                                    Sex.FEMALE -> "Female"
                                },
                            )
                        }
                    }
                }
                var bwText by remember(bodyweightKg, weightUnit) {
                    mutableStateOf(bodyweightKg?.let { Weights.formatKgAs(it, weightUnit) } ?: "")
                }
                OutlinedTextField(
                    value = bwText,
                    onValueChange = { text ->
                        bwText = text
                        val parsed = text.replace(',', '.').toDoubleOrNull()
                        if (text.isBlank()) {
                            viewModel.setBodyweightFallbackKg(null)
                        } else if (parsed != null) {
                            viewModel.setBodyweightFallbackKg(Weights.displayToKg(parsed, weightUnit))
                        }
                    },
                    label = { Text("Bodyweight (${Weights.unitLabel(weightUnit)}) — fallback") },
                    supportingText = { Text("A bodyweight logged on a workout takes priority.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Column {
                Text("Default rest timer", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val options = listOf(60, 90, 120, 180)
                    options.forEachIndexed { index, seconds ->
                        SegmentedButton(
                            selected = restSeconds == seconds,
                            onClick = { viewModel.setRestTimerSeconds(seconds) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(formatSeconds(seconds)) }
                    }
                }
            }
        }
    }
}
