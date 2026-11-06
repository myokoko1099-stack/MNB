package com.mnb.browser.tabs

import com.mnb.browser.navigation.UrlPolicy
import com.mnb.browser.ui.premium.UrlUtils

/** Text shown for tabs (pure functions, unit-testable). */
object TabDisplay {
    const val NEW_TAB_LABEL = "New tab"

    fun label(tab: TabInfo): String = when {
        tab.title.isNotBlank() -> tab.title
        tab.url.isBlank() || tab.url.trimEnd('/') == UrlPolicy.HOME_URL.trimEnd('/') -> NEW_TAB_LABEL
        else -> UrlUtils.simplifyForDisplay(tab.url)
    }

    fun countBadge(count: Int): String = if (count > 99) "99+" else count.toString()
}
