package dev.gatsyuk.grindsync.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.data.NutritionRepository
import dev.gatsyuk.grindsync.core.database.dao.DiaryEntryWithFood
import dev.gatsyuk.grindsync.core.database.entity.DiaryEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.FoodItemEntity
import dev.gatsyuk.grindsync.core.model.Meal
import dev.gatsyuk.grindsync.core.stats.NutritionMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface OffSearchState {
    data object Idle : OffSearchState
    data object Loading : OffSearchState
    data class Results(val foods: List<FoodItemEntity>) : OffSearchState
    data class Error(val message: String) : OffSearchState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: NutritionRepository,
) : ViewModel() {

    val date = MutableStateFlow(LocalDate.now())

    val entries: StateFlow<List<DiaryEntryWithFood>> = date
        .flatMapLatest { repository.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totals = entries.map { NutritionMath.totals(it) }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            NutritionMath.DayTotals(0.0, 0.0, 0.0, 0.0),
        )

    val target = date
        .flatMapLatest { repository.observeTargetFor(it) }
        .map { it ?: repository.defaultTarget }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.defaultTarget)

    /** kcal per day for the 7 days ending at [date] (oldest first). */
    val weekKcal = date
        .flatMapLatest { d ->
            repository.observeRange(d.minusDays(6), d).map { entries ->
                val byDay = entries.groupBy { it.entry.date }
                (6 downTo 0).map { back ->
                    val day = d.minusDays(back.toLong())
                    NutritionMath.totals(byDay[day].orEmpty()).kcal
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(7) { 0.0 })

    val allFoods = repository.observeAllFoods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recents = repository.observeRecentFoods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val offSearch = MutableStateFlow<OffSearchState>(OffSearchState.Idle)

    fun previousDay() { date.value = date.value.minusDays(1) }
    fun nextDay() { date.value = date.value.plusDays(1) }
    fun today() { date.value = LocalDate.now() }

    /** Explicit user action — the app's only network call (NFR-8). */
    fun searchOnline(query: String) {
        if (query.isBlank()) return
        offSearch.value = OffSearchState.Loading
        viewModelScope.launch {
            repository.searchOpenFoodFacts(query)
                .onSuccess { offSearch.value = OffSearchState.Results(it) }
                .onFailure { error ->
                    android.util.Log.w("OffSearch", "Open Food Facts search failed", error)
                    offSearch.value = OffSearchState.Error(
                        "Search failed — check the connection and try again.",
                    )
                }
        }
    }

    fun clearOffSearch() { offSearch.value = OffSearchState.Idle }

    fun addToDiary(food: FoodItemEntity, meal: Meal, quantityServings: Double) {
        viewModelScope.launch {
            val id = repository.ensureLocalFood(food)
            repository.logEntry(date.value, meal, id, quantityServings)
        }
    }

    fun createCustomFood(food: FoodItemEntity, onCreated: (FoodItemEntity) -> Unit) {
        viewModelScope.launch {
            val id = repository.createFood(food)
            onCreated(food.copy(id = id))
        }
    }

    fun toggleFavorite(food: FoodItemEntity) = viewModelScope.launch {
        repository.toggleFavorite(food)
    }

    fun updateQuantity(entry: DiaryEntryEntity, quantity: Double) = viewModelScope.launch {
        repository.updateEntryQuantity(entry, quantity)
    }

    fun deleteEntry(entry: DiaryEntryEntity) = viewModelScope.launch {
        repository.deleteEntry(entry)
    }

    fun setTarget(kcal: Int, protein: Int, carbs: Int, fat: Int) = viewModelScope.launch {
        repository.setTarget(kcal, protein, carbs, fat)
    }
}
