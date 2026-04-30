package org.bibletranslationtools.resourcecatalog.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bibletranslationtools.resourcecatalog.api.models.LanguageCatalog
import org.bibletranslationtools.resourcecatalog.api.models.ProjectCatalog
import org.bibletranslationtools.resourcecatalog.api.models.ResourceCatalog
import org.bibletranslationtools.resourcecatalog.api.models.toLibraryLanguage
import org.bibletranslationtools.resourcecatalog.json
import org.bibletranslationtools.resourcecatalog.library.models.ChunkMarker
import org.bibletranslationtools.resourcecatalog.library.models.TargetLanguage
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import java.io.File
import org.bibletranslationtools.resourcecatalog.api.models.TargetLanguage as ApiLanguage

internal class Api(private val httpClient: HttpClient = defaultHttpClient()) {

    /**
     * Downloads the full source catalog starting from [url] and returns all
     * projects with their languages and resources fully populated.
     *
     * This is a pure network operation — no DB access. The result is passed
     * to [org.bibletranslationtools.resourcecatalog.ResourceCatalogClient] which writes it in a single transaction.
     */
    @Throws(Exception::class)
    suspend fun fetchSources(
        url: String,
        onProgress: (Float, String?) -> Unit = { _, _ -> }
    ): List<ProjectCatalog> {
        val projects = httpClient.get(url).body<List<ProjectCatalog>>()
        return projects.mapIndexed { index, project ->
            onProgress(index / projects.size.toFloat(), project.slug)
            project.copy(languages = fetchLanguagesForProject(project))
        }
    }

    private suspend fun fetchLanguagesForProject(project: ProjectCatalog): List<LanguageCatalog> {
        val languages = httpClient.get(project.languagesUrl).body<List<LanguageCatalog>>()
        return languages.map { language ->
            language.copy(resources = fetchResourcesForLanguage(language))
        }
    }

    private suspend fun fetchResourcesForLanguage(language: LanguageCatalog): List<ResourceCatalog> {
        println(language.resourceUrl)
        val resource: List<ResourceCatalog> = httpClient.get(language.resourceUrl).body()
        return resource
    }

    /**
     * Downloads chunk markers for all projects in [chunkUrls].
     *
     * Pure network operation — no DB access. Pass the result to
     * [org.bibletranslationtools.resourcecatalog.ResourceCatalogClient] which writes it in a single transaction.
     */
    @Throws(Exception::class)
    suspend fun fetchChunks(
        chunkUrls: Map<String, String>,
        onProgress: (Float, String?) -> Unit = { _, _ -> }
    ): Map<String, List<ChunkMarker>> {
        val result = mutableMapOf<String, List<ChunkMarker>>()
        chunkUrls.entries.forEachIndexed { index, (slug, url) ->
            onProgress((index + 1) / chunkUrls.size.toFloat(), "chunk_markers")
            val chunks = httpClient.get(url).body<List<MarkerChunk>>()
            result[slug] = chunks.map { ChunkMarker(it.chapter, it.firstVerse) }
        }
        return result
    }

    /**
     * Downloads and parses a raw catalog response from [url].
     */
    @Throws(Exception::class)
    suspend fun fetchCatalog(url: String): String = httpClient.get(url).body()

    /**
     * Parses target languages from a raw langnames or temp-langnames response.
     */
    fun parseTargetLanguages(data: String): List<TargetLanguage> =
        json.decodeFromString<List<ApiLanguage>>(data).map { it.toLibraryLanguage() }

    /**
     * Parses approved temp language mappings from a raw approved-temp-langnames
     * response. Returns (tempSlug to approvedSlug) pairs.
     */
    fun parseApprovedTempLanguages(data: String): List<Pair<String, String>> =
        json.decodeFromString<List<Map<String, String>>>(data)
            .flatMap { entry -> entry.map { (k, v) -> k to v } }

    /**
     * Downloads a resource container archive to [destFile].
     */
    @Throws(Exception::class)
    suspend fun downloadResourceContainer(url: String, destFile: File) {
        if (url.isEmpty()) throw Exception("Missing resource container url")
        destFile.parentFile?.mkdirs()
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw Exception("Download failed: ${response.status}")
            }
            val channel = response.bodyAsChannel()
            destFile.outputStream().use { output ->
                channel.copyTo(output)
            }
        }
    }

    /**
     * Downloads translation word assignments from [url].
     * Returns null if the request fails or the server returns an error.
     */
    suspend fun fetchWordAssignments(url: String): String? {
        if (url.isEmpty()) return null
        return try {
            val response = httpClient.get(url)
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        /**
         * Returns the first resource container format found in the list.
         */
        fun getResourceContainerFormat(formats: List<Resource.Format>): Resource.Format? {
            val regex = "${ResourceContainer.BASE_MIME_TYPE}\\+.+".toRegex()
            return formats.firstOrNull { it.mimeType.matches(regex) }
        }

        fun defaultHttpClient() = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest {
                headers.append("Accept", "application/json")
            }
        }
    }
}

@Serializable
private data class MarkerChunk(
    @SerialName("chp") val chapter: String,
    @SerialName("firstvs") val firstVerse: String
)