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

enum class WaraqThemeId(val title: String, val desc: String, val previewColorHex: String) {
    CHARCOAL_NEON("فحمي ونيون (الافتراضي)", "أسود كربوني فاخر مع أخضر نيون", "#39F58A"),
    AMOLED_BLACK("أسود حالك AMOLED", "خلفية سوداء نقية 100% موفرة للطاقة", "#00E676"),
    MIDNIGHT_NAVY("أزرق ليلي ملكي", "كحلي داكن عميق مع سماوي هادئ", "#00D2D3"),
    WARM_SEPIA("ورق دافئ كلاسيكي", "ألوان ورق الكتب القديمة لراحة العين", "#D4A373"),
    PURE_LIGHT("أبيض نقي ومشرق", "تصميم نهاري صافٍ مع أخضر زمردي", "#00874A")
}

data class UserPreferences(
    val themeId: String = WaraqThemeId.CHARCOAL_NEON.name,
    val isDarkTheme: Boolean = true,
    val wordFontSizeSp: Float = 17f,
    val pdfFitMode: String = "WIDTH", // "WIDTH" or "PAGE"
    val isGridView: Boolean = false // Default to list view as in screenshot
)

class UserPreferencesRepository(private val context: Context) {
    private val keyThemeId = stringPreferencesKey("app_theme_id")
    private val keyDarkTheme = booleanPreferencesKey("is_dark_theme")
    private val keyWordFontSize = floatPreferencesKey("word_font_size_sp")
    private val keyPdfFitMode = stringPreferencesKey("pdf_fit_mode")
    private val keyGridView = booleanPreferencesKey("is_grid_view")

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeId = prefs[keyThemeId] ?: WaraqThemeId.CHARCOAL_NEON.name,
            isDarkTheme = prefs[keyDarkTheme] ?: true,
            wordFontSizeSp = prefs[keyWordFontSize] ?: 17f,
            pdfFitMode = prefs[keyPdfFitMode] ?: "WIDTH",
            isGridView = prefs[keyGridView] ?: false
        )
    }

    suspend fun setThemeId(themeId: String) {
        context.dataStore.edit { it[keyThemeId] = themeId }
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
