package org.bibletranslationtools.resourcecatalog

import app.cash.sqldelight.db.SqlDriver
import org.bibletranslationtools.resourcecatalog.models.Catalog
import org.bibletranslationtools.resourcecatalog.models.Category
import org.bibletranslationtools.resourcecatalog.models.CategoryEntry
import org.bibletranslationtools.resourcecatalog.models.ChunkMarker
import org.bibletranslationtools.resourcecatalog.models.SourceLanguage
import org.bibletranslationtools.resourcecatalog.models.TargetLanguage
import org.bibletranslationtools.resourcecatalog.models.Translation
import org.bibletranslationtools.resourcecatalog.models.Versification
import org.bibletranslationtools.resourcecatalog.models.toLanguage
import org.bibletranslationtools.resourcecontainer.ContainerTools
import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer

/**
 * Manages the indexed library content using SQLDelight.
 *
 * The driver is owned by the caller — typically constructed via
 * [createDatabaseDriver], which can copy a prepopulated database out of assets
 * on first launch and point SQLDelight at it.
 */
internal class Library(
    private val driver: SqlDriver,
    private val database: Database = Database(driver)
) {

    // Generated query handles
    private val sourceLanguageQ = database.sourceLanguageQueries
    private val targetLanguageQ = database.targetLanguageQueries
    private val tempTargetLanguageQ = database.tempTargetLanguageQueries
    private val projectQ = database.projectQueries
    private val categoryQ = database.categoryQueries
    private val categoryNameQ = database.categoryNameQueries
    private val resourceQ = database.resourceQueries
    private val resourceFormatQ = database.resourceFormatQueries
    private val legacyResourceInfoQ = database.legacyResourceInfoQueries
    private val versificationQ = database.versificationQueries
    private val versificationNameQ = database.versificationNameQueries
    private val chunkMarkerQ = database.chunkMarkerQueries
    private val catalogQ = database.catalogQueries

    // ---- lifecycle / transactions --------------------------------------------------------------

    /**
     * Run [body] inside a database transaction. Returns whatever [body] returns.
     */
    fun <T> transaction(body: () -> T): T {
        return database.transactionWithResult { body() }
    }

    fun closeDatabase() {
        driver.close()
    }

    // ---- helpers --------------------------------------------------------------------------------

    @Throws(Exception::class)
    private fun validateNotEmpty(value: String?) {
        if (value.isNullOrBlank()) throw Exception("Invalid parameter value")
    }

    private fun deNull(value: String?): String = value ?: ""

    private fun bool(value: Long): Boolean = value > 0

    private fun bool(value: Int): Boolean = value > 0

    private fun longBool(value: Boolean): Long = if (value) 1L else 0L

    // ---- writes ---------------------------------------------------------------------------------

    @Throws(Exception::class)
    fun addSourceLanguage(language: SourceLanguage): Long {
        validateNotEmpty(language.slug)
        validateNotEmpty(language.name)
        validateNotEmpty(language.direction)

        return database.transactionWithResult {
            sourceLanguageQ.upsert(
                slug = language.slug,
                name = language.name,
                direction = language.direction
            )
            sourceLanguageQ.selectIdBySlug(language.slug).executeAsOne()
        }
    }

    @Throws(Exception::class)
    fun addTargetLanguage(language: TargetLanguage): Boolean {
        validateNotEmpty(language.slug)
        validateNotEmpty(language.name)
        validateNotEmpty(language.direction)

        targetLanguageQ.upsert(
            slug = language.slug,
            name = language.name,
            anglicized_name = language.anglicizedName,
            direction = language.direction,
            region = language.region,
            is_gateway_language = longBool(language.isGatewayLanguage)
        )
        return true
    }

    @Throws(Exception::class)
    fun addTempTargetLanguage(language: TargetLanguage): Boolean {
        validateNotEmpty(language.slug)
        validateNotEmpty(language.name)
        validateNotEmpty(language.direction)

        tempTargetLanguageQ.upsert(
            slug = language.slug,
            name = language.name,
            anglicized_name = deNull(language.anglicizedName),
            direction = language.direction,
            region = deNull(language.region),
            is_gateway_language = longBool(language.isGatewayLanguage)
        )
        return true
    }

    @Throws(Exception::class)
    fun setApprovedTargetLanguage(tempTargetLanguageSlug: String, targetLanguageSlug: String): Boolean {
        validateNotEmpty(tempTargetLanguageSlug)
        validateNotEmpty(targetLanguageSlug)

        return database.transactionWithResult {
            val exists = tempTargetLanguageQ.existsBySlug(tempTargetLanguageSlug).executeAsOne()
            if (exists) {
                tempTargetLanguageQ.setApprovedSlug(
                    targetSlug = targetLanguageSlug,
                    tempSlug = tempTargetLanguageSlug
                )
                true
            } else false
        }
    }

    @Throws(Exception::class)
    fun addProject(
        project: Project,
        categories: List<Category>,
        sourceLanguageId: Long
    ): Long {
        validateNotEmpty(project.slug)
        validateNotEmpty(project.name)

        return database.transactionWithResult {
            var parentCategoryId = 0L
            categories.forEach { category ->
                validateNotEmpty(category.slug)
                validateNotEmpty(category.name)

                categoryQ.insertOrIgnore(slug = category.slug, parent_id = parentCategoryId)
                val catId = categoryQ
                    .selectIdBySlugAndParent(slug = category.slug, parent_id = parentCategoryId)
                    .executeAsOneOrNull()
                    ?: throw Exception("Invalid category")
                parentCategoryId = catId

                categoryNameQ.upsert(
                    source_language_id = sourceLanguageId,
                    category_id = parentCategoryId,
                    name = category.name
                )
            }

            projectQ.upsert(
                slug = project.slug,
                name = project.name,
                desc = deNull(project.description),
                icon = deNull(project.icon),
                sort = project.sort.toLong(),
                chunks_url = deNull(project.chunksUrl),
                source_language_id = sourceLanguageId,
                category_id = parentCategoryId
            )

            projectQ.selectIdByLanguageIdAndSlug(
                projectSlug = project.slug,
                languageId = sourceLanguageId
            ).executeAsOne()
        }
    }

    @Throws(Exception::class)
    fun addVersification(versification: Versification, sourceLanguageId: Long): Long {
        validateNotEmpty(versification.slug)
        validateNotEmpty(versification.name)

        return database.transactionWithResult {
            versificationQ.insertOrIgnore(versification.slug)
            val vId = versificationQ.selectIdBySlug(versification.slug).executeAsOneOrNull()
                ?: throw Exception("Invalid versification")

            versificationNameQ.upsert(
                source_language_id = sourceLanguageId,
                versification_id = vId,
                name = versification.name
            )
            vId
        }
    }

    @Throws(Exception::class)
    fun addChunkMarker(chunk: ChunkMarker, projectSlug: String, versificationId: Long): Long {
        validateNotEmpty(chunk.chapter)
        validateNotEmpty(chunk.verse)
        validateNotEmpty(projectSlug)

        return database.transactionWithResult {
            chunkMarkerQ.insertOrIgnore(
                chapter = chunk.chapter,
                verse = chunk.verse,
                project_slug = projectSlug,
                versification_id = versificationId
            )
            chunkMarkerQ.selectIdByUniqueKey(
                projectSlug = projectSlug,
                versificationId = versificationId,
                chapter = chunk.chapter,
                verse = chunk.verse
            ).executeAsOneOrNull() ?: throw Exception("Invalid Chunk Marker")
        }
    }

    @Throws(Exception::class)
    fun addCatalog(catalog: Catalog): Long {
        validateNotEmpty(catalog.slug)
        validateNotEmpty(catalog.url)

        return database.transactionWithResult {
            catalogQ.upsert(
                slug = catalog.slug,
                url = catalog.url,
                modified_at = catalog.modifiedAt.toLong()
            )
            catalogQ.selectBySlug(catalog.slug).executeAsOne().id
        }
    }

    @Throws(Exception::class)
    fun addResource(resource: Resource, projectId: Long): Long {
        validateNotEmpty(resource.slug)
        validateNotEmpty(resource.name)
        validateNotEmpty(resource.type)
        validateNotEmpty(if (resource.formats.isNotEmpty()) "good" else null)
        validateNotEmpty(resource.status.translateMode)
        validateNotEmpty(resource.status.checkingLevel)
        validateNotEmpty(resource.status.version)

        return database.transactionWithResult {
            resourceQ.upsert(
                slug = resource.slug,
                name = resource.name,
                type = resource.type,
                translate_mode = resource.status.translateMode,
                checking_level = resource.status.checkingLevel,
                comments = deNull(resource.status.comments),
                pub_date = resource.status.pubDate,
                license = deNull(resource.status.license),
                version = resource.status.version,
                project_id = projectId
            )

            val resourceId = resourceQ
                .selectIdByProjectAndSlug(project_id = projectId, slug = resource.slug)
                .executeAsOne()

            resource.formats.forEach { format ->
                validateNotEmpty(format.mimeType)
                resourceFormatQ.upsert(
                    package_version = format.packageVersion,
                    mime_type = format.mimeType,
                    modified_at = format.modifiedAt.toLong(),
                    imported = longBool(format.imported),
                    url = deNull(format.url),
                    resource_id = resourceId
                )
            }

            val legacyUrl = resource.legacyData[Api.LEGACY_WORDS_ASSIGNMENTS_URL] as? String
            if (!legacyUrl.isNullOrEmpty()) {
                legacyResourceInfoQ.upsert(
                    translation_words_assignments_url = legacyUrl,
                    resource_id = resourceId
                )
            }
            resourceId
        }
    }

    // ---- reads ----------------------------------------------------------------------------------

    fun listSourceLanguagesLastModified(): List<Map<String, Int>> {
        val pattern = "${ResourceContainer.BASE_MIME_TYPE}+%"
        return resourceFormatQ.sourceLanguagesLastModified(pattern)
            .executeAsList()
            .map { row ->
                mapOf((row.slug ?: "") to ((row.modified_at ?: 0L).toInt()))
            }
    }

    fun listProjectsLastModified(languageSlug: String?): Map<String, Int> {
        val pattern = "${ResourceContainer.BASE_MIME_TYPE}+%"
        return resourceFormatQ.projectsLastModified(
            mimeTypePattern = pattern,
            languageSlug = languageSlug ?: "%"
        ).executeAsList().associate { row ->
            (row.slug ?: "") to ((row.modified_at ?: 0L).toInt())
        }
    }

    fun getProjectExists(projectSlug: String): Boolean {
        return projectQ.selectIdBySlug(projectSlug)
            .executeAsOneOrNull() != null
    }

    fun getTranslation(containerSlug: String): Translation? {
        return try {
            val slugs = ContainerTools.explodeSlug(containerSlug)
            val l = getSourceLanguage(slugs[0])
            val p = getProject(slugs[0], slugs[1], false)
            val r = getResource(slugs[0], slugs[1], slugs[2])
            if (l != null && p != null && r != null) Translation(l.toLanguage(), p, r) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun findTranslations(
        languageSlug: String?,
        projectSlug: String?,
        resourceSlug: String?,
        resourceType: String?,
        translateMode: String?,
        minCheckingLevel: Int,
        maxCheckingLevel: Int
    ): List<Translation> {
        return resourceQ.findTranslations(
            languageSlug = if (languageSlug.isNullOrEmpty()) "%" else languageSlug,
            projectSlug = if (projectSlug.isNullOrEmpty()) "%" else projectSlug,
            resourceSlug = if (resourceSlug.isNullOrEmpty()) "%" else resourceSlug,
            translateMode = if (translateMode.isNullOrEmpty()) "%" else translateMode,
            resourceType = if (resourceType.isNullOrEmpty()) "%" else resourceType,
            minCheckingLevel = minCheckingLevel.toString(),
            maxCheckingLevel = maxCheckingLevel.toLong()
        ).executeAsList().map { row -> buildTranslationFromRow(row) }
    }

    fun getImportedTranslations(): List<Translation> {
        return resourceQ.importedTranslations().executeAsList().map { row ->
            buildTranslationFromRow(row)
        }
    }

    /**
     * Both [findTranslations] and importedTranslations return rows of the same
     * shape, so SQLDelight generates compatible row classes. This helper accepts
     * the shared columns via duck-typed property access — using a small adapter
     * would be cleaner, but the two generated classes are nearly identical so
     * we just write two thin wrappers.
     */
    private fun buildTranslationFromRow(row: FindTranslations): Translation =
        buildTranslation(
            languageSlug = row.language_slug,
            languageName = row.language_name,
            direction = row.direction,
            projectSlug = row.project_slug,
            projectName = row.project_name,
            desc = row.desc,
            icon = row.icon,
            sort = row.sort,
            chunksUrl = row.chunks_url,
            resourceId = row.resource_id ?: 0L,
            resourceSlug = row.resource_slug,
            resourceName = row.resource_name,
            type = row.type,
            translateMode = row.translate_mode,
            checkingLevel = row.checking_level,
            comments = row.comments,
            pubDate = row.pub_date,
            license = row.license,
            version = row.version,
            translationWordsAssignmentsUrl = row.translation_words_assignments_url
        )

    private fun buildTranslationFromRow(row: ImportedTranslations): Translation =
        buildTranslation(
            languageSlug = row.language_slug,
            languageName = row.language_name,
            direction = row.direction,
            projectSlug = row.project_slug,
            projectName = row.project_name,
            desc = row.desc,
            icon = row.icon,
            sort = row.sort,
            chunksUrl = row.chunks_url,
            resourceId = row.resource_id ?: 0L,
            resourceSlug = row.resource_slug,
            resourceName = row.resource_name,
            type = row.type,
            translateMode = row.translate_mode,
            checkingLevel = row.checking_level,
            comments = row.comments,
            pubDate = row.pub_date,
            license = row.license,
            version = row.version,
            translationWordsAssignmentsUrl = row.translation_words_assignments_url
        )

    @Suppress("LongParameterList")
    private fun buildTranslation(
        languageSlug: String?, languageName: String?, direction: String?,
        projectSlug: String?, projectName: String?, desc: String?, icon: String?,
        sort: Long?, chunksUrl: String?,
        resourceId: Long, resourceSlug: String?, resourceName: String?, type: String?,
        translateMode: String?, checkingLevel: String?, comments: String?,
        pubDate: String?, license: String?, version: String?,
        translationWordsAssignmentsUrl: String?
    ): Translation {
        val l = Language(languageSlug ?: "", languageName ?: "", direction ?: "")
        val p = Project(
            projectSlug ?: "",
            projectName ?: "",
            sort?.toInt() ?: 0,
            icon ?: "",
            desc ?: "",
            chunksUrl ?: ""
        ).apply { this.languageSlug = languageSlug ?: "" }

        val status = Resource.Status(
            translateMode ?: "",
            checkingLevel ?: "",
            version ?: "",
            license ?: "",
            pubDate ?: "",
            comments ?: ""
        )
        val r = Resource(
            slug = resourceSlug ?: "",
            name = resourceName ?: "",
            type = type ?: "",
            status = status
        ).apply {
            addLegacyData(
                Api.LEGACY_WORDS_ASSIGNMENTS_URL,
                translationWordsAssignmentsUrl ?: ""
            )
        }
        attachFormats(r, resourceId)
        return Translation(l, p, r)
    }

    fun getSourceLanguage(sourceLanguageSlug: String): SourceLanguage? =
        sourceLanguageQ.selectBySlug(sourceLanguageSlug)
            .executeAsOneOrNull()
            ?.let { SourceLanguage(it.slug, it.name, it.direction) }

    fun getSourceLanguages(): List<SourceLanguage> =
        sourceLanguageQ.selectAllOrdered().executeAsList()
            .map { SourceLanguage(it.slug, it.name, it.direction) }

    fun getSourceLanguages(projectSlug: String): List<SourceLanguage> =
        sourceLanguageQ.selectByProjectSlug(projectSlug).executeAsList()
            .map { SourceLanguage(it.slug, it.name, it.direction) }

    fun getTargetLanguage(targetLanguageSlug: String): TargetLanguage? =
        targetLanguageQ.selectMergedBySlug(targetLanguageSlug)
                .executeAsOneOrNull()?.toTargetLanguage()

    fun findTargetLanguage(nameQuery: String): List<TargetLanguage> {
        val results = targetLanguageQ
            .findMergedByName("%${nameQuery.lowercase()}%")
            .executeAsList()
            .map { it.toTargetLanguage() }
            .toMutableList()

        val nameLower = nameQuery.lowercase()
        results.sortWith { lhs, rhs ->
            var lhId = lhs.slug
            var rhId = rhs.slug
            if (lhId.lowercase().startsWith(nameLower)) lhId = "!!$lhId"
            if (rhId.lowercase().startsWith(nameLower)) rhId = "!!$rhId"
            if (lhs.name.lowercase().startsWith(nameLower)) lhId = "!$lhId"
            if (rhs.name.lowercase().startsWith(nameLower)) rhId = "!$rhId"
            lhId.compareTo(rhId, ignoreCase = true)
        }
        return results
    }

    fun getTargetLanguages(): List<TargetLanguage> =
        targetLanguageQ.selectMergedAll().executeAsList().map { it.toTargetLanguage() }

    fun getApprovedTargetLanguage(tempTargetLanguageSlug: String): TargetLanguage? =
        targetLanguageQ.selectApprovedByTempSlug(tempTargetLanguageSlug)
            .executeAsOneOrNull()
            ?.let {
                TargetLanguage(
                    slug = it.slug,
                    name = it.name,
                    direction = it.direction,
                    anglicizedName = it.anglicized_name ?: "",
                    region = it.region,
                    isGatewayLanguage = bool(it.is_gateway_language)
                )
            }

    // The merged-select rows (from the UNION view) all share the same shape;
    // SQLDelight generates one class per distinct column-set. This extension
    // works on whichever generated row classes match.
    private fun SelectMergedBySlug.toTargetLanguage(): TargetLanguage =
        TargetLanguage(
            slug = slug,
            name = name,
            direction = direction,
            anglicizedName = anglicized_name ?: "",
            region = region,
            isGatewayLanguage = bool(is_gateway_language)
        )

    private fun SelectMergedAll.toTargetLanguage(): TargetLanguage =
        TargetLanguage(
            slug = slug,
            name = name,
            direction = direction,
            anglicizedName = anglicized_name ?: "",
            region = region,
            isGatewayLanguage = bool(is_gateway_language)
        )

    private fun FindMergedByName.toTargetLanguage(): TargetLanguage =
        TargetLanguage(
            slug = slug,
            name = name,
            direction = direction,
            anglicizedName = anglicized_name ?: "",
            region = region,
            isGatewayLanguage = bool(is_gateway_language)
        )

    fun getProject(
        sourceLanguageSlug: String,
        projectSlug: String,
        enableDefaultLanguage: Boolean
    ): Project? {
        val row = projectQ.selectByLanguageAndSlug(
            projectSlug = projectSlug,
            languageSlug = sourceLanguageSlug
        ).executeAsOneOrNull()

        var project: Project? = row?.let {
            Project(
                it.slug,
                it.name,
                it.sort.toInt(),
                it.icon ?: "",
                it.desc ?: "",
                it.chunks_url ?: ""
            ).apply { languageSlug = it.source_language_slug ?: "" }
        }

        if (project == null && enableDefaultLanguage) project = getProject("en", projectSlug, false)
        if (project == null && enableDefaultLanguage) project = getProject("%", projectSlug, false)
        return project
    }

    fun getProjects(
        sourceLanguageSlug: String,
        enableDefaultLanguage: Boolean
    ): List<Project> {
        return if (enableDefaultLanguage) {
            projectQ.selectByLanguageWithFallback(
                preferredLanguageSlug = sourceLanguageSlug,
                defaultLanguageSlug = "en"
            ).executeAsList().map { row ->
                Project(
                    row.slug,
                    row.name,
                    row.sort.toInt(),
                    row.icon ?: "",
                    row.desc ?: "",
                    row.chunks_url ?: ""
                ).apply { languageSlug = row.source_language_slug ?: "" }
            }
        } else {
            projectQ.selectByLanguageSlug(sourceLanguageSlug).executeAsList().map { row ->
                Project(
                    row.slug,
                    row.name,
                    row.sort.toInt(),
                    row.icon ?: "",
                    row.desc ?: "",
                    row.chunks_url ?: ""
                ).apply { languageSlug = sourceLanguageSlug }
            }
        }
    }

    fun getProjectCategories(
        parentCategoryId: Long,
        languageSlug: String,
        translateMode: String?
    ): List<CategoryEntry> {
        val mode = translateMode.orEmpty()
        val preferredSlugs = listOf(languageSlug, "en", "%")
        val out = mutableListOf<CategoryEntry>()

        // 1. Categories under this parent
        val categoryRows: List<Pair<Long, String>> = if (mode.isNotEmpty()) {
            projectQ.selectProjectCategoriesByMode(
                translateMode = mode,
                parentId = parentCategoryId
            ).executeAsList().map { it.id to it.slug }
        } else {
            projectQ.selectProjectCategoriesNoMode(parentCategoryId).executeAsList()
                .map { it.id to it.slug }
        }

        categoryRows.forEach { (catId, catSlug) ->
            for (slug in preferredSlugs) {
                val nameRow = categoryNameQ
                    .selectByCategoryAndLanguagePattern(languageSlug = slug, categoryId = catId)
                    .executeAsOneOrNull()
                if (nameRow != null) {
                    out.add(
                        CategoryEntry(
                            CategoryEntry.Type.CATEGORY,
                            catId,
                            catSlug,
                            nameRow.name,
                            nameRow.source_language_slug ?: "",
                            parentCategoryId
                        )
                    )
                    break
                }
            }
        }

        // 2. Projects directly under this category
        val projectRows: List<Pair<Long, String>> = if (mode.isNotEmpty()) {
            projectQ.selectProjectsInCategoryByMode(
                translateMode = mode,
                parentId = parentCategoryId
            ).executeAsList().map { it.id to it.slug }
        } else {
            projectQ.selectProjectsInCategoryNoMode(parentCategoryId).executeAsList()
                .map { it.id to it.slug }
        }

        projectRows.forEach { (projectId, projSlug) ->
            for (slug in preferredSlugs) {
                val nameRow = projectQ
                    .selectProjectNameByLanguage(languageSlug = slug, projectSlug = projSlug)
                    .executeAsOneOrNull()
                if (nameRow != null) {
                    out.add(
                        CategoryEntry(
                            CategoryEntry.Type.PROJECT,
                            projectId,
                            projSlug,
                            nameRow.name,
                            nameRow.source_language_slug ?: "",
                            parentCategoryId
                        )
                    )
                    break
                }
            }
        }
        return out
    }

    fun getResource(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): Resource? {
        val row = resourceQ.selectOneWithLegacy(
            resourceSlug = resourceSlug,
            projectSlug = projectSlug,
            languageSlug = sourceLanguageSlug
        ).executeAsOneOrNull() ?: return null

        val status = Resource.Status(
            row.translate_mode,
            row.checking_level,
            row.version,
            row.license ?: "",
            row.pub_date ?: "",
            row.comments ?: ""
        )
        return Resource(
            slug = resourceSlug,
            name = row.name,
            type = row.type,
            status = status
        ).apply {
            addLegacyData(
                Api.LEGACY_WORDS_ASSIGNMENTS_URL,
                row.translation_words_assignments_url ?: ""
            )
            attachFormats(this, row.id)
        }
    }

    fun getResources(sourceLanguageSlug: String?, projectSlug: String): List<Resource> {
        return if (!sourceLanguageSlug.isNullOrEmpty()) {
            resourceQ.selectByProjectAndLanguage(
                projectSlug = projectSlug,
                languageSlug = sourceLanguageSlug
            ).executeAsList().map { row ->
                Resource(
                    slug = row.slug,
                    name = row.name,
                    type = row.type,
                    status = Resource.Status(
                        row.translate_mode, row.checking_level, row.version,
                        row.license ?: "", row.pub_date ?: "", row.comments ?: ""
                    )
                ).apply {
                    addLegacyData(
                        Api.LEGACY_WORDS_ASSIGNMENTS_URL,
                        row.translation_words_assignments_url ?: ""
                    )
                    attachFormats(this, row.id)
                }
            }
        } else {
            resourceQ.selectByProject(projectSlug).executeAsList().map { row ->
                Resource(
                    slug = row.slug,
                    name = row.name,
                    type = row.type,
                    status = Resource.Status(
                        row.translate_mode, row.checking_level, row.version,
                        row.license ?: "", row.pub_date ?: "", row.comments ?: ""
                    )
                ).apply {
                    addLegacyData(
                        Api.LEGACY_WORDS_ASSIGNMENTS_URL,
                        row.translation_words_assignments_url ?: ""
                    )
                    attachFormats(this, row.id)
                }
            }
        }
    }

    private fun attachFormats(resource: Resource, resourceId: Long) {
        resourceFormatQ.selectByResource(resourceId).executeAsList().forEach { f ->
            resource.addFormat(
                Resource.Format(
                    f.package_version,
                    f.mime_type,
                    f.modified_at.toInt(),
                    f.url,
                    bool(f.imported)
                )
            )
        }
    }

    fun getCatalog(catalogSlug: String): Catalog? =
        catalogQ.selectBySlug(catalogSlug).executeAsOneOrNull()?.let {
            Catalog(catalogSlug, it.url, it.modified_at.toInt())
        }

    fun getCatalogs(): List<Catalog> =
        catalogQ.selectAll().executeAsList()
            .map { Catalog(it.slug, it.url, it.modified_at.toInt()) }

    fun getVersification(sourceLanguageSlug: String, versificationSlug: String): Versification? =
        versificationQ.selectByLanguageAndSlug(
            languageSlug = sourceLanguageSlug,
            versificationSlug = versificationSlug
        ).executeAsOneOrNull()?.let {
            Versification(it.slug ?: "", it.name).apply {
                rowId = it.id ?: 0L
            }
        }

    fun getVersifications(sourceLanguageSlug: String): List<Versification> =
        versificationQ.selectByLanguage(sourceLanguageSlug).executeAsList().map {
            Versification(it.slug ?: "", it.name).apply {
                rowId = it.id ?: 0L
            }
        }

    fun getChunkMarkers(projectSlug: String, versificationSlug: String): List<ChunkMarker> =
        chunkMarkerQ.selectByProjectAndVersification(
            versificationSlug = versificationSlug,
            projectSlug = projectSlug
        ).executeAsList().map { ChunkMarker(it.chapter, it.verse) }

    fun getCategory(languageSlug: String, categorySlug: String): Category? =
        categoryQ.selectByLanguageAndSlug(
            categorySlug = categorySlug,
            languageSlug = languageSlug
        ).executeAsOneOrNull()?.let { Category(it.slug, it.name ?: "") }

    private fun getParentCategory(languageSlug: String, childCategorySlug: String): Category? =
        categoryQ.selectParentByChildSlug(
            childSlug = childCategorySlug,
            languageSlug = languageSlug
        ).executeAsOneOrNull()?.let { Category(it.slug, it.name ?: "") }

    fun getCategories(languageSlug: String, projectSlug: String): List<Category> {
        val first = categoryQ.selectByProjectAndLanguage(
            projectSlug = projectSlug,
            languageSlug = languageSlug
        ).executeAsOneOrNull() ?: return emptyList()

        val out = mutableListOf(Category(first.slug, first.name ?: ""))
        var previous = first.slug
        while (true) {
            val parent = getParentCategory(languageSlug, previous) ?: break
            previous = parent.slug
            out.add(0, parent)
        }
        return out
    }

    // ---- maintenance ----------------------------------------------------------------------------

    fun clearTargetLanguages() {
        targetLanguageQ.deleteAll()
        vacuum()
    }

    fun clearTempLanguages() {
        tempTargetLanguageQ.deleteAll()
        vacuum()
    }

    fun clearApprovedTempLanguages() {
        tempTargetLanguageQ.clearApprovedSlugs()
    }

    fun vacuum() {
        try {
            driver.execute(identifier = null, sql = "VACUUM", parameters = 0, binders = null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
