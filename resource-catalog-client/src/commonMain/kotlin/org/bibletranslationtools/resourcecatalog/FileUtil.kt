package org.bibletranslationtools.resourcecatalog

import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel

internal object FileUtil {

    /**
     * Returns the contents of a file as a string
     */
    @Throws(IOException::class)
    fun readFileToString(file: File): String {
        return file.readText(Charsets.UTF_8)
    }

    /**
     * Recursively deletes a directory or just deletes the file
     */
    fun deleteQuietly(fileOrDirectory: File?): Boolean {
        if (fileOrDirectory == null) return true

        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                if (!deleteQuietly(child)) {
                    return false
                }
            }
        }
        return try {
            if (fileOrDirectory.exists()) fileOrDirectory.delete() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Throws(IOException::class)
    fun copyDirectory(srcDir: File, destDir: File, filter: FileFilter?) {
        require(srcDir.exists()) { "Source '$srcDir' does not exist" }
        require(srcDir.isDirectory) { "Source '$srcDir' exists but is not a directory" }
        require(srcDir.canonicalPath != destDir.canonicalPath) { "Source '$srcDir' and destination '$destDir' are the same" }

        var exclusionList: MutableList<String>? = null
        if (destDir.canonicalPath.startsWith(srcDir.canonicalPath)) {
            val srcFiles = filter?.let { srcDir.listFiles(it) } ?: srcDir.listFiles()
            if (!srcFiles.isNullOrEmpty()) {
                exclusionList = ArrayList(srcFiles.size)
                for (srcFile in srcFiles) {
                    val copiedFile = File(destDir, srcFile.name)
                    exclusionList.add(copiedFile.canonicalPath)
                }
            }
        }
        doCopyDirectory(srcDir, destDir, filter, exclusionList)
    }

    @Throws(IOException::class)
    private fun doCopyDirectory(srcDir: File, destDir: File, filter: FileFilter?, exclusionList: List<String>?) {
        val srcFiles = filter?.let { srcDir.listFiles(it) } ?: srcDir.listFiles()
        ?: throw IOException("Failed to list contents of $srcDir")

        if (destDir.exists()) {
            if (!destDir.isDirectory) {
                throw IOException("Destination '$destDir' exists but is not a directory")
            }
        } else if (!destDir.mkdirs() && !destDir.isDirectory) {
            throw IOException("Destination '$destDir' directory cannot be created")
        }

        if (!destDir.canWrite()) {
            throw IOException("Destination '$destDir' cannot be written to")
        } else {
            for (srcFile in srcFiles) {
                val dstFile = File(destDir, srcFile.name)
                if (exclusionList == null || !exclusionList.contains(srcFile.canonicalPath)) {
                    if (srcFile.isDirectory) {
                        doCopyDirectory(srcFile, dstFile, filter, exclusionList)
                    } else {
                        doCopyFile(srcFile, dstFile)
                    }
                }
            }
            // preserve date
            destDir.setLastModified(srcDir.lastModified())
        }
    }

    /**
     * Copies a file or directory
     */
    @Throws(IOException::class)
    fun copyFile(srcFile: File, destFile: File) {
        require(srcFile.exists()) { "Source '$srcFile' does not exist" }
        require(!srcFile.isDirectory) { "Source '$srcFile' exists but is a directory" }
        require(srcFile.canonicalPath != destFile.canonicalPath) { "Source '$srcFile' and destination '$destFile' are the same" }

        val parentFile = destFile.parentFile
        if (parentFile != null && !parentFile.mkdirs() && !parentFile.isDirectory) {
            throw IOException("Destination '$parentFile' directory cannot be created")
        } else if (destFile.exists() && !destFile.canWrite()) {
            throw IOException("Destination '$destFile' exists but is read-only")
        } else {
            doCopyFile(srcFile, destFile)
        }
    }

    @Throws(IOException::class)
    private fun doCopyFile(srcFile: File, destFile: File) {
        if (destFile.exists() && destFile.isDirectory) {
            throw IOException("Destination '$destFile' exists but is a directory")
        }

        FileInputStream(srcFile).use { fis ->
            FileOutputStream(destFile).use { fos ->
                val input: FileChannel = fis.channel
                val output: FileChannel = fos.channel
                val size = input.size()
                var pos = 0L

                while (pos < size) {
                    var count = size - pos
                    if (count > 31457280L) count = 31457280L
                    pos += output.transferFrom(input, pos, count)
                }
            }
        }

        if (srcFile.length() != destFile.length()) {
            throw IOException("Failed to copy full contents from '$srcFile' to '$destFile'")
        } else {
            // preserve date
            destFile.setLastModified(srcFile.lastModified())
        }
    }
}