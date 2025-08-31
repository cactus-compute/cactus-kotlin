package com.cactus

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.io.copyTo
import kotlin.io.outputStream
import kotlin.io.use
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.IOException

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun downloadAndExtractModel(url: String, filename: String, folder: String): Boolean {
    return withContext(Dispatchers.IO) {
        val appDocDir = applicationContext.cacheDir
        
        // Create a folder for the extracted model weights
        val modelFolderPath = File(appDocDir, folder)
        
        // Check if the model folder already exists and contains files
        if (modelFolderPath.exists()) {
            val files = modelFolderPath.listFiles()
            if (files != null && files.isNotEmpty()) {
                Log.d("CactusLM", "Model folder already exists at ${modelFolderPath.absolutePath} with ${files.size} files")
                return@withContext true
            }
        }
        
        // Download the ZIP file to temporary location
        val zipFilePath = File(appDocDir, filename)
        
        try {
            Log.d("CactusLM", "Downloading ZIP file from $url")
            val urlConnection = URL(url).openConnection() as HttpURLConnection
            urlConnection.connect()

            if (urlConnection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Failed to download ZIP file: ${urlConnection.responseCode}")
            }

            // Stream the response directly to a file to avoid memory issues
            var totalBytes = 0
            urlConnection.inputStream.use { input ->
                zipFilePath.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        // Log progress every 10MB
                        if (totalBytes % (10 * 1024 * 1024) == 0) {
                            Log.d("CactusLM", "Downloaded ${totalBytes / (1024 * 1024)} MB...")
                        }
                    }
                }
            }
            
            Log.d("CactusLM", "Downloaded $totalBytes bytes to ${zipFilePath.absolutePath}")
            
            // Create the model folder if it doesn't exist
            modelFolderPath.mkdirs()
            
            // Extract the ZIP file
            Log.d("CactusLM", "Extracting ZIP file...")
            ZipInputStream(BufferedInputStream(zipFilePath.inputStream())).use { zipInput ->
                var entry: ZipEntry?
                while (zipInput.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry!!
                    val extractedFilePath = File(modelFolderPath, currentEntry.name)
                    
                    // Security check for path traversal
                    if (!extractedFilePath.canonicalPath.startsWith(modelFolderPath.canonicalPath + File.separator)) {
                        throw SecurityException("Zip path traversal attempt detected.")
                    }
                    
                    if (currentEntry.isDirectory) {
                        extractedFilePath.mkdirs()
                    } else {
                        // Create subdirectories if they don't exist
                        extractedFilePath.parentFile?.mkdirs()
                        
                        // Write the file content
                        extractedFilePath.outputStream().use { fileOutput ->
                            zipInput.copyTo(fileOutput)
                        }
                    }
                    zipInput.closeEntry()
                }
            }
            
            // Clean up the temporary ZIP file
            zipFilePath.delete()
            Log.d("CactusLM", "ZIP extraction completed successfully to ${modelFolderPath.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("CactusLM", "Download and extraction failed: $e")
            // Clean up partial files on failure
            try {
                if (zipFilePath.exists()) {
                    zipFilePath.delete()
                }
                if (modelFolderPath.exists()) {
                    modelFolderPath.deleteRecursively()
                }
            } catch (_: Exception) {}
            false
        }
    }
}

actual fun getModelPath(modelFolder: String): String {
    val appDocDir = applicationContext.cacheDir
    return File(appDocDir, modelFolder).absolutePath
}
