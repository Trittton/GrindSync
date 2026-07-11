package dev.gatsyuk.grindsync.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gatsyuk.grindsync.core.database.dao.DiaryEntryWithFood
import dev.gatsyuk.grindsync.core.database.entity.FoodItemEntity
import dev.gatsyuk.grindsync.core.database.entity.NutritionTargetEntity
import dev.gatsyuk.grindsync.core.model.FoodSource
import dev.gatsyuk.grindsync.core.model.Meal
import kotlin.math.roundToInt

/**
 * Add-food flow (SPEC §6.6): recents, favorites, my foods, plus an EXPLICIT
 * "search online" action for Open Food Facts — the only thing in the app
 * that touches the network (NFR-8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    meal: Meal,
    viewModel: NutritionViewModel,
    onDismiss: () -> Unit,
) {
    val allFoods by viewModel.allFoods.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val offState by viewModel.offSearch.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var quantityFor by remember { mutableStateOf<FoodItemEntity?>(null) }
    var showCustomForm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Add to ${mealLabel(meal)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search foods") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.searchOnline(query) },
                    enabled = query.isNotBlank() && offState !is OffSearchState.Loading,
                    modifier = Modifier.weight(1f),
                ) { Text("Search online") }
                OutlinedButton(
                    onClick = { showCustomForm = true },
                    modifier = Modifier.weight(1f),
                ) { Text("New food") }
            }

            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                when (val state = offState) {
                    is OffSearchState.Loading -> item {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator() }
                    }
                    is OffSearchState.Error -> item {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    is OffSearchState.Results -> {
                        item { SectionHeader("Open Food Facts (${state.foods.size})") }
                        if (state.foods.isEmpty()) {
                            item {
                                Text(
                                    "No usable results (products need kcal data).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.foods) { food ->
                            FoodRow(food, onTap = { quantityFor = food }, onStar = null)
                        }
                    }
                    OffSearchState.Idle -> {}
                }

                val filter: (FoodItemEntity) -> Boolean = {
                    query.isBlank() || it.name.contains(query, ignoreCase = true)
                }
                val recentFiltered = recents.filter(filter)
                if (recentFiltered.isNotEmpty()) {
                    item { SectionHeader("Recent") }
                    items(recentFiltered, key = { "r${it.id}" }) { food ->
                        FoodRow(food, onTap = { quantityFor = food }, onStar = { viewModel.toggleFavorite(food) })
                    }
                }
                val favFiltered = favorites.filter(filter).filter { f -> recentFiltered.none { it.id == f.id } }
                if (favFiltered.isNotEmpty()) {
                    item { SectionHeader("Favorites") }
                    items(favFiltered, key = { "f${it.id}" }) { food ->
                        FoodRow(food, onTap = { quantityFor = food }, onStar = { viewModel.toggleFavorite(food) })
                    }
                }
                val rest = allFoods.filter(filter)
                    .filter { f -> recentFiltered.none { it.id == f.id } && favFiltered.none { it.id == f.id } }
                if (rest.isNotEmpty()) {
                    item { SectionHeader("My foods") }
                    items(rest, key = { "a${it.id}" }) { food ->
                        FoodRow(food, onTap = { quantityFor = food }, onStar = { viewModel.toggleFavorite(food) })
                    }
                }
                if (allFoods.isEmpty() && offState is OffSearchState.Idle) {
                    item {
                        Text(
                            "No foods yet — create one or search online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    quantityFor?.let { food ->
        QuantityDialog(
            food = food,
            onConfirm = { qty ->
                viewModel.addToDiary(food, meal, qty)
                quantityFor = null
                onDismiss()
            },
            onDismiss = { quantityFor = null },
        )
    }

    if (showCustomForm) {
        CustomFoodDialog(
            onCreate = { food ->
                viewModel.createCustomFood(food) { created -> quantityFor = created }
                showCustomForm = false
            },
            onDismiss = { showCustomForm = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun FoodRow(food: FoodItemEntity, onTap: () -> Unit, onStar: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(food.name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    food.brand,
                    "${food.kcalPerServing.roundToInt()} kcal / ${food.servingSize.roundToInt()} ${food.servingUnit}",
                    if (food.source == FoodSource.OFF) "OFF" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            if (onStar != null) {
                IconButton(onClick = onStar) {
                    Icon(
                        if (food.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Favorite",
                        tint = if (food.isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onTap),
    )
}

@Composable
fun QuantityDialog(
    food: FoodItemEntity,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val gramsMode = food.servingUnit == "g" && food.servingSize == 100.0
    var text by remember { mutableStateOf(if (gramsMode) "100" else "1") }
    val quantity = text.replace(',', '.').toDoubleOrNull()
        ?.let { if (gramsMode) it / 100.0 else it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (gramsMode) "Amount (g)" else "Servings × ${food.servingSize.roundToInt()} ${food.servingUnit}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                quantity?.let {
                    Text(
                        "= ${(food.kcalPerServing * it).roundToInt()} kcal · " +
                            "P ${(food.proteinG * it).roundToInt()} · " +
                            "C ${(food.carbsG * it).roundToInt()} · " +
                            "F ${(food.fatG * it).roundToInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = quantity != null && quantity > 0,
                onClick = { onConfirm(quantity!!) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Custom food entry — also the §8.3 guardrail: users can recreate any fetched
 *  food with corrected values as their own CUSTOM copy. */
