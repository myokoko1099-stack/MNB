package com.mnb.browser.library

/** Loading state of one list (bookmarks or history). */
enum class LoadState { Loading, Ready, Error }

/** One row of the bookmark list or the history list. [timeMillis] is the save time or the visit time. */
data class LibraryEntry(val id: Long, val url: String, val title: String, val timeMillis: Long) {
    val displayTitle: String get() = LibraryText.displayTitle(title, url)
}

/** Text of the Bookmarks and History screens and of their menu entries (pure, unit-testable). */
object LibraryText {
    const val MAX_TITLE_LENGTH = 300

    const val MENU_BOOKMARK_ADD = "Bookmark this page"
    const val MENU_BOOKMARK_REMOVE = "Remove bookmark"
    const val MENU_BOOKMARKS = "Bookmarks"
    const val MENU_HISTORY = "History"

    const val BOOKMARKS_TITLE = "Bookmarks"
    const val BOOKMARKS_EMPTY_TITLE = "No bookmarks yet"
    const val BOOKMARKS_EMPTY_BODY = "Open a page, then choose More > Bookmark this page to keep it here."
    const val BOOKMARK_DELETE = "Delete bookmark"

    const val HISTORY_TITLE = "History"
    const val HISTORY_EMPTY_TITLE = "No history yet"
    const val HISTORY_EMPTY_BODY = "Pages you finish loading show up here, newest first."
    const val HISTORY_DELETE = "Delete history entry"
    const val CLEAR_ALL = "Clear all"
    const val CLEAR_CONFIRM_TITLE = "Clear all history?"
    const val CLEAR_CONFIRM_BODY =
        "This removes every page from your browsing history. Bookmarks and open tabs are not affected."
    const val CLEAR_CONFIRM_ACTION = "Clear"
    const val CANCEL = "Cancel"

    const val LOAD_ERROR_TITLE = "Couldn't load this list"
    const val LOAD_ERROR_BODY = "Something went wrong while reading your saved data."
    const val RETRY = "Retry"
    const val DONE = "Done"

    fun bookmarkMenuLabel(isBookmarked: Boolean): String =
        if (isBookmarked) MENU_BOOKMARK_REMOVE else MENU_BOOKMARK_ADD

    /** The title to show: the stored title, or the address when the title is blank. */
    fun displayTitle(title: String?, url: String): String =
        title?.trim()?.takeIf { it.isNotEmpty() } ?: url

    /** A title that is safe to store: trimmed, never null, never absurdly long. */
    fun cleanTitle(title: String?): String = title.orEmpty().trim().take(MAX_TITLE_LENGTH)

    /** "Just now", "5 min ago", "3 h ago", "2 d ago". A time in the future counts as "Just now". */
    fun timeAgo(nowMillis: Long, thenMillis: Long): String {
        val diff = nowMillis - thenMillis
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            diff < minute -> "Just now"
            diff < hour -> "${diff / minute} min ago"
            diff < day -> "${diff / hour} h ago"
            else -> "${diff / day} d ago"
        }
    }
}
