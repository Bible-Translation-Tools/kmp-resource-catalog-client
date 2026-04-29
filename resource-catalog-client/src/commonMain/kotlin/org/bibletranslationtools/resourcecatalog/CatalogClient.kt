package org.bibletranslationtools.resourcecatalog

import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecatalog.api.Api
import org.bibletranslationtools.resourcecatalog.api.models.LanguageCatalog
import org.bibletranslationtools.resourcecatalog.api.models.ProjectCatalog
import org.bibletranslationtools.resourcecatalog.api.models.ResourceCatalog
import org.bibletranslationtools.resourcecatalog.api.models.toRcStatus
import org.bibletranslationtools.resourcecatalog.library.Index
import org.bibletranslationtools.resourcecatalog.library.Library
import org.bibletranslationtools.resourcecatalog.library.models.Catalog
import org.bibletranslationtools.resourcecatalog.library.models.Category
import org.bibletranslationtools.resourcecatalog.library.models.SourceLanguage
import org.bibletranslationtools.resourcecatalog.library.models.Versification
import org.bibletranslationtools.resourcecatalog.library.models.toRcLanguage
import org.bibletranslationtools.resourcecontainer.ContainerTools
import org.bibletranslationtools.resourcecontainer.PackageInfo
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.bibletranslationtools.resourcecontainer.errors.InvalidRCException
import org.bibletranslationtools.resourcecontainer.errors.MissingRCException
import java.io.File
import org.bibletranslationtools.resourcecontainer.Resource as RcResource

