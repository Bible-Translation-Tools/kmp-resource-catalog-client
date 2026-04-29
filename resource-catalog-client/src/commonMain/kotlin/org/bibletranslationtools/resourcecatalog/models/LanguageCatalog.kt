package org.bibletranslationtools.resourcecatalog.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LanguageCatalog(
    val language: Language,
    val project: Project,
    @SerialName("res_catalog")
    val resourceUrl: String,
    val resources: List<ResourceCatalog> = emptyList()
)

@Serializable
internal data class Language(
    val slug: String,
    val name: String,
    val direction: String,
    @SerialName("date_modified")
    val modifiedAt: Int
)
