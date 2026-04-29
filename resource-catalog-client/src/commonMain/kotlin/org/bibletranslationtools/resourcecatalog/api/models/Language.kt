package org.bibletranslationtools.resourcecatalog.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Language(
    val slug: String,
    val name: String,
    val direction: String,
    @SerialName("date_modified")
    val modifiedAt: Int
)