class CatalogClient(
    private val databasePath: String,
    private val resourceDir: File,
    httpClient: HttpClient = Api.defaultHttpClient()
) {

    private val api = Api(httpClient)
    lateinit var library: Index
        private set

    private val sourceCatalogs = mutableListOf<Catalog>()

    // Single-threaded dispatcher for all database operations. Guarantees
    // serial access and prevents connection starvation across coroutines.
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Separate dispatcher for network I/O so downloads never hold a
    // database transaction open.
    private val networkDispatcher = Dispatchers.IO

    fun open() {
        library = Library(databasePath)
    }

    /**
     * Closes the database connection. The client must not be used after this.
     */
    fun close() = library.closeDatabase()

    fun setSourceCatalogs(catalogs: List<Catalog>) {
        sourceCatalogs.clear()
        sourceCatalogs.addAll(catalogs)
    }

    suspend fun updateSources(
        url: String,
        onProgress: (Float, String?) -> Unit = { _, _ -> }
    ) {
        val projects = withContext(networkDispatcher) {
            api.fetchSources(url, onProgress)
        }
        withTransaction {
            indexSources(projects)
        }
    }

    private fun indexSources(projects: List<ProjectCatalog>) {
        for (projectCatalog in projects) {
            for (languageCatalog in projectCatalog.languages) {
                val lang = languageCatalog.language
                val languageId = library.addSourceLanguage(
                    SourceLanguage(lang.slug, lang.name, lang.direction)
                )
                library.addVersification(Versification("en-US", "American English"), languageId)

                val categories = projectCatalog.meta.zip(languageCatalog.project.meta)
                    .map { (slug, name) -> Category(slug, name) }

                val project = Project(
                    slug = projectCatalog.slug,
                    name = languageCatalog.project.name,
                    sort = projectCatalog.sort.toInt(),
                    description = languageCatalog.project.desc,
                    chunksUrl = languageCatalog.resources.firstOrNull()?.chunksUrl ?: ""
                )
                val projectId = library.addProject(project, categories, languageId)

                for (rc in languageCatalog.resources) {
                    indexResource(projectCatalog, languageCatalog, rc, projectId, languageId)
                }
            }
        }
    }

    private fun indexResource(
        projectCatalog: ProjectCatalog,
        languageCatalog: LanguageCatalog,
        rc: ResourceCatalog,
        projectId: Long,
        languageId: Long
    ) {
        val translateMode = when (rc.slug.lowercase()) {
            "obs", "ulb" -> "all"
            else -> "gl"
        }

        val mainResource = RcResource(
            slug = rc.slug,
            name = rc.name,
            type = "book",
            status = rc.status.toRcStatus(translateMode)
        ).apply {
            addLegacyData(Index.LEGACY_WORDS_ASSIGNMENTS_URL, rc.twCatUrl)
            addFormat(RcResource.Format(
                ResourceContainer.VERSION,
                ContainerTools.typeToMime("book"),
                rc.modifiedAt.toInt(),
                rc.sourceUrl,
                false
            ))
        }
        library.addResource(mainResource, projectId)

        if (rc.notesUrl.isNotEmpty()) {
            val tnResource = RcResource(
                slug = "tn",
                name = "translationNotes",
                type = "help",
                status = rc.status.toRcStatus(
                    "gl", listOf(
                        RcResource.SourceTranslation(
                            languageCatalog.language.slug,
                            "tn",
                            mainResource.status.version
                        )
                    )
                )
            ).apply {
                addFormat(RcResource.Format(
                    ResourceContainer.VERSION,
                    ContainerTools.typeToMime("help"),
                    rc.modifiedAt.toInt(),
                    rc.notesUrl,
                    false
                ))
            }
            library.addResource(tnResource, projectId)
        }

        if (rc.questionsUrl.isNotEmpty()) {
            val tqResource = RcResource(
                slug = "tq",
                name = "translationQuestions",
                type = "help",
                status = rc.status.toRcStatus(
                    "gl", listOf(
                        RcResource.SourceTranslation(
                            languageCatalog.language.slug,
                            "tq",
                            mainResource.status.version
                        )
                    )
                )
            ).apply {
                addFormat(RcResource.Format(
                    ResourceContainer.VERSION,
                    ContainerTools.typeToMime("help"),
                    rc.modifiedAt.toInt(),
                    rc.questionsUrl,
                    false
                ))
            }
            library.addResource(tqResource, projectId)
        }

        if (rc.termsUrl.isNotEmpty()) {
            val isObs = projectCatalog.slug == "obs"
            val twProjectSlug = if (isObs) "bible-obs" else "bible"
            val twProjectName = "translationWords" + if (isObs) " OBS" else ""
            val twProjectId = library.addProject(
                Project(twProjectSlug, twProjectName, 100),
                emptyList(),
                languageId
            )
            val twResource = RcResource(
                slug = "tw",
                name = "translationWords",
                type = "dict",
                status = rc.status.toRcStatus("gl", listOf(
                    RcResource.SourceTranslation(languageCatalog.language.slug, "tw", mainResource.status.version)
                ))
            ).apply {
                addFormat(RcResource.Format(
                    ResourceContainer.VERSION,
                    ContainerTools.typeToMime("dict"),
                    rc.modifiedAt.toInt(),
                    rc.termsUrl,
                    false
                ))
            }
            library.addResource(twResource, twProjectId)
        }
    }

    /**
     * Downloads and indexes chunk markers for all projects that have a chunks URL.
     *
     * Network and DB phases are fully separated:
     * 1. Collect chunk URLs from the DB.
     * 2. Download all chunk data over the network.
     * 3. Write everything in a single transaction.
     */
    @Throws(Exception::class)
    suspend fun updateChunks(onProgress: (Float, String?) -> Unit = { _, _ -> }) {
        val (chunkUrls, versificationRowId) = withContext(dbDispatcher) {
            val urls = library.getSourceLanguages()
                .flatMap { library.getProjects(it.slug) }
                .filter { it.chunksUrl.isNotEmpty() }
                .associate { it.slug to it.chunksUrl }
            val rowId = library.getVersification("en", "en-US")?.rowId
            urls to rowId
        }

        if (versificationRowId == null) {
            Logger.w(this::javaClass.name, "Unknown versification while downloading chunks")
            return
        }

        val chunks = withContext(networkDispatcher) {
            api.fetchChunks(chunkUrls, onProgress)
        }

        withTransaction {
            for ((slug, markers) in chunks) {
                for (marker in markers) {
                    library.addChunkMarker(marker, slug, versificationRowId)
                }
            }
        }
    }

    /**
     * Updates all global catalogs (languages, temp languages, approvals).
     *
     * @param force if true, re-injects global catalog URLs into the DB first
     */
    @Throws(Exception::class)
    suspend fun updateCatalogs(
        force: Boolean,
        onProgress: (Float, String?) -> Unit = { _, _ -> }
    ) {
        if (force) {
            withContext(dbDispatcher) {
                sourceCatalogs.forEach { library.addCatalog(it) }
            }
        }
        val catalogs = withContext(dbDispatcher) { library.getCatalogs() }
        for (catalog in catalogs) {
            updateCatalog(catalog.slug, catalog.url, onProgress)
        }
    }

    @Throws(Exception::class)
    private suspend fun updateCatalog(
        slug: String,
        url: String,
        onProgress: (Float, String?) -> Unit
    ) {
        val data = withContext(networkDispatcher) { api.fetchCatalog(url) }

        when (slug) {
            "langnames" -> {
                val languages = api.parseTargetLanguages(data)
                library.clearTargetLanguages()

                withTransaction {
                    languages.forEachIndexed { i, language ->
                        if (!library.addTargetLanguage(language)) {
                            Logger.w(this::javaClass.name, "Failed to add target language: ${language.slug}")
                        }
                        onProgress(i / languages.size.toFloat(), slug)
                    }
                }
            }
            "new-language-questions" -> { /* not implemented */ }
            "temp-langnames" -> {
                val languages = api.parseTargetLanguages(data)
                library.clearTempLanguages()

                withTransaction {
                    languages.forEachIndexed { i, language ->
                        if (!library.addTempTargetLanguage(language)) {
                            Logger.w(this::javaClass.name, "Failed to add temp language: ${language.slug}")
                        }
                        onProgress((i + 1) / languages.size.toFloat(), slug)
                    }
                }
            }
            "approved-temp-langnames" -> {
                val approvals = api.parseApprovedTempLanguages(data)
                library.clearApprovedTempLanguages()
                withTransaction {
                    approvals.forEachIndexed { i, (tempSlug, approvedSlug) ->
                        if (!library.setApprovedTargetLanguage(tempSlug, approvedSlug)) {
                            Logger.w(this::javaClass.name, "Failed to approve temp language: $tempSlug as $approvedSlug")
                        }
                        onProgress((i + 1) / approvals.size.toFloat(), slug)
                    }
                }
            }
            else -> throw Exception("Catalog '$slug' is not supported")
        }
    }

    /**
     * Downloads a resource container and converts it from the legacy format.
     */
    @Throws(Exception::class)
    suspend fun downloadResourceContainer(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer {
        // Read everything from DB first
        val (format, containerSlug, packageInfo, legacyUrl) = withContext(dbDispatcher) {
            val resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
                ?: throw Exception("Unknown resource: ${sourceLanguageSlug}_${projectSlug}_$resourceSlug")
            val fmt = Api.getResourceContainerFormat(resource.formats)
                ?: throw Exception("Missing resource container format")
            val slug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
            val language = library.getSourceLanguage(sourceLanguageSlug)
                ?: throw Exception("Missing language: $sourceLanguageSlug")
            val project = library.getProject(sourceLanguageSlug, projectSlug)
                ?: throw Exception("Missing project: $projectSlug")
            val categories = library.getCategories(sourceLanguageSlug, projectSlug)
            val mimeType = if (project.slug != "obs" && resource.type == "book") {
                "text/usx"
            } else "text/markdown"
            val info = PackageInfo(
                packageVersion = ResourceContainer.VERSION,
                modifiedAt = fmt.modifiedAt,
                contentMimeType = mimeType,
                language = language.toRcLanguage(),
                project = project.copy(categories = categories.map { it.slug }),
                resource = resource
            )
            val url = resource.legacyData[Index.LEGACY_WORDS_ASSIGNMENTS_URL] as? String ?: ""
            Quadruple(fmt, slug, info, url)
        }

        val destFile = File(resourceDir, "$containerSlug.${ResourceContainer.FILE_EXTENSION}")
        val containerDir = File(resourceDir, containerSlug)
        FileUtil.deleteQuietly(destFile)
        FileUtil.deleteQuietly(containerDir)

        // All network calls together, outside any transaction
        val (rawData, wordAssignments) = withContext(networkDispatcher) {
            api.downloadResourceContainer(format.url, destFile)
            val content = FileUtil.readFileToString(destFile)
            FileUtil.deleteQuietly(destFile)

            val assignments = try {
                api.fetchWordAssignments(legacyUrl)
                    ?.let { ContainerTools.decodeWordAssignments(it) }
            } catch (e: Exception) {
                Logger.w(this::javaClass.name, e.message ?: e.toString())
                null
            }
            content to assignments
        }

        val content = ContainerTools.decodeContent(
            rawData,
            packageInfo.resource.type,
            packageInfo.resource.slug
        )

        return ContainerTools.convertResource(
            content,
            packageInfo,
            wordAssignments,
            containerDir
        )
    }

    /**
    * Copies a valid resource container into the resource directory and indexes it.
    * The container must be open (uncompressed). Existing containers are overwritten.
    */
    @Throws(Exception::class)
    suspend fun importResourceContainer(directory: File): ResourceContainer {
        val rc = ResourceContainer.load(directory)

        withContext(dbDispatcher) {
            if (!library.getProjectExists(rc.project.slug)) {
                throw InvalidRCException("Unsupported project")
            }
        }

        deleteResourceContainer(rc.slug)
        FileUtil.copyDirectory(
            directory,
            File(resourceDir, rc.slug),
            null
        )

        withTransaction {
            val languageId = library.addSourceLanguage(SourceLanguage(rc.language))
            val categories = buildList {
                try {
                    rc.info.project.categories.forEach { slug ->
                        val name = library.getCategory(
                            rc.language.slug,
                            slug
                        )?.name ?: slug
                        add(Category(slug, name))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val projectId = library.addProject(rc.project, categories, languageId)
            rc.resource.addFormat(
                RcResource.Format(
                    packageVersion = rc.info.packageVersion,
                    mimeType = rc.resource.type,
                    modifiedAt = rc.modifiedAt,
                    url = "",
                    imported = true
                )
            )
            library.addResource(rc.resource, projectId)
        }

        return openResourceContainer(
            rc.language.slug,
            rc.project.slug,
            rc.resource.slug
        )
    }

    /**
     * Exports a closed resource container to [destFile].
     */
    @Throws(Exception::class)
    fun exportResourceContainer(
        destFile: File,
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ) {
        val slug = ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug)
        val srcDir = File(resourceDir, slug)
        val srcFile = File("$srcDir.${ResourceContainer.FILE_EXTENSION}")
        if (!srcFile.exists() && srcDir.isDirectory) ResourceContainer.close(srcDir)
        if (!srcFile.exists()) throw MissingRCException("Resource container not found at $srcFile")
        FileUtil.copyFile(srcFile, destFile)
    }

    /** Opens a resource container archive so its contents can be read. */
    @Throws(Exception::class)
    fun openResourceContainer(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer {
        library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
            ?: throw Exception("Unknown resource")
        return openResourceContainer(
            ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
        )
    }

    /**
     * Opens a resource container archive by slug without validating against
     * the index.
     */
    @Throws(Exception::class)
    fun openResourceContainer(containerSlug: String): ResourceContainer {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.FILE_EXTENSION}")
        return try {
            if (directory.exists() && directory.isDirectory) {
                ResourceContainer.load(directory)
            }
            else ResourceContainer.open(archive, directory)
        } catch (_: Exception) {
            ResourceContainer.open(archive, directory)
        }
    }

    /** Closes a resource container archive. */
    @Throws(Exception::class)
    fun closeResourceContainer(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): File {
        library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
            ?: throw Exception("Unknown resource")

        val rcSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)

        return ResourceContainer.close(File(resourceDir, rcSlug))
    }

    /** Returns when a resource container was last modified, or -1 if unknown. */
    fun getResourceContainerLastModified(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): Int {
        val resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
            ?: return -1
        return Api.getResourceContainerFormat(resource.formats)?.modifiedAt ?: -1
    }

    /** Returns true if the resource container exists on disk. */
    fun resourceContainerExists(containerSlug: String): Boolean {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.FILE_EXTENSION}")
        return (directory.exists() && directory.isDirectory) || (archive.exists() && archive.isFile)
    }

    fun resourceContainerExists(
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): Boolean = resourceContainerExists(
        ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug)
    )

    /** Deletes a resource container from disk. */
    fun deleteResourceContainer(containerSlug: String) {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.FILE_EXTENSION}")
        if (directory.exists() && directory.isDirectory) FileUtil.deleteQuietly(directory)
        if (archive.exists() && archive.isFile) FileUtil.deleteQuietly(archive)
    }

    /**
     * Runs [block] inside a database transaction on [dbDispatcher].
     * Commits on success, rolls back on any exception.
     */
    private suspend fun <T> withTransaction(block: () -> T): T =
        withContext(dbDispatcher) {
            library.transaction { block() }
        }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)