@Composable
fun CustomFoodDialog(
    onCreate: (FoodItemEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var servingSize by remember { mutableStateOf("100") }
    var servingUnit by remember { mutableStateOf("g") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && num(servingSize) != null && num(kcal) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = brand, onValueChange = { brand = it },
                    label = { Text("Brand (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = servingSize, onValueChange = { servingSize = it },
                        label = { Text("Serving") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = servingUnit, onValueChange = { servingUnit = it },
                        label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = kcal, onValueChange = { kcal = it },
                        label = { Text("kcal") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = protein, onValueChange = { protein = it },
                        label = { Text("P (g)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carbs, onValueChange = { carbs = it },
                        label = { Text("C (g)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fat, onValueChange = { fat = it },
                        label = { Text("F (g)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onCreate(
                        FoodItemEntity(
                            name = name.trim(),
                            brand = brand.trim().ifBlank { null },
                            servingSize = num(servingSize)!!,
                            servingUnit = servingUnit.trim().ifBlank { "g" },
                            kcalPerServing = num(kcal)!!,
                            proteinG = num(protein) ?: 0.0,
                            carbsG = num(carbs) ?: 0.0,
                            fatG = num(fat) ?: 0.0,
                        ),
                    )
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EditEntryDialog(
    entry: DiaryEntryWithFood,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gramsMode = entry.food.servingUnit == "g" && entry.food.servingSize == 100.0
    var text by remember {
        mutableStateOf(
            if (gramsMode) (entry.entry.quantityServings * 100).roundToInt().toString()
            else entry.entry.quantityServings.toString(),
        )
    }
    val quantity = text.replace(',', '.').toDoubleOrNull()
        ?.let { if (gramsMode) it / 100.0 else it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.food.name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(if (gramsMode) "Amount (g)" else "Servings") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = quantity != null && quantity > 0,
                onClick = { onSave(quantity!!) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun TargetsDialog(
    current: NutritionTargetEntity,
    onSave: (Int, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var kcal by remember { mutableStateOf(current.kcalTarget.toString()) }
    var protein by remember { mutableStateOf(current.proteinTargetG.toString()) }
    var carbs by remember { mutableStateOf(current.carbsTargetG.toString()) }
    var fat by remember { mutableStateOf(current.fatTargetG.toString()) }
    val valid = listOf(kcal, protein, carbs, fat).all { it.toIntOrNull() != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily targets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = kcal, onValueChange = { kcal = it }, label = { Text("Calories (kcal)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protein, onValueChange = { protein = it }, label = { Text("Protein g") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = carbs, onValueChange = { carbs = it }, label = { Text("Carbs g") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fat, onValueChange = { fat = it }, label = { Text("Fat g") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(kcal.toInt(), protein.toInt(), carbs.toInt(), fat.toInt()) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
