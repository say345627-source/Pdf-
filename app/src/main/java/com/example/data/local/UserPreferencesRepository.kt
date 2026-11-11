package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "waraq_settings")

data class UserPreferences(
    val isDarkTheme: Boolean = true,
    val wordFontSizeSp: Float = 17f,
    val pdfFitMode: String = "WIDTH", // "WIDTH" or "PAGE"
    val isGridView: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {
    private val keyDarkTheme = booleanPreferencesKey("is_dark_theme")
    private val keyWordFontSize = floatPreferencesKey("word_font_size_sp")
    private val keyPdfFitMode = stringPreferencesKey("pdf_fit_mode")
    private val keyGridView = booleanPreferencesKey("is_grid_view")

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            isDarkTheme = prefs[keyDarkTheme] ?: true,
            wordFontSizeSp = prefs[keyWordFontSize] ?: 17f,
            pdfFitMode = prefs[keyPdfFitMode] ?: "WIDTH",
            isGridView = prefs[keyGridView] ?: true
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[keyDarkTheme] = enabled }
    }

    suspend fun setWordFontSize(fontSizeSp: Float) {
        context.dataStore.edit { it[keyWordFontSize] = fontSizeSp.coerceIn(12f, 32f) }
    }

    suspend fun setPdfFitMode(mode: String) {
        context.dataStore.edit { it[keyPdfFitMode] = mode }
    }

    suspend fun setGridView(isGrid: Boolean) {
        context.dataStore.edit { it[keyGridView] = isGrid }
    }
}
