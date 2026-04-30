package org.bibletranslationtools.resourcecatalog.library.models

/**
 * Represents a global catalog
 */
data class Catalog(
    /** the catalog type */
    val type: CatalogType,
    /** the url where the catalog exists */
    val url: String,
    /** when the catalog was last modified */
    val modifiedAt: Int
)

enum class CatalogType(val slug: String) {
    TARGET_LANGUAGES("langnames"),
    LANGUAGE_QUESTIONS("new-language-questions"),
    TEMP_LANGUAGES("temp-langnames"),
    APPROVED_LANGUAGES("approved-temp-langnames");

    companion object {
        fun of(slug: String): CatalogType {
            return entries.find { it.slug == slug }
                ?: throw IllegalArgumentException("Unknown catalog type")
        }
    }
}
