package org.bibletranslationtools.resourcecatalog.api.models

import kotlinx.serialization.Serializable

@Serializable
internal data class Project(
    val name: String,
    val desc: String,
    val meta: List<String>,
    val sort: String
)