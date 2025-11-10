package utils

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import utils.CactusLogger

@OptIn(ExperimentalForeignApi::class)
object IOSFileUtils {
    fun ensureFilePresentOrDownloadedAndUnzipped(
        urlString: String,
        fileName: String,
        baseDir: String,
        extractedDirName: String = fileName.removeSuffix(".zip")
    ): Boolean {
        val fm = NSFileManager.Companion.defaultManager

        if (fileName.endsWith(".zip", ignoreCase = true)) {
            val path = "$baseDir/$fileName"
            if (fm.fileExistsAtPath(path)) return true

            if (!downloadFile(urlString, path)) return false

            val targetDir = "$baseDir/$extractedDirName"
            val unzipOk = extractZip(zipFilePath = path.toPath(), outputDir = targetDir.toPath())
            if (unzipOk) {
                runCatching { fm.removeItemAtPath(path, null) }
            }
            return unzipOk
        } else {
            val targetDir = "$baseDir/$extractedDirName"
            createDirectoryIfNeeded(targetDir)
            val path = "$targetDir/$fileName"
            
            if (fm.fileExistsAtPath(path)) return true
            
            return downloadFile(urlString, path)
        }
    }

    private fun downloadFile(urlString: String, filePath: String): Boolean {
        return try {
            val nsUrl = NSURL(string = urlString)
            val data: NSData = NSData.Companion.dataWithContentsOfURL(nsUrl) ?: return false
            data.writeToFile(filePath, true)
        } catch (e: Exception) {
            CactusLogger.e("IOSFileUtils", "Error downloading file from $urlString to $filePath", throwable = e)
            false
        }
    }

    private fun extractZip(zipFilePath: Path, outputDir: Path): Boolean {
        return try {
            val zipFileSystem = FileSystem.Companion.SYSTEM.openZip(zipFilePath)
            val fileSystem = FileSystem.Companion.SYSTEM
            
            fileSystem.createDirectories(outputDir)
            
            val paths = zipFileSystem.listRecursively("/".toPath())
                .filter { zipFileSystem.metadata(it).isRegularFile }
                .toList()

            paths.forEach { zipEntryPath ->
                zipFileSystem.source(zipEntryPath).buffer().use { source ->
                    val fullPath = zipEntryPath.toString().trimStart('/')

                    // Strip the first directory level if it exists (removes the top-level folder from zip)
                    val relativeFilePath = if (fullPath.contains('/')) {
                        fullPath.substringAfter('/')
                    } else {
                        fullPath
                    }

                    val fileToWrite = outputDir.resolve(relativeFilePath)
                    fileToWrite.createParentDirectories()
                    fileSystem.sink(fileToWrite).buffer().use { sink ->
                        val bytes = sink.writeAll(source)
                        CactusLogger.d("IOSFileUtils", "Unzipped: $relativeFilePath to $fileToWrite; $bytes bytes written")
                    }
                }
            }

            val extractedFiles = fileSystem.listRecursively(outputDir).toList()
            CactusLogger.d("IOSFileUtils", "Extraction completed. Found ${extractedFiles.size} total items in $outputDir")

            true
        } catch (e: Exception) {
            CactusLogger.e("IOSFileUtils", "Error unzipping $zipFilePath to $outputDir", throwable = e)
            e.printStackTrace()
            false
        }
    }

    private fun Path.createParentDirectories() {
        this.parent?.let { parent ->
            FileSystem.Companion.SYSTEM.createDirectories(parent)
        }
    }

    fun getModelsDirectory(): String? {
        val documentsDirectory = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String
        return documentsDirectory?.let { "$it/models" }
    }

    fun createDirectoryIfNeeded(path: String): Boolean {
        return NSFileManager.Companion.defaultManager.createDirectoryAtPath(
            path, true, null, null
        )
    }

    fun fileExists(path: String): Boolean {
        return NSFileManager.Companion.defaultManager.fileExistsAtPath(path)
    }
}