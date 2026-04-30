package org.bibletranslationtools.resourcecatalog.library

import org.bibletranslationtools.resourcecatalog.library.models.Catalog
import org.bibletranslationtools.resourcecatalog.library.models.CatalogType
import org.bibletranslationtools.resourcecatalog.library.models.Category
import org.bibletranslationtools.resourcecatalog.library.models.CategoryEntry
import org.bibletranslationtools.resourcecatalog.library.models.ChunkMarker
import org.bibletranslationtools.resourcecatalog.library.models.SourceLanguage
import org.bibletranslationtools.resourcecatalog.library.models.TargetLanguage
import org.bibletranslationtools.resourcecatalog.library.models.Translation
import org.bibletranslationtools.resourcecatalog.library.models.Versification
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource

interface Index {

    companion object {
        const val LEGACY_WORDS_ASSIGNMENTS_URL = "words_assignments_url"
    }

    fun closeDatabase()

    /**
     * Returns a source language.
     *
     * @param slug
     * @return the language object or null if it does not exist
     */
    fun getSourceLanguage(slug: String): SourceLanguage?

    /**
     * Returns a list of every source language.
     *
     * @return an array of source languages
     */
    fun getSourceLanguages(): List<SourceLanguage>

    /**
     * Returns a list of source languages in which the project exists.
     *
     * @return an array of source languages
     */
    fun getSourceLanguages(projectSlug: String): List<SourceLanguage>

    /**
     * Returns a list of source languages and when they were last modified.
     * The value is taken from the max modified resource format date within the language
     *
     * @return {slug, modified_at}
     */
    fun listSourceLanguagesLastModified(): List<Map<String, Int>>

    /**
     * Inserts or updates a source language in the library.
     *
     * @param language
     * @return the id of the source language row
     */
    fun addSourceLanguage(language: SourceLanguage): Long

    /**
     * Returns a target language.
     * The result may be a temp target language.
     *
     * Note: does not include the row id. You don't need it
     *
     * @param slug
     * @return the language object or null if it does not exist
     */
    fun getTargetLanguage(slug: String): TargetLanguage?

    /**
     * Returns a list of every target language.
     * The result may include temp target languages.
     *
     * Note: does not include the row id. You don't need it.
     * And we are pulling from two tables so it would be confusing.
     *
     * @return
     */
    fun getTargetLanguages(): List<TargetLanguage>

    /**
     * Searches for a target language by name.
     * @param query
     * @return
     */
    fun findTargetLanguage(query: String): List<TargetLanguage>

    /**
     * Returns the target language that has been assigned to a temporary target language.
     *
     * Note: does not include the row id. You don't need it
     *
     * @param tempTargetLanguageSlug the temporary target language with the assignment
     * @return the language object or null if it does not exist
     */
    fun getApprovedTargetLanguage(
        tempTargetLanguageSlug: String
    ): TargetLanguage?

    /**
     * Inserts or updates a target language in the library.
     * Note: the result is boolean since you don't need the row id. See getTargetLanguages for more information
     *
     * @param language
     * @return
     */
    fun addTargetLanguage(language: TargetLanguage): Boolean

    /**
     * Deletes all target languages
     */
    fun clearTargetLanguages()

    /**
     * Inserts or updates a temporary target language in the library.
     *
     * Note: the result is boolean since you don't need the row id. See getTargetLanguages for more information
     *
     * @param language
     * @return
     */
    fun addTempTargetLanguage(
        language: TargetLanguage
    ): Boolean

    /**
     * Maps approved target language to temporary language
     *
     * @param tempTargetLanguageSlug temporary target language slug
     * @param targetLanguageSlug approved target language slug
     */
    fun setApprovedTargetLanguage(
        tempTargetLanguageSlug: String,
        targetLanguageSlug: String
    ): Boolean

    /**
     * Deletes all temporary languages
     */
    fun clearTempLanguages()

    /**
     * Unassociates all approved languages from temporary languages
     */
    fun clearApprovedTempLanguages()

    /**
     * Returns a project with the option of falling back to a default language if not found
     *
     * @param languageSlug the source language code for which the project will be returned
     * @param projectSlug the project code
     * @param enableDefaultLanguage allows this method to use the default language if no project is found in this language
     * @return the project object or null
     */
    fun getProject(
        languageSlug: String,
        projectSlug: String,
        enableDefaultLanguage: Boolean = false
    ): Project?

    /**
     * Returns a list of projects in the given language or (if enabled) a default language.
     * The affect is a list of all unique projects with preference given to the specified language
     *
     * @param languageSlug the source language code for which projects will be returned
     * @param enableDefaultLanguage if true the default language will be used to fetch the remaining projects
     * @return an array of projects that are available in the source language
     */
    fun getProjects(
        languageSlug: String,
        enableDefaultLanguage: Boolean = true
    ): List<Project>

    /**
     * Check if a project exists by this slug
     *
     * @param projectSlug project slug
     */
    fun getProjectExists(projectSlug: String): Boolean

    /**
     * Returns an array of categories that exist underneath the parent category.
     * The results of this method are a combination of categories and projects.
     *
     * @param parentCategoryId the category whose children will be returned. If 0 then all top level categories will be returned.
     * @param languageSlug the language in which the category titles will be displayed
     * @param translateMode limit the results to just those with the given translate mode.
     * @return
     */
    fun getProjectCategories(
        parentCategoryId: Long,
        languageSlug: String,
        translateMode: String?
    ): List<CategoryEntry>

