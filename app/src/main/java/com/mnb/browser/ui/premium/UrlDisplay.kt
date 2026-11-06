// Derived from Hydrogen Browser (MIT), copyright 2022 Liu Zihao, modified for MNB. See THIRD_PARTY_NOTICES.md
package com.mnb.browser.ui.premium


object UrlUtils {

    /**
     * Simplify a URL for address-bar display (like Chrome):
     * strip scheme, trivial subdomains (www., m.), and lone trailing slash.
     */
    fun simplifyForDisplay(url: String): String {
        var result = url
            .removePrefix("https://")
            .removePrefix("http://")
        if (result.startsWith("www.")) {
            result = result.removePrefix("www.")
        } else if (result.startsWith("m.")) {
            result = result.removePrefix("m.")
        }
        if (result.endsWith("/") && result.count { it == '/' } == 1) {
            result = result.removeSuffix("/")
        }
        return result
    }
}
