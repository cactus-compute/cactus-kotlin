@file:OptIn(ExperimentalForeignApi::class)
package com.cactus

import utils.IOSFileUtils
import kotlinx.cinterop.*

actual fun getModelPath(modelFolder: String): String {
    val modelsDir = IOSFileUtils.getModelsDirectory() ?: ""
    return "$modelsDir/$modelFolder"
}