package dev.glacia.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.glaciaPreferences by preferencesDataStore(name = "glacia_settings")
enum class ThemeChoice { SYSTEM, DARK, LIGHT }

class ThemePreferences(private val context: Context) {
    private val key = stringPreferencesKey("theme")

    val theme: Flow<ThemeChoice> = context.glaciaPreferences.data.map { preferences ->
        runCatching { ThemeChoice.valueOf(preferences[key] ?: "SYSTEM") }
            .getOrDefault(ThemeChoice.SYSTEM)
    }

    suspend fun setTheme(choice: ThemeChoice) {
        context.glaciaPreferences.edit { it[key] = choice.name }
    }
}
