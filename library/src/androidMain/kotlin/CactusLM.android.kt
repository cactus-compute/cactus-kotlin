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

private var currentHandle: Long? = null

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun downloadModel(url: String, filename: String): Boolean {
    return withContext(Dispatchers.IO) {
        val modelsDir = File(applicationContext.cacheDir, "models")
        
        val modelFolderName = filename.removeSuffix(".zip")
        val modelFolder = File(modelsDir, modelFolderName)

        if (modelFolder.exists() && modelFolder.listFiles()?.isNotEmpty() == true) {
            Log.d("CactusLM", "Model folder '$modelFolderName' already exists. Skipping download.")
            return@withContext true
        }

        val zipFile = File(modelsDir, filename)

        try {
            modelsDir.mkdirs()
            Log.d("CactusLM", "Downloading model from: $url")
            val urlConnection = URL(url).openConnection() as HttpURLConnection
            urlConnection.connect()
            if (urlConnection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP ${urlConnection.responseCode} ${urlConnection.responseMessage}")
            }

            urlConnection.inputStream.use { input ->
                zipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("CactusLM", "Download complete. Saved to ${zipFile.path}")
            Log.d("CactusLM", "Extracting ${zipFile.name}...")
            modelFolder.mkdirs()

            ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zipInput ->
                var entry: ZipEntry?
                while (zipInput.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry!!
                    val outputFile = File(modelFolder, currentEntry.name)

                    if (!outputFile.canonicalPath.startsWith(modelFolder.canonicalPath + File.separator)) {
                        throw SecurityException("Zip path traversal attempt detected.")
                    }
                    
                    if (currentEntry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        outputFile.outputStream().use { fileOutput ->
                            zipInput.copyTo(fileOutput)
                        }
                    }
                    zipInput.closeEntry()
                }
            }
            Log.d("CactusLM", "Extraction to '${modelFolder.path}' successful.")
            true
        } catch (e: Exception) {
            Log.e("CactusLM", "Failed to download and extract model", e)
            if (modelFolder.exists()) {
                modelFolder.deleteRecursively()
            }
            false
        } finally {
            if (zipFile.exists()) {
                zipFile.delete()
                Log.d("CactusLM", "Cleaned up temporary file: ${zipFile.name}")
            }
        }
    }
}

actual suspend fun initializeModel(modelFolderName: String, contextSize: UInt): Long? {
    return withContext(Dispatchers.Default) {
        try {
            Log.d("CactusLM", "Initializing model from folder: $modelFolderName")

            val modelsDir = File(applicationContext.cacheDir, "models")
            val modelFolder = File(modelsDir, modelFolderName)

            if (!modelFolder.exists() || !modelFolder.isDirectory) {
                Log.e("CactusLM", "Model folder not found: ${modelFolder.absolutePath}")
                return@withContext null
            }

            Log.d("CactusLM", "Model folder found: ${modelFolder.absolutePath}")
            Log.d("CactusLM", "Initializing context...")

            val handle = CactusContext.initContext(modelFolder.absolutePath, contextSize)

            if (handle != null) {
                currentHandle = handle
                Log.d("CactusLM", "Model loaded successfully with handle: $handle")
                handle
            } else {
                Log.e("CactusLM", "Failed to initialize context from path: ${modelFolder.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e("CactusLM", "Exception while initializing model: ${e.message}", e)
            null
        }
    }
}

actual suspend fun generateCompletion(
    handle: Long,
    messages: List<ChatMessage>,
    options: CactusCompletionParams
): CactusCompletionResult? {
    return withContext(Dispatchers.Default) {
        try {
            Log.d("CactusLM", "Generating  completion")
            val result = CactusContext.completion(handle, messages, options)
            if (result.success) {
                Log.d("CactusLM", " completion successful")
                result
            } else {
                Log.e("CactusLM", " completion failed: ${result.response}")
                null
            }
        } catch (e: Exception) {
            Log.e("CactusLM", "Exception in  completion: ${e.message}", e)
            null
        }
    }
}

actual fun uninitializeModel(handle: Long) {
    try {
        CactusContext.freeContext(handle)
        if (currentHandle == handle) {
            currentHandle = null
        }
        Log.d("CactusLM", " Model unloaded")
    } catch (e: Exception) {
        Log.e("CactusLM", "Error unloading  model: ${e.message}")
    }
}
