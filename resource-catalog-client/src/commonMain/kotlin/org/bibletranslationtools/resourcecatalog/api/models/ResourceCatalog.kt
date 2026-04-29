package org.bibletranslationtools.resourcecatalog.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ResourceCatalog(
    val slug: String,
    val name: String,
    @SerialName("date_modified")
    val modifiedAt: String,
    @SerialName("source")
    val sourceUrl: String,
    @SerialName("chunks")
    val chunksUrl: String,
    @SerialName("usfm")
    val usfmUrl: String,
    @SerialName("notes")
    val notesUrl: String,
    @SerialName("checking_questions")
    val questionsUrl: String,
    @SerialName("terms")
    val termsUrl: String,
    @SerialName("tw_cat")
    val twCatUrl: String,
    val status: ProjectStatus
)
