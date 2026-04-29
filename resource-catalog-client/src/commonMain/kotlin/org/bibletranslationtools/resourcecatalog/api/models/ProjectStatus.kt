package org.bibletranslationtools.resourcecatalog.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bibletranslationtools.resourcecontainer.Resource

@Serializable
internal data class ProjectStatus(
    @SerialName("checking_entity")
    val checkingEntity: String?,
    @SerialName("checking_level")
    val checkingLevel: String,
    @SerialName("source_text")
    val sourceText: String?,
    @SerialName("source_text_version")
    val sourceTextVersion: String?,
    @SerialName("publish_date")
    val publishedAt: String,
    val version: String,
    val contributors: String,
    val comments: String?
)

internal fun ProjectStatus.toRcStatus(
    translateMode: String,
    sourceTranslations: List<Resource.SourceTranslation> = emptyList()
) = Resource.Status(
    translateMode = translateMode,
    checkingLevel = checkingLevel,
    version = version,
    license = "",
    pubDate = publishedAt,
    sourceTranslations = sourceTranslations
)