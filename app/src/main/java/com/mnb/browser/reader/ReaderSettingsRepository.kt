package com.mnb.browser.reader

/**
 * Persists [ReaderSettings] in the same [KeyValueStore] as reading positions. The single
 * "settings" key cannot collide with position keys, which are all prefixed "pos:".
 */
class ReaderSettingsRepository(private val store: KeyValueStore) {

    /** Always returns usable settings: missing or corrupt fields fall back to defaults. */
    fun load(): ReaderSettings {
        val defaults = ReaderSettings()
        val parts = store.get(KEY)?.split(',') ?: return defaults
        if (parts.size != 4) return defaults
        return ReaderSettings(
            fontSizeSp = parts[0].toIntOrNull() ?: defaults.fontSizeSp,
            marginDp = parts[1].toIntOrNull() ?: defaults.marginDp,
            paragraphSpacingDp = parts[2].toIntOrNull() ?: defaults.paragraphSpacingDp,
            theme = ReaderTheme.values().firstOrNull { it.name == parts[3] } ?: defaults.theme,
        ).sanitized()
    }

    fun save(settings: ReaderSettings) {
        val s = settings.sanitized()
        store.put(KEY, "${s.fontSizeSp},${s.marginDp},${s.paragraphSpacingDp},${s.theme.name}")
    }

    private companion object {
        const val KEY = "settings"
    }
}
