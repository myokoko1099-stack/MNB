package com.mnb.browser.translation.quality

import com.mnb.browser.translation.TranslationResult
import com.mnb.browser.translation.chunking.ChapterChunk
import com.mnb.browser.translation.chunking.ChunkReassembler
import com.mnb.browser.translation.chunking.ParagraphSplitter
import com.mnb.browser.translation.memory.CharacterMemory
import com.mnb.browser.translation.memory.GlossaryMemory

/**
 * Everything the checker looks at. It needs no database and no provider: build it from plain text
 * ([fromText]), a provider outcome ([fromResult]) or translated chunks ([fromChunks]).
 *
 * The checker only reads this; neither the source nor the translation is ever changed.
 */
data class QualityInput(
    /** Source paragraphs, as [ParagraphSplitter] splits them. */
    val sourceParagraphs: List<String>,
    /**
     * Translated paragraphs. Compared with the source by position. The factories drop blank lines
     * (they are layout); pass a list directly to have a blank entry reported as an empty paragraph.
     */
    val translatedParagraphs: List<String>,
    /** The provider outcome, when there is one, so failures and mock output can be reported. */
    val result: TranslationResult? = null,
    /** Source chunks of the chapter (each may carry its translated text). Empty when chunking was not used. */
    val chunks: List<ChapterChunk> = emptyList(),
    /** Terms checked for consistent use. UNCERTAIN entries are reported but never treated as settled. */
    val glossary: List<GlossaryMemory> = emptyList(),
    val characters: List<CharacterMemory> = emptyList(),
    /** Known wrong renderings per source term, keyed by normalized source term. See [QualityVariants]. */
    val termVariants: Map<String, List<String>> = emptyMap(),
) {
    init {
        require(sourceParagraphs.any { it.isNotBlank() }) { "Source text must not be blank" }
    }

    companion object {
        fun fromText(
            sourceText: String,
            translatedText: String?,
            chunks: List<ChapterChunk> = emptyList(),
            glossary: List<GlossaryMemory> = emptyList(),
            characters: List<CharacterMemory> = emptyList(),
            termVariants: Map<String, List<String>> = emptyMap(),
        ): QualityInput = QualityInput(
            sourceParagraphs = ParagraphSplitter.split(sourceText),
            translatedParagraphs = ParagraphSplitter.split(translatedText.orEmpty()),
            result = null,
            chunks = chunks,
            glossary = glossary,
            characters = characters,
            termVariants = termVariants,
        )

        fun fromResult(
            sourceText: String,
            result: TranslationResult,
            chunks: List<ChapterChunk> = emptyList(),
            glossary: List<GlossaryMemory> = emptyList(),
            characters: List<CharacterMemory> = emptyList(),
            termVariants: Map<String, List<String>> = emptyMap(),
        ): QualityInput {
            val text = (result as? TranslationResult.Success)?.translatedText
            return QualityInput(
                sourceParagraphs = ParagraphSplitter.split(sourceText),
                translatedParagraphs = ParagraphSplitter.split(text.orEmpty()),
                result = result,
                chunks = chunks,
                glossary = glossary,
                characters = characters,
                termVariants = termVariants,
            )
        }

        /**
         * The source is the chunks' own text rejoined; the translation is the chunks' translated
         * texts joined the same way (a chunk without a translation contributes nothing and is
         * reported by the chunk checks).
         */
        fun fromChunks(
            chunks: List<ChapterChunk>,
            glossary: List<GlossaryMemory> = emptyList(),
            characters: List<CharacterMemory> = emptyList(),
            termVariants: Map<String, List<String>> = emptyMap(),
        ): QualityInput {
            require(chunks.isNotEmpty()) { "chunks must not be empty" }
            return fromText(
                sourceText = ChunkReassembler.reassemble(chunks),
                translatedText = joinTranslated(chunks),
                chunks = chunks,
                glossary = glossary,
                characters = characters,
                termVariants = termVariants,
            )
        }

        private fun joinTranslated(chunks: List<ChapterChunk>): String {
            val sb = StringBuilder()
            var previous: ChapterChunk? = null
            for (chunk in ChunkReassembler.ordered(chunks)) {
                val text = chunk.chunk.translatedText?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    if (sb.isNotEmpty() && (previous == null || previous.endParagraphIndex != chunk.startParagraphIndex)) {
                        sb.append(ParagraphSplitter.SEPARATOR)
                    }
                    sb.append(text)
                }
                previous = chunk
            }
            return sb.toString()
        }
    }
}
