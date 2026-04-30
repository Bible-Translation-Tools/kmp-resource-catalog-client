package org.bibletranslationtools.resourcecatalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import org.bibletranslationtools.resourcecatalog.api.Api
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiParseTest {

    private val api = Api(HttpClient(MockEngine { respond("") }))

    // ---- parseTargetLanguages --------------------------------------------------

    @Test
    fun parseTargetLanguagesReturnsMappedList() {
        val json = """[{"lc":"en","ln":"English","ld":"ltr","ang":"English","lr":"Americas","gl":true}]"""
        val result = api.parseTargetLanguages(json)
        assertEquals(1, result.size)
        val lang = result.first()
        assertEquals("en", lang.slug)
        assertEquals("English", lang.name)
        assertEquals("ltr", lang.direction)
        assertEquals("English", lang.anglicizedName)
        assertEquals("Americas", lang.region)
        assertTrue(lang.isGatewayLanguage)
    }

    @Test
    fun parseTargetLanguagesHandlesMultiple() {
        val json = """[
            {"lc":"en","ln":"English","ld":"ltr","ang":"English","lr":"Americas","gl":true},
            {"lc":"fr","ln":"French","ld":"ltr","ang":"French","lr":"Europe","gl":false},
            {"lc":"ar","ln":"Arabic","ld":"rtl","ang":"Arabic","lr":"Middle East","gl":true}
        ]"""
        val result = api.parseTargetLanguages(json)
        assertEquals(3, result.size)
        assertEquals("en", result[0].slug)
        assertEquals("fr", result[1].slug)
        assertEquals("ar", result[2].slug)
    }

    @Test
    fun parseTargetLanguagesIgnoresUnknownKeys() {
        val json = """[{"lc":"es","ln":"Spanish","ld":"ltr","ang":"Spanish","lr":"Americas","gl":false,"extra_key":"ignored"}]"""
        val result = api.parseTargetLanguages(json)
        assertEquals(1, result.size)
        assertEquals("es", result.first().slug)
    }

    @Test
    fun parseTargetLanguagesReturnsEmptyForEmptyArray() {
        val result = api.parseTargetLanguages("[]")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseTargetLanguagesFalseGatewayByDefault() {
        val json = """[{"lc":"xx","ln":"Test","ld":"ltr","ang":"Test","lr":"Region"}]"""
        val result = api.parseTargetLanguages(json)
        assertFalse(result.first().isGatewayLanguage)
    }

    @Test
    fun parseTargetLanguagesRtlDirection() {
        val json = """[{"lc":"ar","ln":"Arabic","ld":"rtl","ang":"Arabic","lr":"Middle East","gl":false}]"""
        val result = api.parseTargetLanguages(json)
        assertEquals("rtl", result.first().direction)
    }

    // ---- parseApprovedTempLanguages --------------------------------------------

    @Test
    fun parseApprovedTempLanguagesSingleEntry() {
        val json = """[{"tmp-abc":"en"}]"""
        val result = api.parseApprovedTempLanguages(json)
        assertEquals(1, result.size)
        assertEquals("tmp-abc" to "en", result.first())
    }

    @Test
    fun parseApprovedTempLanguagesMultipleEntries() {
        val json = """[{"tmp-1":"en"},{"tmp-2":"fr"},{"tmp-3":"de"}]"""
        val result = api.parseApprovedTempLanguages(json)
        assertEquals(3, result.size)
        assertEquals("tmp-1" to "en", result[0])
        assertEquals("tmp-2" to "fr", result[1])
        assertEquals("tmp-3" to "de", result[2])
    }

    @Test
    fun parseApprovedTempLanguagesMultiplePerObject() {
        val json = """[{"tmp-1":"en","tmp-2":"fr"}]"""
        val result = api.parseApprovedTempLanguages(json)
        assertEquals(2, result.size)
    }

    @Test
    fun parseApprovedTempLanguagesReturnsEmptyForEmptyArray() {
        val result = api.parseApprovedTempLanguages("[]")
        assertTrue(result.isEmpty())
    }

    // ---- getResourceContainerFormat --------------------------------------------

    @Test
    fun getResourceContainerFormatReturnsNullForEmptyList() {
        assertNull(Api.getResourceContainerFormat(emptyList()))
    }

    @Test
    fun getResourceContainerFormatReturnsNullForNonRcMimeType() {
        val format = Resource.Format("", "application/json", 0, "", false)
        assertNull(Api.getResourceContainerFormat(listOf(format)))
    }

    @Test
    fun getResourceContainerFormatFindsMatchingFormat() {
        val rcFormat = Resource.Format("", "${ResourceContainer.BASE_MIME_TYPE}+scripture", 0, "", false)
        val result = Api.getResourceContainerFormat(listOf(rcFormat))
        assertEquals(rcFormat, result)
    }

    @Test
    fun getResourceContainerFormatReturnsFirstMatch() {
        val rc1 = Resource.Format("", "${ResourceContainer.BASE_MIME_TYPE}+scripture", 0, "url1", false)
        val rc2 = Resource.Format("", "${ResourceContainer.BASE_MIME_TYPE}+notes", 0, "url2", false)
        val result = Api.getResourceContainerFormat(listOf(rc1, rc2))
        assertEquals(rc1, result)
    }

    @Test
    fun getResourceContainerFormatSkipsNonRcFormats() {
        val nonRc = Resource.Format("", "application/json", 0, "", false)
        val rc = Resource.Format("", "${ResourceContainer.BASE_MIME_TYPE}+scripture", 0, "", false)
        val result = Api.getResourceContainerFormat(listOf(nonRc, rc))
        assertEquals(rc, result)
    }
}
