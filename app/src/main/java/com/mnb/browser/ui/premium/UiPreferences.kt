// Derived from Hydrogen Browser (MIT), copyright 2022 Liu Zihao, modified for MNB. See THIRD_PARTY_NOTICES.md
package com.mnb.browser.ui.premium

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class ThemeMode { System, Light, Dark }

class UiPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("mnb_ui", Context.MODE_PRIVATE)

    var themeMode: ThemeMode by mutableStateOf(readThemeMode())
        private set


    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }


    fun getShortcuts(): List<Shortcut> {
        val json = prefs.getString(KEY_SHORTCUTS, null) ?: return DEFAULT_SHORTCUTS
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Shortcut(obj.getString("name"), obj.getString("url"))
            }
        } catch (_: JSONException) {
            DEFAULT_SHORTCUTS
        }
    }

    fun saveShortcuts(list: List<Shortcut>) {
        val array = JSONArray()
        list.forEach { shortcut ->
            array.put(
                JSONObject().apply {
                    put("name", shortcut.name)
                    put("url", shortcut.url)
                }
            )
        }
        prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply()
    }

    private fun readThemeMode(): ThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, null)
        return ThemeMode.values().firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHORTCUTS = "shortcuts"

        val DEFAULT_SHORTCUTS = listOf(
            Shortcut("DuckDuckGo", "https://duckduckgo.com"),
            Shortcut("Wikipedia", "https://www.wikipedia.org"),
            Shortcut("GitHub", "https://github.com"),
        )
    }
}
