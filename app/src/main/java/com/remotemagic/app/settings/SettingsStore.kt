package com.remotemagic.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Blocks of the remote screen that the user can reorder. */
enum class RemoteSection { APPS, NAVIGATION, DPAD, NUMBERS, MEDIA }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val vibrate: Boolean = true,
    val sounds: Boolean = false,
    val autoConnect: Boolean = true,
    val order: List<RemoteSection> = RemoteSection.entries
)

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val vibrateKey = booleanPreferencesKey("vibrate")
    private val soundKey = booleanPreferencesKey("sounds")
    private val autoKey = booleanPreferencesKey("auto_connect")
    private val orderKey = stringPreferencesKey("order")

    val settings = context.store.data.map { p ->
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == p[themeKey] } ?: ThemeMode.DARK,
            vibrate = p[vibrateKey] ?: true,
            sounds = p[soundKey] ?: false,
            autoConnect = p[autoKey] ?: true,
            order = parseOrder(p[orderKey])
        )
    }

    suspend fun setTheme(value: ThemeMode) { context.store.edit { it[themeKey] = value.name } }
    suspend fun setVibrate(value: Boolean) { context.store.edit { it[vibrateKey] = value } }
    suspend fun setSounds(value: Boolean) { context.store.edit { it[soundKey] = value } }
    suspend fun setAutoConnect(value: Boolean) { context.store.edit { it[autoKey] = value } }
    suspend fun setOrder(value: List<RemoteSection>) { context.store.edit { p -> p[orderKey] = value.joinToString(",") { it.name } } }
    suspend fun reset() { context.store.edit { it.clear() } }

    companion object {
        fun parseOrder(value: String?): List<RemoteSection> {
            val saved = value.orEmpty().split(',').mapNotNull { n -> RemoteSection.entries.firstOrNull { it.name == n } }
            return (saved + RemoteSection.entries).distinct()
        }
    }
}
