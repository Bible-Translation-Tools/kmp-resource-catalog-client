package org.bibletranslationtools.resourcecatalog.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bibletranslationtools.resourcecatalog.library.models.TargetLanguage as LibraryLanguage

@Serializable
internal data class TargetLanguage(
    @SerialName("lc")
    val slug: String,
    @SerialName("ln")
    val name: String,
    @SerialName("ld")
    val direction: String,
    @SerialName("ang")
    val anglicized: String,
    @SerialName("lr")
    val region: String,
    @SerialName("gl")
    val isGateway: Boolean = false
)

internal fun TargetLanguage.toLibraryLanguage() = LibraryLanguage(
    slug = slug,
    name = name,
    direction = direction,
    anglicizedName = anglicized,
    region = region,
    isGatewayLanguage = isGateway
)