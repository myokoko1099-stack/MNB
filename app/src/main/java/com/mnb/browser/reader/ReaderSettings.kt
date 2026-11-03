package com.mnb.browser.reader

import kotlin.math.roundToInt

/** Reader colour mode. SYSTEM keeps the Phase 03 Part 1 behaviour (follow the device). */
enum class ReaderTheme { SYSTEM, LIGHT, DARK }

/**
 * User-adjustable reading appearance. Values are always kept inside the allowed ranges
 * (see [sanitized]) so a corrupt or out-of-range stored value can never break the reader.
 */
data class ReaderSettings(
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val marginDp: Int = DEFAULT_MARGIN_DP,
    val paragraphSpacingDp: Int = DEFAULT_PARAGRAPH_SPACING_DP,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
) {
    /** Line height follows the font size so Myanmar script stacks stay unclipped at any size. */
    val lineHeightSp: Int get() = (fontSizeSp * LINE_HEIGHT_RATIO).roundToInt()

    fun sanitized(): ReaderSettings = copy(
        fontSizeSp = fontSizeSp.coerceIn(FONT_SIZE_RANGE),
        marginDp = marginDp.coerceIn(MARGIN_RANGE),
        paragraphSpacingDp = paragraphSpacingDp.coerceIn(PARAGRAPH_SPACING_RANGE),
    )

    companion object {
        const val DEFAULT_FONT_SIZE_SP = 18
        const val DEFAULT_MARGIN_DP = 20
        const val DEFAULT_PARAGRAPH_SPACING_DP = 16
        /** 32sp line height at the 18sp default, the Part 1 value. */
        const val LINE_HEIGHT_RATIO = 32f / 18f

        val FONT_SIZE_RANGE = 14..30
        val MARGIN_RANGE = 8..48
        val PARAGRAPH_SPACING_RANGE = 4..40
    }
}
