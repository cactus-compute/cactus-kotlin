package com.cactus

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import okio.Path.Companion.toPath
import okio.Path
import okio.FileSystem
import okio.buffer
import okio.use
import okio.openZip

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadSTTModel(
    model: String,
    modelName: String,
    spkModel: String,
    spkModelName: String
): Boolean = withContext(Dispatchers.Default) {
    try {
        // Prefer Caches directory to mirror Android's cacheDir
        val cachesDir = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return@withContext false

        val modelsDir = "$cachesDir/models/vosk"
        NSFileManager.defaultManager.createDirectoryAtPath(
            modelsDir, true, null, null
        )

        // Download main model
        val modelOk = ensureFilePresentOrDownloadedAndUnzipped(
            urlString = model,
            fileName = modelName,
            baseDir = modelsDir
        )
        if (!modelOk) return@withContext false

        // Download speaker model
        val spkOk = ensureFilePresentOrDownloadedAndUnzipped(
            urlString = spkModel,
            fileName = spkModelName,
            baseDir = modelsDir
        )
        if (!spkOk) return@withContext false

        true
    } catch (_: Throwable) {
        false
    }
}

actual suspend fun initializeSTT(modelFolder: String, spkModelFolder: String): Boolean {
    return try {
        val initialized = initializeSpeechRecognition(modelFolder, spkModelFolder)
        if (initialized) {
            println("iOS Speech recognition initialized successfully")
        } else {
            println("Failed to initialize iOS speech recognition")
        }
        initialized
    } catch (e: Exception) {
        println("Error initializing iOS speech recognition: $e")
        false
    }
}

actual suspend fun performSTT(params: SpeechRecognitionParams): SpeechRecognitionResult? {
    return try {
        println("iOS performSTT called with language=$params")

        if (!isSpeechRecognitionAuthorized()) {
            println("Requesting speech permissions...")
            val permissionGranted = requestSpeechPermissions()
            if (!permissionGranted) {
                println("Speech recognition permission not granted")
                return null
            }
            println("Speech recognition permission granted")
        }

        if (!isSpeechRecognitionAvailable()) {
            println("Vosk model not loaded on this device")
            return SpeechRecognitionResult(
                success = false
            )
        }

        println("Creating speech recognition params (on-device mode)...")

        println("Calling performSpeechRecognition...")
        val speechResult = performSpeechRecognition(params)

        println("performSpeechRecognition returned: $speechResult")

        if (speechResult == null) {
            println("❌ speechResult is null")
            return null
        }

        speechResult
    } catch (e: Exception) {
        println("STT error: $e")
        e.printStackTrace()
        null
    }
}

actual fun stopSTT() {
    stopSpeechRecognition()
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun checkModelsDownloaded(modelName: String, spkModelName: String): Boolean = withContext(Dispatchers.Default) {
    try {
        // Use the same directory as download function
        val cachesDir = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return@withContext false

        val modelsDir = "$cachesDir/models/vosk"
        val fm = NSFileManager.defaultManager

        val modelPath = "$modelsDir/$modelName"
        val spkModelPath = "$modelsDir/$spkModelName"

        val modelExists = fm.fileExistsAtPath(modelPath)
        val spkModelExists = fm.fileExistsAtPath(spkModelPath)

        println("Checking models - Main model ($modelName): $modelExists, Speaker model ($spkModelName): $spkModelExists")
        
        modelExists && spkModelExists
    } catch (e: Exception) {
        println("Error checking downloaded models: $e")
        false
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ensureFilePresentOrDownloadedAndUnzipped(
    urlString: String,
    fileName: String,
    baseDir: String
): Boolean {
    val fm = NSFileManager.defaultManager

    val path = "$baseDir/$fileName"
    if (fm.fileExistsAtPath(path)) return true

    if (fileName.endsWith(".zip", ignoreCase = true)) {
        val unzippedGuess = path.removeSuffix(".zip")
        if (fm.fileExistsAtPath(unzippedGuess)) return true
    }

    val nsUrl = NSURL(string = urlString)
    val data: NSData = NSData.dataWithContentsOfURL(nsUrl) ?: return false
    data.writeToFile(path, true)

    if (fileName.endsWith(".zip", ignoreCase = true)) {
        val targetDir = baseDir
        val unzipOk = extractZip(zipFilePath = path.toPath(), outputDir = targetDir.toPath())
        // If unzip succeeded, delete the archive to match Android behavior
        if (unzipOk) {
            runCatching { fm.removeItemAtPath(path, null) }
        }
        return unzipOk
    }

    return true
}

fun extractZip(zipFilePath: Path, outputDir: Path) : Boolean {
    return try {
        val zipFileSystem = FileSystem.SYSTEM.openZip(zipFilePath)
        val fileSystem = FileSystem.SYSTEM
        val paths = zipFileSystem.listRecursively("/".toPath())
            .filter { zipFileSystem.metadata(it).isRegularFile }
            .toList()

        paths.forEach { zipFilePath ->
            zipFileSystem.source(zipFilePath).buffer().use { source ->
                val relativeFilePath = zipFilePath.toString().trimStart('/')
                val fileToWrite = outputDir.resolve(relativeFilePath)
                fileToWrite.createParentDirectories()
                fileSystem.sink(fileToWrite).buffer().use { sink ->
                    val bytes = sink.writeAll(source)
                    println("Unzipped: $relativeFilePath to $fileToWrite; $bytes bytes written")
                }
            }
        }
        true
    } catch (e: Exception) {
        println("Error unzipping $zipFilePath to $outputDir: $e")
        false
    }
}

fun Path.createParentDirectories() {
    this.parent?.let { parent ->
        FileSystem.SYSTEM.createDirectories(parent)
    }
}