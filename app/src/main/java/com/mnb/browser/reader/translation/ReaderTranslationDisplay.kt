package com.mnb.browser.reader.translation

/** Which text the reader shows for the open chapter. */
enum class ReaderViewMode { ORIGINAL, TRANSLATION }

/**
 * What the reader's translation controls show for one [ReaderTranslationState] (Phase 04-I10-B).
 * Pure data and rules, no Compose, so every state can be unit tested. It is built from the state
 * alone: it can only offer translated text for [ReaderTranslationState.Completed], so a missing,
 * waiting, failed, unavailable or placeholder (mock) translation is never shown as a translation.
 */
data class ReaderTranslationDisplay(
    /** False when there is nothing to say about translation (no state, or original text only). */
    val showControls: Boolean,
    /** Short status line, or null when the state needs none. */
    val message: String?,
    /** Optional second line with the reason. */
    val detail: String? = null,
    /** True while a progress bar belongs under the message. */
    val showProgress: Boolean = false,
    /** 0..1 for [showProgress]; null means the amount of work is not known (indeterminate). */
    val progress: Float? = null,
    /** The translated chapter split into paragraphs; empty unless the state is Completed. */
    val translatedParagraphs: List<String> = emptyList(),
) {
    val canShowTranslation: Boolean get() = translatedParagraphs.isNotEmpty()

    /** The original is always readable; the translation only when it exists and was asked for. */
    fun effectiveMode(requested: ReaderViewMode): ReaderViewMode =
        if (requested == ReaderViewMode.TRANSLATION && canShowTranslation) ReaderViewMode.TRANSLATION
        else ReaderViewMode.ORIGINAL

    companion object {
        const val PENDING_MESSAGE = "ဘာသာပြန်ရန် စောင့်ဆိုင်းနေသည်"
        const val PROCESSING_MESSAGE = "ဘာသာပြန်နေသည်"
        const val FAILED_MESSAGE = "ဘာသာပြန်ခြင်း မအောင်မြင်ပါ"
        const val UNAVAILABLE_MESSAGE = "ဘာသာပြန်မရနိုင်ပါ"

        private val LINE_BREAK = Regex("\\r?\\n")

        fun of(state: ReaderTranslationState?): ReaderTranslationDisplay = when (state) {
            null, ReaderTranslationState.OriginalOnly -> ReaderTranslationDisplay(showControls = false, message = null)

            is ReaderTranslationState.Pending -> ReaderTranslationDisplay(
                showControls = true,
                message = PENDING_MESSAGE,
                detail = state.queuePosition?.let { "တန်းစီအမှတ် $it" },
            )

            is ReaderTranslationState.Processing -> ReaderTranslationDisplay(
                showControls = true,
                message = if (state.totalChunks > 0) {
                    "$PROCESSING_MESSAGE (${state.completedChunks}/${state.totalChunks})"
                } else {
                    PROCESSING_MESSAGE
                },
                showProgress = true,
                progress = state.fraction,
            )

            is ReaderTranslationState.Completed -> ReaderTranslationDisplay(
                showControls = true,
                message = null,
                translatedParagraphs = state.translatedText.split(LINE_BREAK).map { it.trim() }.filter { it.isNotEmpty() },
            )

            is ReaderTranslationState.Failed -> ReaderTranslationDisplay(
                showControls = true,
                message = FAILED_MESSAGE,
                detail = failureDetail(state),
            )

            is ReaderTranslationState.Unavailable -> ReaderTranslationDisplay(
                showControls = true,
                message = UNAVAILABLE_MESSAGE,
                detail = when (state.reason) {
                    ReaderTranslationUnavailableReason.NO_CHAPTER_IDENTITY -> "ဤ Chapter ကို မှတ်သားမထားသေးပါ"
                    ReaderTranslationUnavailableReason.NOT_CONFIGURED -> "ဘာသာပြန်စနစ်ကို ချိတ်ဆက်မထားသေးပါ"
                    ReaderTranslationUnavailableReason.MOCK_ONLY -> "စမ်းသပ်ဘာသာပြန်သာ ရှိသောကြောင့် မပြပါ"
                },
            )
        }

        private fun failureDetail(state: ReaderTranslationState.Failed): String {
            val reason = when (state.reason) {
                ReaderTranslationFailure.PROVIDER -> "ဘာသာပြန်ဝန်ဆောင်မှု အဆင်မပြေပါ"
                ReaderTranslationFailure.INVALID_CONTENT -> "ဤ Chapter ၏ စာသားကို ဘာသာမပြန်နိုင်ပါ"
                ReaderTranslationFailure.UNSUPPORTED_LANGUAGE -> "ဤဘာသာစကားကို မပံ့ပိုးပါ"
                ReaderTranslationFailure.STORAGE -> "သိမ်းထားသော ဘာသာပြန်ကို ဖတ်မရပါ"
                ReaderTranslationFailure.UNKNOWN -> "အကြောင်းရင်း မသိရပါ"
            }
            return if (state.canRetry) "$reason · နောက်မှ ထပ်ကြိုးစားနိုင်ပါသည်" else reason
        }
    }
}
