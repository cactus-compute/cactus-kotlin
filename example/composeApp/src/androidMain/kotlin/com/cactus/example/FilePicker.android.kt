package com.cactus.example

import android.content.Context
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
import java.util.UUID

@Composable
actual fun rememberFilePickerLauncher(
    onFileSelected: (String?) -> Unit
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
                launcher.launch("audio/*")
            }
        }
    }
}

private fun copyUriToTempFileQuick(context: Context, uri: Uri): File? {
    return try {
        val fileName = "temp_audio_${System.currentTimeMillis()}.wav"
        val tempFile = File(context.cacheDir, fileName)
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output, 16384)
            }
        }
        
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
