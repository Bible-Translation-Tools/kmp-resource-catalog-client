package org.bibletranslationtools.resourcecatalog

import org.bibletranslationtools.resourcecatalog.library.Library
import org.bibletranslationtools.resourcecatalog.library.models.Catalog
import org.bibletranslationtools.resourcecatalog.library.models.CatalogType
import org.bibletranslationtools.resourcecatalog.library.models.Category
import org.bibletranslationtools.resourcecatalog.library.models.ChunkMarker
import org.bibletranslationtools.resourcecatalog.library.models.SourceLanguage
import org.bibletranslationtools.resourcecatalog.library.models.TargetLanguage
import org.bibletranslationtools.resourcecatalog.library.models.Versification
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryTest {

    private lateinit var library: Library

    @BeforeTest
    fun setUp() {
        library = Library(":memory:")
    }

    @AfterTest
    fun tearDown() {
        library.closeDatabase()
    }

    // ---- helpers ---------------------------------------------------------------

    private fun addLang(
        slug: String = "en",
        name: String = "English",
        dir: String = "ltr"
    ): Long = library.addSourceLanguage(SourceLanguage(slug, name, dir))

    private fun addProject(
        langId: Long,
        slug: String = "gen",
        name: String = "Genesis",
        categories: List<Category> = emptyList()
    ): Long = library.addProject(Project(slug, name, 1, "", "", ""), categories, langId)

    private fun addResource(projectId: Long, slug: String = "ulb"): Long {
        val status = Resource.Status("odbt", "3", "1.0", "", "", "")
        val resource = Resource(slug, "ULB", "book", status).apply {
            addFormat(Resource.Format("", "application/zip+scripture", 0, "", false))
        }
        return library.addResource(resource, projectId)
    }

    private fun addVersification(langId: Long, slug: String = "ufw"): Long =
        library.addVersification(Versification(slug, "Unfoldingword"), langId)

    // ---- Source Language -------------------------------------------------------

    @Test
    fun addSourceLanguageReturnsPositiveId() {
        assertTrue(addLang() > 0)
    }

    @Test
    fun addSourceLanguageUpsertReturnsSameId() {
        val id1 = addLang("en", "English", "ltr")
        val id2 = addLang("en", "English (updated)", "ltr")
        assertEquals(id1, id2)
    }

    @Test
    fun addSourceLanguageUpsertUpdatesName() {
        addLang("en", "English", "ltr")
        addLang("en", "English (updated)", "ltr")
        assertEquals("English (updated)", library.getSourceLanguage("en")?.name)
    }

    @Test
    fun addSourceLanguageThrowsForBlankSlug() {
        assertFailsWith<Exception> { addLang("", "English", "ltr") }
    }

    @Test
    fun addSourceLanguageThrowsForBlankName() {
        assertFailsWith<Exception> { addLang("en", "", "ltr") }
    }

    @Test
    fun addSourceLanguageThrowsForBlankDirection() {
        assertFailsWith<Exception> { addLang("en", "English", "") }
    }

    @Test
    fun getSourceLanguageReturnsNullForUnknownSlug() {
        assertNull(library.getSourceLanguage("xx"))
    }

    @Test
    fun getSourceLanguageReturnsExpectedFields() {
        addLang("es", "Spanish", "ltr")
        val lang = library.getSourceLanguage("es")
        assertNotNull(lang)
        assertEquals("es", lang.slug)
        assertEquals("Spanish", lang.name)
        assertEquals("ltr", lang.direction)
    }

    @Test
    fun getSourceLanguagesReturnsAll() {
        addLang("en", "English", "ltr")
        addLang("es", "Spanish", "ltr")
        assertEquals(2, library.getSourceLanguages().size)
    }

    @Test
    fun getSourceLanguagesEmptyWhenNone() {
        assertTrue(library.getSourceLanguages().isEmpty())
    }

    @Test
    fun getSourceLanguagesByProjectSlugFilters() {
        val enId = addLang("en")
        val esId = addLang("es", "Spanish", "ltr")
        addProject(enId, "gen")
        addProject(esId, "exo", "Exodus")

        val langs = library.getSourceLanguages("gen")
        assertEquals(1, langs.size)
        assertEquals("en", langs.first().slug)
    }

    // ---- Target Language -------------------------------------------------------

    @Test
    fun addTargetLanguageReturnsTrue() {
        assertTrue(library.addTargetLanguage(TargetLanguage("fr", "French", "ltr")))
    }

    @Test
    fun addTargetLanguageThrowsForBlankSlug() {
        assertFailsWith<Exception> {
            library.addTargetLanguage(TargetLanguage("", "French", "ltr"))
        }
    }

    @Test
    fun addTargetLanguageThrowsForBlankName() {
        assertFailsWith<Exception> {
            library.addTargetLanguage(TargetLanguage("fr", "", "ltr"))
        }
    }

    @Test
    fun getTargetLanguageReturnsNullForUnknownSlug() {
        assertNull(library.getTargetLanguage("xx"))
    }

    @Test
    fun getTargetLanguageReturnsExpectedFields() {
        library.addTargetLanguage(TargetLanguage("fr", "French", "ltr", "French", "Europe", true))
        val lang = library.getTargetLanguage("fr")
        assertNotNull(lang)
        assertEquals("fr", lang.slug)
        assertEquals("French", lang.name)
        assertEquals("ltr", lang.direction)
        assertEquals("French", lang.anglicizedName)
        assertEquals("Europe", lang.region)
        assertTrue(lang.isGatewayLanguage)
    }

    @Test
    fun getTargetLanguagesReturnsAll() {
        library.addTargetLanguage(TargetLanguage("fr", "French", "ltr"))
        library.addTargetLanguage(TargetLanguage("de", "German", "ltr"))
        assertEquals(2, library.getTargetLanguages().size)
    }

    @Test
    fun clearTargetLanguagesRemovesAll() {
        library.addTargetLanguage(TargetLanguage("fr", "French", "ltr"))
        library.clearTargetLanguages()
        assertTrue(library.getTargetLanguages().isEmpty())
    }

    @Test
    fun findTargetLanguageByNamePartial() {
        library.addTargetLanguage(TargetLanguage("fr", "French", "ltr"))
        library.addTargetLanguage(TargetLanguage("de", "German", "ltr"))
        val results = library.findTargetLanguage("fre")
        assertEquals(1, results.size)
        assertEquals("fr", results.first().slug)
    }

    @Test
    fun findTargetLanguagePrefixMatchSortedFirst() {
        library.addTargetLanguage(TargetLanguage("en", "English", "ltr"))
        library.addTargetLanguage(TargetLanguage("enz", "English-Z", "ltr"))
        library.addTargetLanguage(TargetLanguage("es", "Spanish", "ltr"))
        val results = library.findTargetLanguage("en")
        assertEquals("en", results.first().slug)
    }

    // ---- Temp Target Language --------------------------------------------------

    @Test
    fun addTempTargetLanguageReturnsTrue() {
        assertTrue(library.addTempTargetLanguage(TargetLanguage("tmp-1", "Temp Language", "ltr")))
    }

    @Test
    fun addTempTargetLanguageThrowsForBlankSlug() {
        assertFailsWith<Exception> {
            library.addTempTargetLanguage(TargetLanguage("", "Temp", "ltr"))
        }
    }

    @Test
    fun setApprovedTargetLanguageReturnsFalseWhenTempNotFound() {
        assertFalse(library.setApprovedTargetLanguage("nonexistent", "en"))
    }

    @Test
    fun setApprovedTargetLanguageReturnsTrueWhenTempExists() {
        library.addTempTargetLanguage(TargetLanguage("tmp-abc", "Temp ABC", "ltr"))
        assertTrue(library.setApprovedTargetLanguage("tmp-abc", "en"))
    }

    @Test
    fun setApprovedTargetLanguageThrowsForBlankSlugs() {
        assertFailsWith<Exception> { library.setApprovedTargetLanguage("", "en") }
        assertFailsWith<Exception> { library.setApprovedTargetLanguage("tmp", "") }
    }

    @Test
    fun getTargetLanguagesIncludesTempLanguages() {
        library.addTargetLanguage(TargetLanguage("fr", "French", "ltr"))
        library.addTempTargetLanguage(TargetLanguage("tmp-1", "Temp Language", "ltr"))
        assertEquals(2, library.getTargetLanguages().size)
    }

    @Test
    fun clearTempLanguagesRemovesOnlyTemp() {
        library.addTargetLanguage(TargetLanguage("fr", "French", "ltr"))
        library.addTempTargetLanguage(TargetLanguage("tmp-1", "Temp Language", "ltr"))
        library.clearTempLanguages()
        assertEquals(1, library.getTargetLanguages().size)
        assertEquals("fr", library.getTargetLanguages().first().slug)
    }

    // ---- Project ---------------------------------------------------------------

    @Test
    fun addProjectReturnsPositiveId() {
        val langId = addLang()
        assertTrue(addProject(langId) > 0)
    }

    @Test
    fun addProjectThrowsForBlankSlug() {
        val langId = addLang()
        assertFailsWith<Exception> {
            library.addProject(Project("", "Genesis", 1, "", "", ""), emptyList(), langId)
        }
    }

    @Test
    fun addProjectThrowsForBlankName() {
        val langId = addLang()
        assertFailsWith<Exception> {
            library.addProject(Project("gen", "", 1, "", "", ""), emptyList(), langId)
        }
    }

    @Test
    fun getProjectReturnsNullForUnknownProject() {
        val langId = addLang()
        assertNull(library.getProject("en", "xxx"))
    }

    @Test
    fun getProjectReturnsExpectedFields() {
        val langId = addLang()
        addProject(langId, "gen", "Genesis")
        val project = library.getProject("en", "gen")
        assertNotNull(project)
        assertEquals("gen", project.slug)
        assertEquals("Genesis", project.name)
    }

    @Test
    fun getProjectFallsBackToEnglishWhenEnabled() {
        val enId = addLang("en")
        addProject(enId, "gen", "Genesis")
        val project = library.getProject("fr", "gen", enableDefaultLanguage = true)
        assertNotNull(project)
        assertEquals("gen", project.slug)
    }

    @Test
    fun getProjectDoesNotFallBackWhenDisabled() {
        val enId = addLang("en")
        addProject(enId, "gen", "Genesis")
        assertNull(library.getProject("fr", "gen", enableDefaultLanguage = false))
    }

    @Test
    fun getProjectExistsReturnsFalseWhenMissing() {
        assertFalse(library.getProjectExists("xxx"))
    }

    @Test
    fun getProjectExistsReturnsTrueWhenPresent() {
        val langId = addLang()
        addProject(langId, "gen")
        assertTrue(library.getProjectExists("gen"))
    }

    @Test
    fun getProjectsReturnsAllForLanguage() {
        val enId = addLang("en")
        addProject(enId, "gen", "Genesis")
        addProject(enId, "exo", "Exodus")
        assertEquals(2, library.getProjects("en", enableDefaultLanguage = false).size)
    }

    @Test
    fun addProjectWithCategories() {
        val langId = addLang()
        val cat = Category("bible", "Bible")
        val projectId = library.addProject(
            Project("gen", "Genesis", 1, "", "", ""),
            listOf(cat),
            langId
        )
        assertTrue(projectId > 0)
    }

    @Test
    fun getProjectCategoriesReturnsTopLevel() {
        val langId = addLang()
        val cat = Category("bible", "Bible")
        library.addProject(Project("gen", "Genesis", 1, "", "", ""), listOf(cat), langId)
        val entries = library.getProjectCategories(0L, "en", null)
        assertTrue(entries.isNotEmpty())
    }

    @Test
    fun getCategoriesReturnsHierarchy() {
        val langId = addLang()
        val cats = listOf(Category("bible", "Bible"), Category("ot", "Old Testament"))
        library.addProject(Project("gen", "Genesis", 1, "", "", ""), cats, langId)
        val categories = library.getCategories("en", "gen")
        assertEquals(2, categories.size)
        assertEquals("bible", categories.first().slug)
        assertEquals("ot", categories.last().slug)
    }

    // ---- Resource --------------------------------------------------------------

    @Test
    fun addResourceReturnsPositiveId() {
        val langId = addLang()
        val projectId = addProject(langId)
        assertTrue(addResource(projectId) > 0)
    }

    @Test
    fun addResourceThrowsForBlankSlug() {
        val langId = addLang()
        val projectId = addProject(langId)
        assertFailsWith<Exception> {
            val status = Resource.Status("odbt", "3", "1.0", "", "", "")
            val resource = Resource("", "ULB", "book", status).apply {
                addFormat(Resource.Format("", "application/zip+scripture", 0, "", false))
            }
            library.addResource(resource, projectId)
        }
    }

    @Test
    fun addResourceThrowsWhenNoFormats() {
        val langId = addLang()
        val projectId = addProject(langId)
        assertFailsWith<Exception> {
            val status = Resource.Status("odbt", "3", "1.0", "", "", "")
            library.addResource(Resource("ulb", "ULB", "book", status), projectId)
        }
    }

    @Test
    fun getResourceReturnsNullForUnknown() {
        assertNull(library.getResource("en", "gen", "ulb"))
    }

    @Test
    fun getResourceReturnsExpectedFields() {
        val langId = addLang()
        val projectId = addProject(langId)
        addResource(projectId, "ulb")
        val resource = library.getResource("en", "gen", "ulb")
        assertNotNull(resource)
        assertEquals("ulb", resource.slug)
        assertEquals("ULB", resource.name)
        assertEquals("book", resource.type)
        assertEquals("odbt", resource.status.translateMode)
        assertEquals("3", resource.status.checkingLevel)
    }

    @Test
    fun getResourceIncludesFormat() {
        val langId = addLang()
        val projectId = addProject(langId)
        addResource(projectId, "ulb")
        val resource = library.getResource("en", "gen", "ulb")
        assertNotNull(resource)
        assertEquals(1, resource.formats.size)
        assertEquals("application/zip+scripture", resource.formats.first().mimeType)
    }

    @Test
    fun getResourcesReturnsAllForProject() {
        val langId = addLang()
        val projectId = addProject(langId)
        addResource(projectId, "ulb")
        addResource(projectId, "udb")
        assertEquals(2, library.getResources("en", "gen").size)
    }

    @Test
    fun getResourcesWithNullLanguageReturnsAll() {
        val enId = addLang("en")
        val esId = addLang("es", "Spanish", "ltr")
        val enProjectId = addProject(enId, "gen")
        val esProjectId = addProject(esId, "gen", "Genesis")
        addResource(enProjectId, "ulb")
        addResource(esProjectId, "ulb")
        assertEquals(2, library.getResources(null, "gen").size)
    }

    // ---- Versification ---------------------------------------------------------

    @Test
    fun addVersificationReturnsPositiveId() {
        val langId = addLang()
        assertTrue(addVersification(langId) > 0)
    }

    @Test
    fun addVersificationThrowsForBlankSlug() {
        val langId = addLang()
        assertFailsWith<Exception> {
            library.addVersification(Versification("", "Unfoldingword"), langId)
        }
    }

    @Test
    fun getVersificationReturnsNullForUnknown() {
        assertNull(library.getVersification("en", "xxx"))
    }

    @Test
    fun getVersificationReturnsExpectedFields() {
        val langId = addLang()
        addVersification(langId, "ufw")
        val versification = library.getVersification("en", "ufw")
        assertNotNull(versification)
        assertEquals("ufw", versification.slug)
        assertEquals("Unfoldingword", versification.name)
        assertTrue(versification.rowId > 0)
    }

    @Test
    fun getVersificationsReturnsAllForLanguage() {
        val langId = addLang()
        addVersification(langId, "ufw")
        addVersification(langId, "kjv")
        assertEquals(2, library.getVersifications("en").size)
    }

    // ---- Chunk Marker ----------------------------------------------------------

    @Test
    fun addChunkMarkerReturnsPositiveId() {
        val langId = addLang()
        addProject(langId)
        val versificationId = addVersification(langId)
        val id = library.addChunkMarker(ChunkMarker("1", "1"), "gen", versificationId)
        assertTrue(id > 0)
    }

    @Test
    fun addChunkMarkerThrowsForBlankChapter() {
        val langId = addLang()
        addProject(langId)
        val versificationId = addVersification(langId)
        assertFailsWith<Exception> {
            library.addChunkMarker(ChunkMarker("", "1"), "gen", versificationId)
        }
    }

    @Test
    fun getChunkMarkersReturnsExpectedMarkers() {
        val langId = addLang()
        addProject(langId)
        val versificationId = addVersification(langId)
        library.addChunkMarker(ChunkMarker("1", "1"), "gen", versificationId)
        library.addChunkMarker(ChunkMarker("1", "6"), "gen", versificationId)
        library.addChunkMarker(ChunkMarker("2", "1"), "gen", versificationId)

        val markers = library.getChunkMarkers("gen", "ufw")
        assertEquals(3, markers.size)
    }

    @Test
    fun getChunkMarkersEmptyForUnknownProject() {
        val langId = addLang()
        addProject(langId)
        val versificationId = addVersification(langId)
        assertTrue(library.getChunkMarkers("xxx", "ufw").isEmpty())
    }

    // ---- Catalog ---------------------------------------------------------------

    @Test
    fun addCatalogReturnsPositiveId() {
        val catalog = Catalog(CatalogType.TARGET_LANGUAGES, "https://example.com/langnames.json", 1234)
        assertTrue(library.addCatalog(catalog) > 0)
    }

    @Test
    fun addCatalogThrowsForBlankUrl() {
        assertFailsWith<Exception> {
            library.addCatalog(Catalog(CatalogType.TARGET_LANGUAGES, "", 0))
        }
    }

    @Test
    fun getCatalogReturnsNullWhenMissing() {
        assertNull(library.getCatalog(CatalogType.TARGET_LANGUAGES))
    }

    @Test
    fun getCatalogReturnsExpectedFields() {
        val catalog = Catalog(CatalogType.TARGET_LANGUAGES, "https://example.com/langnames.json", 1234)
        library.addCatalog(catalog)
        val result = library.getCatalog(CatalogType.TARGET_LANGUAGES)
        assertNotNull(result)
        assertEquals(CatalogType.TARGET_LANGUAGES, result.type)
        assertEquals("https://example.com/langnames.json", result.url)
        assertEquals(1234, result.modifiedAt)
    }

    @Test
    fun addCatalogUpsertUpdatesUrl() {
        library.addCatalog(Catalog(CatalogType.TARGET_LANGUAGES, "https://example.com/old.json", 1))
        library.addCatalog(Catalog(CatalogType.TARGET_LANGUAGES, "https://example.com/new.json", 2))
        assertEquals("https://example.com/new.json", library.getCatalog(CatalogType.TARGET_LANGUAGES)?.url)
    }

    @Test
    fun getCatalogsReturnsAll() {
        library.addCatalog(Catalog(CatalogType.TARGET_LANGUAGES, "https://example.com/a.json", 1))
        library.addCatalog(Catalog(CatalogType.TEMP_LANGUAGES, "https://example.com/b.json", 2))
        assertEquals(2, library.getCatalogs().size)
    }

    // ---- findTranslations ------------------------------------------------------

    @Test
    fun findTranslationsEmptyWhenNoData() {
        assertTrue(library.findTranslations().isEmpty())
    }

    @Test
    fun findTranslationsReturnsAllWhenNoFilters() {
        val langId = addLang()
        val projectId = addProject(langId)
        addResource(projectId, "ulb")
        val translations = library.findTranslations()
        assertEquals(1, translations.size)
    }

    @Test
    fun findTranslationsFiltersByLanguageSlug() {
        val enId = addLang("en")
        val esId = addLang("es", "Spanish", "ltr")
        val enProjectId = addProject(enId, "gen")
        val esProjectId = addProject(esId, "gen", "Genesis")
        addResource(enProjectId)
        addResource(esProjectId)

        val results = library.findTranslations(languageSlug = "en")
        assertEquals(1, results.size)
        assertEquals("en", results.first().language.slug)
    }

    @Test
    fun findTranslationsFiltersByProjectSlug() {
        val langId = addLang()
        val genId = addProject(langId, "gen", "Genesis")
        val exoId = addProject(langId, "exo", "Exodus")
        addResource(genId)
        addResource(exoId)

        val results = library.findTranslations(projectSlug = "gen")
        assertEquals(1, results.size)
        assertEquals("gen", results.first().project.slug)
    }

    @Test
    fun findTranslationsFiltersByResourceSlug() {
        val langId = addLang()
        val projectId = addProject(langId)
        addResource(projectId, "ulb")
        addResource(projectId, "udb")

        val results = library.findTranslations(resourceSlug = "ulb")
        assertEquals(1, results.size)
        assertEquals("ulb", results.first().resource.slug)
    }

    @Test
    fun findTranslationsTranslationHasCorrectContainerSlug() {
        val langId = addLang("en")
        val projectId = addProject(langId, "gen")
        addResource(projectId, "ulb")

        val translation = library.findTranslations().first()
        assertEquals("en_gen_ulb", translation.resourceContainerSlug)
    }

    // ---- getTranslation --------------------------------------------------------

    @Test
    fun getTranslationReturnsNullForUnknownSlug() {
        assertNull(library.getTranslation("en_gen_ulb"))
    }

    @Test
    fun getTranslationReturnsTranslation() {
        val langId = addLang("en")
        val projectId = addProject(langId, "gen")
        addResource(projectId, "ulb")

        val translation = library.getTranslation("en_gen_ulb")
        assertNotNull(translation)
        assertEquals("en", translation.language.slug)
        assertEquals("gen", translation.project.slug)
        assertEquals("ulb", translation.resource.slug)
    }

    // ---- listModified ----------------------------------------------------------

    @Test
    fun listSourceLanguagesLastModifiedReturnsEntry() {
        val langId = addLang("en")
        val projectId = addProject(langId)
        val status = Resource.Status("odbt", "3", "1.0", "", "", "")
        val resource = Resource("ulb", "ULB", "book", status).apply {
            addFormat(Resource.Format("", "application/tsrc+ulb", 12345, "", false))
        }
        library.addResource(resource, projectId)
        val result = library.listSourceLanguagesLastModified()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun listProjectsLastModifiedReturnsEntry() {
        val langId = addLang("en")
        val projectId = addProject(langId, "gen")
        val status = Resource.Status("odbt", "3", "1.0", "", "", "")
        val resource = Resource("ulb", "ULB", "book", status).apply {
            addFormat(Resource.Format("", "application/tsrc+ulb", 67890, "", false))
        }
        library.addResource(resource, projectId)
        val result = library.listProjectsLastModified("en")
        assertTrue(result.isNotEmpty())
    }

    // ---- transaction -----------------------------------------------------------

    @Test
    fun transactionRollsBackOnException() {
        val langId = addLang()
        runCatching {
            library.transaction {
                library.addSourceLanguage(SourceLanguage("fr", "French", "ltr"))
                throw RuntimeException("rollback")
            }
        }
        assertNull(library.getSourceLanguage("fr"))
    }
}
