package dev.gatsyuk.grindsync.feature.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gatsyuk.grindsync.core.database.dao.DiaryEntryWithFood
import dev.gatsyuk.grindsync.core.database.entity.FoodItemEntity
import dev.gatsyuk.grindsync.core.database.entity.NutritionTargetEntity
import dev.gatsyuk.grindsync.core.model.Meal
import dev.gatsyuk.grindsync.core.stats.NutritionMath
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

val ProteinColor = Color(0xFFE0654A)
val CarbsColor = Color(0xFFE39A3C)

fun mealLabel(meal: Meal): String =
    meal.name.lowercase().replaceFirstChar { it.uppercase() }

/** Daily nutrition view (SPEC §6.6): ring + macro bars vs targets, meals list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(viewModel: NutritionViewModel = hiltViewModel()) {
    val date by viewModel.date.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val weekKcal by viewModel.weekKcal.collectAsStateWithLifecycle()

    var addSheetMeal by remember { mutableStateOf<Meal?>(null) }
    var editEntry by remember { mutableStateOf<DiaryEntryWithFood?>(null) }
    var showTargets by remember { mutableStateOf(false) }

    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrition") },
                actions = {
                    IconButton(onClick = { showTargets = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit targets")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    TextButton(onClick = viewModel::today, modifier = Modifier.weight(1f)) {
                        Text(if (date == LocalDate.now()) "Today" else date.format(dateFormat))
                    }
                    IconButton(onClick = viewModel::nextDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
            }

            item { DaySummaryCard(totals, target) }
            item { WeekCard(weekKcal, target.kcalTarget) }

            Meal.entries.forEach { meal ->
                item(key = meal.name) {
                    MealCard(
                        meal = meal,
                        entries = entries.filter { it.entry.meal == meal },
                        onAdd = { addSheetMeal = meal },
                        onEntryTap = { editEntry = it },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    addSheetMeal?.let { meal ->
        AddFoodSheet(
            meal = meal,
            viewModel = viewModel,
            onDismiss = {
                addSheetMeal = null
                viewModel.clearOffSearch()
            },
        )
    }

    editEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onSave = { qty ->
                viewModel.updateQuantity(entry.entry, qty)
                editEntry = null
            },
            onDelete = {
                viewModel.deleteEntry(entry.entry)
                editEntry = null
            },
            onDismiss = { editEntry = null },
        )
    }

    if (showTargets) {
        TargetsDialog(
            current = target,
            onSave = { kcal, p, c, f ->
                viewModel.setTarget(kcal, p, c, f)
                showTargets = false
            },
            onDismiss = { showTargets = false },
        )
    }
}

@Composable
private fun DaySummaryCard(totals: NutritionMath.DayTotals, target: NutritionTargetEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalorieRing(
                    consumed = totals.kcal,
                    targetKcal = target.kcalTarget,
                    modifier = Modifier.size(132.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    MacroBar("Protein", totals.proteinG, target.proteinTargetG, ProteinColor)
                    MacroBar("Carbs", totals.carbsG, target.carbsTargetG, CarbsColor)
                    MacroBar("Fat", totals.fatG, target.fatTargetG, MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun CalorieRing(consumed: Double, targetKcal: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = if (consumed > targetKcal) ProteinColor else MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22f
            val inset = stroke / 2 + 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = track,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke),
            )
            val sweep = (min(1.0, consumed / targetKcal.coerceAtLeast(1)) * 360).toFloat()
            drawArc(
                color = progressColor,
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(consumed.roundToInt().toString(), style = MaterialTheme.typography.headlineSmall)
            Text(
                "of $targetKcal kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroBar(label: String, valueG: Double, targetG: Int, color: Color) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                "${valueG.roundToInt()} / $targetG g",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { min(1f, (valueG / targetG.coerceAtLeast(1)).toFloat()) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun WeekCard(weekKcal: List<Double>, targetKcal: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val avg = weekKcal.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }
            Text(
                "Last 7 days · avg ${avg.roundToInt()} kcal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            val max = (weekKcal.maxOrNull() ?: 0.0).coerceAtLeast(targetKcal.toDouble())
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                weekKcal.forEach { kcal ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.height(40.dp).width(18.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(((36 * kcal / max).roundToInt().coerceAtLeast(2)).dp)
                                    .background(
                                        if (kcal > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<DiaryEntryWithFood>,
    onAdd: () -> Unit,
    onEntryTap: (DiaryEntryWithFood) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mealLabel(meal),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                val kcal = NutritionMath.totals(entries).kcal
                if (kcal > 0) {
                    Text(
                        "${kcal.roundToInt()} kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onAdd) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add to ${mealLabel(meal)}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            entries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.food.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            quantityLabel(entry.food, entry.entry.quantityServings),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${(entry.food.kcalPerServing * entry.entry.quantityServings).roundToInt()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onEntryTap(entry) }) { Text("Edit") }
                }
            }
            if (entries.isEmpty()) {
                Text(
                    "Nothing logged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun quantityLabel(food: FoodItemEntity, quantity: Double): String {
    return if (food.servingUnit == "g" && food.servingSize == 100.0) {
        "${(quantity * 100).roundToInt()} g"
    } else {
        val q = if (quantity == quantity.toLong().toDouble()) {
            quantity.toLong().toString()
        } else {
            "%.2f".format(quantity).trimEnd('0').trimEnd('.')
        }
        "$q × ${food.servingSize.roundToInt()} ${food.servingUnit}"
    }
}
