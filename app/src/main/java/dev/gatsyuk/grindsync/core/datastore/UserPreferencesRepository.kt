package dev.gatsyuk.grindsync.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gatsyuk.grindsync.core.model.ThemeMode
import dev.gatsyuk.grindsync.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REST_TIMER_SECONDS = intPreferencesKey("rest_timer_seconds")
    }

    /** Display-only unit; stored data stays kg (SPEC §12.1). */
    val weightUnit: Flow<WeightUnit> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEIGHT_UNIT]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
            ?: WeightUnit.KG
    }

    /** Dark by default — dark-first design system (SPEC §6.8). */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK
    }

    /** Default rest-timer countdown, seconds. */
    val restTimerSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.REST_TIMER_SECONDS] ?: 120
    }

    suspend fun setRestTimerSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.REST_TIMER_SECONDS] = seconds }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        context.dataStore.edit { it[Keys.WEIGHT_UNIT] = unit.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule // UserPreferencesRepository is constructor-injected; module reserved for future bindings.
