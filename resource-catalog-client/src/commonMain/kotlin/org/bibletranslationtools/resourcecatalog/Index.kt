package org.bibletranslationtools.resourcecatalog

import org.bibletranslationtools.resourcecatalog.models.Catalog
import org.bibletranslationtools.resourcecatalog.models.Category
import org.bibletranslationtools.resourcecatalog.models.CategoryEntry
import org.bibletranslationtools.resourcecatalog.models.ChunkMarker
import org.bibletranslationtools.resourcecatalog.models.SourceLanguage
import org.bibletranslationtools.resourcecatalog.models.TargetLanguage
import org.bibletranslationtools.resourcecatalog.models.Translation
import org.bibletranslationtools.resourcecatalog.models.Versification
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource

interface Index {

    fun addTempTargetLanguage(language: TargetLanguage): Boolean

    fun listSourceLanguagesLastModified(): List<Map<String, Int>>

    fun listProjectsLastModified(languageSlug: String?): Map<String, Int>

    fun getTranslation(containerSlug: String): Translation?

    fun findTranslations(
        languageSlug: String?,
        projectSlug: String?,
        resourceSlug: String?,
        resourceType: String?,
        translateMode: String?,
        minCheckingLevel: Int,
        maxCheckingLevel: Int
    ): List<Translation>

    fun getImportedTranslations(): List<Translation>

    fun getSourceLanguage(sourceLanguageSlug: String): SourceLanguage?

    fun addSourceLanguage(language: SourceLanguage): Long

    fun getSourceLanguages(): List<SourceLanguage>

    fun getSourceLanguages(projectSlug: String): List<SourceLanguage>

    fun getTargetLanguage(targetLanguageSlug: String): TargetLanguage?

    fun addTargetLanguage(language: TargetLanguage): Boolean

    fun findTargetLanguage(nameQuery: String): List<TargetLanguage>

    fun getTargetLanguages(): List<TargetLanguage>

    fun getApprovedTargetLanguage(tempTargetLanguageSlug: String): TargetLanguage?

    fun getProject(
        sourceLanguageSlug: String,
        projectSlug: String,
        enableDefaultLanguage: Boolean = false
    ): Project?

    fun getProjects(
        sourceLanguageSlug: String,
        enableDefaultLanguage: Boolean = false
    ): List<Project>

    fun addProject(project: Project, categories: List<Category>, sourceLanguageId: Long): Long

    fun getProjectCategories(
        parentCategoryId: Long,
        languageSlug: String,
        translateMode: String?
    ): List<CategoryEntry>

    fun getResource(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): Resource?

    fun addResource(resource: Resource, projectId: Long): Long

    fun getResources(sourceLanguageSlug: String?, projectSlug: String): List<Resource>

    fun getCatalog(catalogSlug: String): Catalog?

    fun addCatalog(catalog: Catalog): Long

    fun getCatalogs(): List<Catalog>

    fun getVersification(sourceLanguageSlug: String, versificationSlug: String): Versification?

    fun addVersification(versification: Versification, sourceLanguageId: Long): Long

    fun getVersifications(sourceLanguageSlug: String): List<Versification>

    fun getChunkMarkers(projectSlug: String, versificationSlug: String): List<ChunkMarker>

    fun getCategory(languageSlug: String, categorySlug: String): Category?

    fun getCategories(languageSlug: String, projectSlug: String): List<Category>
}