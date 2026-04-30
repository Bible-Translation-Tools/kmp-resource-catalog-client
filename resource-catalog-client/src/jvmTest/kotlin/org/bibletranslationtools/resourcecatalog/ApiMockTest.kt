package org.bibletranslationtools.resourcecatalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.bibletranslationtools.resourcecatalog.api.Api
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiMockTest {

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )

    // ---- fetchCatalog ----------------------------------------------------------

    @Test
    fun fetchCatalogReturnsBodyString() = runTest {
        val api = Api(HttpClient(MockEngine { respond("catalog-data") }))
        assertEquals("catalog-data", api.fetchCatalog("http://example.com/catalog"))
    }

    // ---- fetchWordAssignments --------------------------------------------------

    @Test
    fun fetchWordAssignmentsReturnsBodyOnSuccess() = runTest {
        val api = Api(HttpClient(MockEngine { respond("word-assignments-json") }))
        assertEquals("word-assignments-json", api.fetchWordAssignments("http://example.com/words"))
    }

    @Test
    fun fetchWordAssignmentsReturnsNullForEmptyUrl() = runTest {
        val api = Api(HttpClient(MockEngine { respond("") }))
        assertNull(api.fetchWordAssignments(""))
    }

    @Test
    fun fetchWordAssignmentsReturnsNullOnServerError() = runTest {
        val api = Api(HttpClient(MockEngine { respond("error", HttpStatusCode.InternalServerError) }))
        assertNull(api.fetchWordAssignments("http://example.com/words"))
    }

    @Test
    fun fetchWordAssignmentsReturnsNullOn404() = runTest {
        val api = Api(HttpClient(MockEngine { respond("not found", HttpStatusCode.NotFound) }))
        assertNull(api.fetchWordAssignments("http://example.com/words"))
    }

    // ---- downloadResourceContainer --------------------------------------------

    @Test
    fun downloadResourceContainerThrowsForEmptyUrl() = runTest {
        val api = Api(HttpClient(MockEngine { respond("") }))
        val dest = Files.createTempFile("rc-test", ".zip").toFile()
        try {
            assertFailsWith<Exception> { api.downloadResourceContainer("", dest) }
        } finally {
            dest.delete()
        }
    }

    @Test
    fun downloadResourceContainerThrowsOnErrorResponse() = runTest {
        val api = Api(HttpClient(MockEngine { respond("not found", HttpStatusCode.NotFound) }))
        val dest = Files.createTempFile("rc-test", ".zip").toFile()
        try {
            assertFailsWith<Exception> {
                api.downloadResourceContainer("http://example.com/rc.zip", dest)
            }
        } finally {
            dest.delete()
        }
    }

    @Test
    fun downloadResourceContainerWritesBodyToFile() = runTest {
        val content = "binary-zip-content"
        val api = Api(HttpClient(MockEngine {
            respond(
                content = ByteReadChannel(content),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/zip")
            )
        }))
        val dest = Files.createTempFile("rc-test", ".zip").toFile()
        try {
            api.downloadResourceContainer("http://example.com/rc.zip", dest)
            assertTrue(dest.exists())
            assertEquals(content, dest.readText())
        } finally {
            dest.delete()
        }
    }

    // ---- fetchChunks -----------------------------------------------------------

    @Test
    fun fetchChunksReturnsMappedChunkMarkers() = runTest {
        val chunksJson = """[{"chp":"1","firstvs":"1"},{"chp":"1","firstvs":"6"},{"chp":"2","firstvs":"1"}]"""
        val api = Api(HttpClient(MockEngine { jsonResponse(chunksJson) }) {
            install(ContentNegotiation) { json(json) }
        })

        val result = api.fetchChunks(mapOf("gen" to "http://example.com/gen-chunks"))
        val markers = result["gen"]

        assertNotNull(markers)
        assertEquals(3, markers.size)
        assertEquals("1", markers[0].chapter)
        assertEquals("1", markers[0].verse)
        assertEquals("6", markers[1].verse)
        assertEquals("2", markers[2].chapter)
    }

    @Test
    fun fetchChunksHandlesMultipleProjects() = runTest {
        val genJson = """[{"chp":"1","firstvs":"1"}]"""
        val exoJson = """[{"chp":"1","firstvs":"1"},{"chp":"2","firstvs":"1"}]"""
        val api = Api(HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.contains("gen") -> jsonResponse(genJson)
                else -> jsonResponse(exoJson)
            }
        }) {
            install(ContentNegotiation) { json(json) }
        })

        val result = api.fetchChunks(
            mapOf(
                "gen" to "http://example.com/gen",
                "exo" to "http://example.com/exo"
            )
        )

        assertEquals(1, result["gen"]?.size)
        assertEquals(2, result["exo"]?.size)
    }

    // ---- fetchSources ----------------------------------------------------------

    @Test
    fun fetchSourcesReturnsParsedProjects() = runTest {
        val projectsJson = """[{"slug":"gen","lang_catalog":"http://example.com/gen/langs","date_modified":100,"meta":["bible","ot"],"sort":"0"}]"""
        val langsJson = """[{"language":{"slug":"en","name":"English","direction":"ltr","date_modified":100},"project":{"name":"Genesis","desc":"","meta":[],"sort":"0"},"res_catalog":"http://example.com/gen/en/res"}]"""
        val resourcesJson = """[{"slug":"ulb","name":"ULB","date_modified":"100","status":{"checking_level":"3","publish_date":"2024-01-01","version":"1","contributors":"","comments":"","checking_entity":null,"source_text":null,"source_text_version":null}}]"""

        val api = Api(HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.contains("/langs") -> jsonResponse(langsJson)
                request.url.encodedPath.contains("/res") -> jsonResponse(resourcesJson)
                else -> jsonResponse(projectsJson)
            }
        }) {
            install(ContentNegotiation) { json(json) }
        })

        val projects = api.fetchSources("http://example.com/catalog")

        assertEquals(1, projects.size)
        assertEquals("gen", projects.first().slug)
        assertEquals(1, projects.first().languages.size)
        assertEquals("en", projects.first().languages.first().language.slug)
        assertEquals(1, projects.first().languages.first().resources.size)
    }
}
