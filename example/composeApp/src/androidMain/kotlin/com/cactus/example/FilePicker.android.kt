package com.cactus.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

@Composable
actual fun rememberFilePickerLauncher(
    onFileSelected: (String?) -> Unit,
    mimeType: String
): FilePickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val tempFile = copyUriToTempFileQuick(context, uri)
                    
                    withContext(Dispatchers.Main) {
                        onFileSelected(tempFile?.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        onFileSelected(null)
                    }
                }
            }
        } else {
            onFileSelected(null)
        }
    }
    
    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                launcher.launch(mimeType)
            }
        }
    }
}

private fun copyUriToTempFileQuick(context: Context, uri: Uri): File? {
    return try {
        val detectedType = context.contentResolver.getType(uri) ?: ""
        val isImage = detectedType.startsWith("image")
        val fileName = if (isImage) "picked_image_${System.currentTimeMillis()}.jpg" else "temp_audio_${System.currentTimeMillis()}.wav"
        val tempFile = File(context.cacheDir, fileName)
        
        if (isImage) {
            // Resize and compress image
            context.contentResolver.openInputStream(uri)?.use { input ->
                val originalBitmap = BitmapFactory.decodeStream(input)
                if (originalBitmap != null) {
                    // Resize to max 512x512 while maintaining aspect ratio
                    val maxDimension = 512
                    val originalWidth = originalBitmap.width
                    val originalHeight = originalBitmap.height
                    
                    var targetWidth = originalWidth
                    var targetHeight = originalHeight
                    
                    if (originalWidth > maxDimension || originalHeight > maxDimension) {
                        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
                        if (originalWidth > originalHeight) {
                            targetWidth = maxDimension
                            targetHeight = (maxDimension / aspectRatio).toInt()
                        } else {
                            targetHeight = maxDimension
                            targetWidth = (maxDimension * aspectRatio).toInt()
                        }
                    }
                    
                    // Create resized bitmap
                    val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
                    
                    // Save as JPEG with 85% quality
                    FileOutputStream(tempFile).use { output ->
                        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                    }
                    
                    // Clean up bitmaps
                    if (resizedBitmap != originalBitmap) {
                        resizedBitmap.recycle()
                    }
                    originalBitmap.recycle()
                } else {
                    return null
                }
            }
        } else {
            // For audio files, just copy
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, 16384)
                }
            }
        }
        
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
