package com.mnb.browser.ui.premium

import com.mnb.browser.navigation.UrlPolicy

/**
 * What to do when the address-bar overlay is submitted (pure logic, unit-testable).
 *
 * The overlay opens with the current page address already in the field. Submitting without having
 * changed it must NOT reload or replace the page, it only closes the overlay. Only new text starts
 * a navigation (an address or a search), which then replaces the current page.
 */
object AddressInput {

    /** Placeholder shown in empty search fields. */
    const val SEARCH_HINT = "Search ${UrlPolicy.SEARCH_ENGINE_NAME} or enter address"

    sealed interface Action {
        data object Dismiss : Action
        data class Navigate(val text: String) : Action
    }

    fun onSubmit(currentUrl: String, typed: String): Action {
        val text = typed.trim()
        if (text.isEmpty()) return Action.Dismiss
        if (currentUrl.isNotBlank() && text == currentUrl.trim()) return Action.Dismiss
        return Action.Navigate(text)
    }
}
