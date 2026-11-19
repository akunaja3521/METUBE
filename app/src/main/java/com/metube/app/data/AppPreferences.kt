package com.metube.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "metube_settings")

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class ViewMode { POSTER, THUMBNAIL }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val viewMode: ViewMode = ViewMode.POSTER,
    val autoplayNext: Boolean = true,
    val manualFolderUris: Set<String> = emptySet(),
    val subtitleEnabled: Boolean = true,
    val subtitleSizeSp: Float = 18f,
    val subtitleColorArgb: Int = android.graphics.Color.WHITE,
    val subtitleBottomPadding: Float = 0.08f,
    val subtitleSyncOffsetMs: Long = 0L
)

class AppPreferences(private val context: Context) {
    companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
        val KEY_AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val KEY_MANUAL_FOLDERS = stringSetPreferencesKey("manual_folder_uris")
        val KEY_SUB_ENABLED = booleanPreferencesKey("sub_enabled")
        val KEY_SUB_SIZE = floatPreferencesKey("sub_size_sp")
        val KEY_SUB_COLOR = intPreferencesKey("sub_color_argb")
        val KEY_SUB_PADDING = floatPreferencesKey("sub_bottom_padding")
        val KEY_SUB_SYNC = longPreferencesKey("sub_sync_ms")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.DARK.name)
            }.getOrDefault(ThemeMode.DARK),
            viewMode = runCatching {
                ViewMode.valueOf(prefs[KEY_VIEW_MODE] ?: ViewMode.POSTER.name)
            }.getOrDefault(ViewMode.POSTER),
            autoplayNext = prefs[KEY_AUTOPLAY_NEXT] ?: true,
            manualFolderUris = prefs[KEY_MANUAL_FOLDERS] ?: emptySet(),
            subtitleEnabled = prefs[KEY_SUB_ENABLED] ?: true,
            subtitleSizeSp = prefs[KEY_SUB_SIZE] ?: 18f,
            subtitleColorArgb = prefs[KEY_SUB_COLOR] ?: android.graphics.Color.WHITE,
            subtitleBottomPadding = prefs[KEY_SUB_PADDING] ?: 0.08f,
            subtitleSyncOffsetMs = prefs[KEY_SUB_SYNC] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.dataStore.edit { it[KEY_VIEW_MODE] = mode.name }
    }

    suspend fun setAutoplayNext(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTOPLAY_NEXT] = enabled }
    }

    suspend fun addManualFolderUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_MANUAL_FOLDERS] ?: emptySet()
            prefs[KEY_MANUAL_FOLDERS] = current + uriString
        }
    }

    suspend fun removeManualFolderUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_MANUAL_FOLDERS] ?: emptySet()
            prefs[KEY_MANUAL_FOLDERS] = current - uriString
        }
    }

    suspend fun updateSubtitleConfig(
        enabled: Boolean? = null,
        sizeSp: Float? = null,
        colorArgb: Int? = null,
        bottomPadding: Float? = null,
        syncOffsetMs: Long? = null
    ) {
        context.dataStore.edit { prefs ->
            enabled?.let { prefs[KEY_SUB_ENABLED] = it }
            sizeSp?.let { prefs[KEY_SUB_SIZE] = it }
            colorArgb?.let { prefs[KEY_SUB_COLOR] = it }
            bottomPadding?.let { prefs[KEY_SUB_PADDING] = it }
            syncOffsetMs?.let { prefs[KEY_SUB_SYNC] = it }
        }
    }
}
