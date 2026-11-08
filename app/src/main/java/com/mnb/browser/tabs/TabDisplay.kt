package com.mnb.browser.tabs

import com.mnb.browser.navigation.UrlPolicy
import com.mnb.browser.ui.premium.UrlUtils

/** Text shown for tabs (pure functions, unit-testable). */
object TabDisplay {
    const val NEW_TAB_LABEL = "New tab"

    fun label(tab: TabInfo): String = when {
        // A tab on the new-tab page is always "New tab", even if it still remembers an old page title.
        UrlPolicy.isNewTabAddress(tab.url) -> NEW_TAB_LABEL
        tab.title.isNotBlank() -> tab.title
        else -> UrlUtils.simplifyForDisplay(tab.url)
    }

    fun countBadge(count: Int): String = if (count > 99) "99+" else count.toString()
}
