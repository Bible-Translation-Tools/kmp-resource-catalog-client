package org.bibletranslationtools.resourcecatalog.api.models

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