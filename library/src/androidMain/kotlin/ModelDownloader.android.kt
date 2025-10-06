package com.cactus

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.copyTo
import kotlin.io.outputStream
import kotlin.io.use

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun downloadAndExtractModels(tasks: List<DownloadTask>): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            for (task in tasks) {
                val modelFolderPath = File(File(applicationContext.filesDir, "models"), task.folder)
                if (modelFolderPath.exists() && (modelFolderPath.listFiles()?.isNotEmpty() == true)) {
                    Log.d("CactusDownloader", "Model folder already exists for ${task.folder}")
                    continue
                }

                    val downloadedFilePath = File(File(applicationContext.filesDir, "models"), task.filename)
                downloadedFilePath.parentFile?.mkdirs()
                
                Log.d("CactusDownloader", "Downloading file from ${task.url}")
                val urlConnection = URL(task.url).openConnection() as HttpURLConnection
                urlConnection.connect()

                if (urlConnection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Failed to download file: ${urlConnection.responseCode} from ${task.url}")
                }

                urlConnection.inputStream.use { input ->
                    downloadedFilePath.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                modelFolderPath.mkdirs()
                
                if (task.requiresExtraction) {
                    // Extract ZIP file
                    ZipInputStream(BufferedInputStream(downloadedFilePath.inputStream())).use { zipInput ->
                        var entry: ZipEntry?
                        while (zipInput.nextEntry.also { entry = it } != null) {
                            val currentEntry = entry!!
                            val extractedFilePath = File(modelFolderPath, currentEntry.name)
                            
                            if (!extractedFilePath.canonicalPath.startsWith(modelFolderPath.canonicalPath + File.separator)) {
                                throw SecurityException("Zip path traversal attempt detected.")
                            }
                            
                            if (currentEntry.isDirectory) {
                                extractedFilePath.mkdirs()
                            } else {
                                extractedFilePath.parentFile?.mkdirs()
                                extractedFilePath.outputStream().use { fileOutput ->
                                    zipInput.copyTo(fileOutput)
                                }
                            }
                            zipInput.closeEntry()
                        }
                    }
                    downloadedFilePath.delete()
                    Log.d("CactusDownloader", "ZIP extraction completed for ${task.filename}")

                    // If the extracted content is a single directory, move its contents up a level.
                    val contents = modelFolderPath.listFiles()
                    if (contents != null && contents.size == 1 && contents[0].isDirectory) {
                        val nestedDir = contents[0]
                        Log.d("CactusDownloader", "Found single nested directory ${nestedDir.name}, moving contents up.")
                        nestedDir.listFiles()?.forEach { file ->
                            file.renameTo(File(modelFolderPath, file.name))
                        }
                        nestedDir.delete()
                    }
                } else {
                    // Direct file download (e.g., .bin files) - just move to model folder
                    val targetFile = File(modelFolderPath, task.filename)
                    downloadedFilePath.renameTo(targetFile)
                    Log.d("CactusDownloader", "File download completed for ${task.filename}")
                }
            }
            true
        } catch (e: Exception) {
            Log.e("CactusDownloader", "Download and extraction failed: $e")
            // Basic cleanup
            tasks.forEach { task ->
                File(applicationContext.cacheDir, task.filename).delete()
                File(applicationContext.cacheDir, task.folder).deleteRecursively()
            }
            false
        }
    }
}

actual suspend fun modelExists(modelName: String): Boolean {
    return withContext(Dispatchers.IO) {
        val modelsDir = File(File(applicationContext.filesDir, "models"), modelName)
        modelsDir.exists() && modelsDir.isDirectory && (modelsDir.listFiles()?.isNotEmpty() == true)
    }
}
