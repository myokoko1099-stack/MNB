package com.mnb.browser.ui.premium

import com.mnb.browser.navigation.UrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressInputTest {
    private val page = "https://www.royalroad.com/fictions/best-rated"

    @Test fun unchangedAddressOnlyClosesTheOverlay() =
        assertEquals(AddressInput.Action.Dismiss, AddressInput.onSubmit(page, page))

    @Test fun unchangedAddressWithSpacesOnlyClosesTheOverlay() =
        assertEquals(AddressInput.Action.Dismiss, AddressInput.onSubmit(page, "  $page \n"))

    @Test fun emptyTextClosesTheOverlay() {
        assertEquals(AddressInput.Action.Dismiss, AddressInput.onSubmit(page, ""))
        assertEquals(AddressInput.Action.Dismiss, AddressInput.onSubmit("", "   "))
    }

    @Test fun newSearchTextNavigatesAndReplacesThePage() =
        assertEquals(AddressInput.Action.Navigate("slow life novel"), AddressInput.onSubmit(page, " slow life novel "))

    @Test fun editedAddressNavigates() =
        assertEquals(AddressInput.Action.Navigate("$page?page=2"), AddressInput.onSubmit(page, "$page?page=2"))

    @Test fun anythingTypedOnTheHomePageNavigates() =
        assertEquals(AddressInput.Action.Navigate("wattpad.com"), AddressInput.onSubmit("", "wattpad.com"))

    @Test fun submittedSearchTextBecomesAGoogleSearch() {
        val action = AddressInput.onSubmit(page, "slow life novel") as AddressInput.Action.Navigate
        val resolved = UrlPolicy.resolve(action.text) as UrlPolicy.Input.Ok
        assertTrue(resolved.isSearch)
        assertEquals("https://www.google.com/search?q=slow+life+novel", resolved.url)
    }

    @Test fun hintNamesTheSearchEngine() = assertEquals("Search Google or enter address", AddressInput.SEARCH_HINT)
}
