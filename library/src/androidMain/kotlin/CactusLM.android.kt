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

actual fun getModelPath(modelFolder: String): String {
    val appDocDir = File(applicationContext.filesDir, "models")
    return File(appDocDir, modelFolder).absolutePath
}
