package org.bibletranslationtools.resourcecatalog.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProjectCatalog(
    val slug: String,
    @SerialName("lang_catalog")
    val languagesUrl: String,
    @SerialName("date_modified")
    val modifiedAt: Int,
    val meta: List<String>,
    val sort: String,
    val languages: List<LanguageCatalog> = emptyList()
)

@Serializable
internal data class Project(
    val name: String,
    val desc: String,
    val meta: List<String>,
    val sort: String
)