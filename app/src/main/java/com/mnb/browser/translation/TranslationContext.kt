package com.mnb.browser.translation

data class CharacterInfo(
    val name: String,
    val translatedName: String? = null,
    val description: String = "",
    val speechStyle: String = "",
) {
    init {
        require(name.isNotBlank()) { "Character name must not be blank" }
        require(translatedName == null || translatedName.isNotBlank()) {
            "translatedName must be null or not blank"
        }
    }
}

data class GlossaryEntry(val sourceTerm: String, val translatedTerm: String) {
    init {
        require(sourceTerm.isNotBlank()) { "sourceTerm must not be blank" }
        require(translatedTerm.isNotBlank()) { "translatedTerm must not be blank" }
    }
}

/** What a translator should remember about a novel. Filled in by the later Context Memory phase. */
data class TranslationContext(
    val novelId: NovelId,
    val characters: List<CharacterInfo> = emptyList(),
    val glossary: List<GlossaryEntry> = emptyList(),
    val storyContext: String = "",
    val instructions: String = "",
) {
    init {
        val terms = glossary.map { it.sourceTerm }
        require(terms.size == terms.toSet().size) { "Glossary has a duplicate sourceTerm" }
    }

    val isEmpty: Boolean
        get() = characters.isEmpty() && glossary.isEmpty() && storyContext.isBlank() && instructions.isBlank()

    fun findGlossary(term: String): GlossaryEntry? = glossary.firstOrNull { it.sourceTerm == term }
}