    /**
     * Returns a list of projects and when they were last modified
     * The value is taken from the max modified resource format date within the project
     *
     * @param languageSlug the source language whose projects will be selected.
     * If left empty the results will include all projects in all languages.
     * @return
     */
    fun listProjectsLastModified(
        languageSlug: String?
    ): Map<String, Int>

    /**
     * Inserts or updates a project in the library
     *
     * @param project
     * @param categories this is the category branch that the project will attach to
     * @param languageId the parent source language row id
     * @return the id of the project row
     */
    fun addProject(
        project: Project,
        categories: List<Category>,
        languageId: Long
    ): Long

    /**
     * Returns a resource
     *
     * @param languageSlug the source language slug
     * @param projectSlug the project slug
     * @param resourceSlug the resource slug
     * @return the Resource object or null if it does not exist
     */
    fun getResource(
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): Resource?

    /**
     * Returns a list of resources available in the given project
     *
     * @param languageSlug the source language of the resource. If null then all resources of the project will be returned.
     * @param projectSlug the project whose resources will be returned
     * @return
     */
    fun getResources(
        languageSlug: String?,
        projectSlug: String
    ): List<Resource>

    /**
     * Inserts or updates a resource in the library.
     *
     * @param resource the resource being indexed
     * @param projectId the parent project row id
     * @return the id of the resource row
     */
    fun addResource(
        resource: Resource,
        projectId: Long
    ): Long

    /**
     * Returns a translation that matches the resource container slug
     *
     * @param containerSlug
     * @return
     */
    fun getTranslation(containerSlug: String): Translation?

    /**
     * Returns a list of translations available for the project
     *
     * @param languageSlug the language these translations are available in. Leave null for all.
     * @param projectSlug the project for whom these translations are available. Leave null for all
     * @param resourceSlug the resource for whom these translations are available. Leave null for all
     * @param resourceType the resource type allowed for returned translations. Leave null for all.
     * @param translateMode limit the results to just those with the given translate mode. Leave null for all
     * @param minCheckingLevel the minimum checking level allowed for returned translations. Use 0 for no minimum
     * @param maxCheckingLevel the maximum checking level allowed for returned translations. Use -1 for no maximum
     * @return a list of matching translations
     */
    fun findTranslations(
        languageSlug: String? = null,
        projectSlug: String? = null,
        resourceSlug: String? = null,
        resourceType: String? = null,
        translateMode: String? = null,
        minCheckingLevel: Int = 0,
        maxCheckingLevel: Int = -1
    ): List<Translation>

    /**
     * Returns a list of translations that have been manually imported by the user.
     *
     * @return a list of translations
     */
    fun getImportedTranslations(): List<Translation>

    /**
     * Returns a catalog
     *
     * @param type Catalog type
     * @return the catalog object or null if it does not exist
     */
    fun getCatalog(type: CatalogType): Catalog?

    /**
     * Returns a list of catalogs
     * @return
     */
    fun getCatalogs(): List<Catalog>

    /**
     * Inserts or updates a catalog in the library.
     *
     * @param catalog
     * @return the id of the catalog
     */
    fun addCatalog(catalog: Catalog): Long

    /**
     * Returns the category with it's localized title.
     * This will return null if there is no matching localized category.
     * This does not necessarily mean the category does not exist.
     *
     * @param languageSlug the language slug in which the category title will be given
     * @param categorySlug the category slug
     * @return the category or null
     */
    fun getCategory(
        languageSlug: String,
        categorySlug: String
    ): Category?

    /**
     * Returns a list of categories in a project
     *
     * @param languageSlug the language in which the category title will be given
     * @param projectSlug the project slug
     * @return a list of categories in the project
     */
    fun getCategories(
        languageSlug: String,
        projectSlug: String
    ): List<Category>

    /**
     * Returns a list of chunk markers for a project
     *
     * @param projectSlug
     * @param versificationSlug
     * @return
     */
    fun getChunkMarkers(
        projectSlug: String,
        versificationSlug: String
    ): List<ChunkMarker>

    fun addChunkMarker(
        chunk: ChunkMarker,
        projectSlug: String,
        versificationId: Long
    ): Long

    /**
     * Returns a versification
     *
     * @param languageSlug the source language code for which the versification will be returned
     * @param versificationSlug
     * @return versification or null
     */
    fun getVersification(
        languageSlug: String,
        versificationSlug: String
    ): Versification?

    /**
     * Returns a list of versifications
     *
     * @param languageSlug the source language code for which versifications will be returned
     * @return
     */
    fun getVersifications(
        languageSlug: String
    ): List<Versification>

    /**
     * Inserts or updates a versification in the library.
     *
     * @param versification
     * @param languageId the parent source language row id
     * @return the id of the versification or -1
     */
    fun addVersification(
        versification: Versification,
        languageId: Long
    ): Long

    /**
     * Compacts the database file by reclaiming space left by deleted rows.
     * This operation rewrites the entire database, so it may take a while
     * on large databases and blocks all other database access during that time.
     */
    fun vacuum()

    fun <T> transaction(body: () -> T): T
}