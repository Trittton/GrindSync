package dev.gatsyuk.soloranking.feature.profile

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gatsyuk.soloranking.core.datastore.UserPreferencesRepository
import dev.gatsyuk.soloranking.core.export.BackupSerializer
import dev.gatsyuk.soloranking.core.export.BackupWorker
import dev.gatsyuk.soloranking.core.export.ExportImportRepository
import dev.gatsyuk.soloranking.core.model.Sex
import dev.gatsyuk.soloranking.core.model.ThemeMode
import dev.gatsyuk.soloranking.core.model.WeightUnit
import dev.gatsyuk.soloranking.core.model.Weights
import dev.gatsyuk.soloranking.core.model.formatSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface DataOpResult {
    data class Success(val message: String) : DataOpResult
    data class Failure(val message: String) : DataOpResult
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val prefs: UserPreferencesRepository,
    private val exportImport: ExportImportRepository,
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
    val autoBackup = prefs.autoBackupEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launch { prefs.setWeightUnit(unit) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setRestTimerSeconds(seconds: Int) = viewModelScope.launch { prefs.setRestTimerSeconds(seconds) }
    fun setSex(sex: Sex) = viewModelScope.launch { prefs.setSex(sex) }
    fun setBodyweightFallbackKg(kg: Double?) = viewModelScope.launch { prefs.setBodyweightFallbackKg(kg) }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutoBackupEnabled(enabled)
        if (enabled) BackupWorker.schedule(context) else BackupWorker.cancel(context)
    }

    /** Writes to a SAF-provided uri (JSON or CSV, decided by [kind]). */
    fun exportTo(uri: Uri, kind: ExportKind, onResult: (DataOpResult) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val snapshot = exportImport.buildSnapshot()
                val content = when (kind) {
                    ExportKind.JSON -> BackupSerializer.toJson(snapshot)
                    ExportKind.CSV_WORKOUTS -> BackupSerializer.workoutsCsv(snapshot)
                    ExportKind.CSV_NUTRITION -> BackupSerializer.nutritionCsv(snapshot)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        ?: error("Could not open the destination file.")
                }
            }
            onResult(
                result.fold(
                    onSuccess = { DataOpResult.Success("Exported successfully.") },
                    onFailure = { DataOpResult.Failure("Export failed: ${it.message}") },
                ),
            )
        }
    }

    fun importFrom(uri: Uri, onResult: (DataOpResult) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not open the file.")
                }
                exportImport.importJson(text)
            }
            onResult(
                result.fold(
                    onSuccess = { DataOpResult.Success("Import complete. All data replaced.") },
                    onFailure = { DataOpResult.Failure("Import failed: ${it.message}") },
                ),
            )
        }
    }
}

enum class ExportKind { JSON, CSV_WORKOUTS, CSV_NUTRITION }

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
    val autoBackup by viewModel.autoBackup.collectAsStateWithLifecycle()

    var status by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportTo(it, ExportKind.JSON) { r -> status = r.message() } } }

    val workoutsCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportTo(it, ExportKind.CSV_WORKOUTS) { r -> status = r.message() } } }

    val nutritionCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportTo(it, ExportKind.CSV_NUTRITION) { r -> status = r.message() } } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Text("Weight unit", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display only. All data is stored in kg.",
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
                    label = { Text("Bodyweight (${Weights.unitLabel(weightUnit)}), fallback") },
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

            HorizontalDivider()

            Column {
                Text("Data: export & import", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your data is yours. JSON is a full backup (and the way to move data " +
                        "between phones or share with friends); CSV is for spreadsheets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { jsonExportLauncher.launch("solo-ranking-backup.json") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Export full backup (JSON)") }
                OutlinedButton(
                    onClick = { workoutsCsvLauncher.launch("solo-ranking-workouts.csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export workouts (CSV)") }
                OutlinedButton(
                    onClick = { nutritionCsvLauncher.launch("solo-ranking-nutrition.csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export nutrition (CSV)") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import backup (JSON)") }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic daily backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Saves a JSON copy to the app's backups folder each day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoBackup, onCheckedChange = { viewModel.setAutoBackup(it) })
                }
            }
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "Importing replaces everything currently in the app with the contents " +
                        "of this backup. This can't be undone. Consider exporting first.",
                )
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    viewModel.importFrom(uri) { r -> status = r.message() }
                    pendingImportUri = null
                }) { Text("Replace") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            },
        )
    }

    status?.let { message ->
        AlertDialog(
            onDismissRequest = { status = null },
            confirmButton = { OutlinedButton(onClick = { status = null }) { Text("OK") } },
            text = { Text(message) },
        )
    }
}

private fun DataOpResult.message(): String = when (this) {
    is DataOpResult.Success -> message
    is DataOpResult.Failure -> message
}
