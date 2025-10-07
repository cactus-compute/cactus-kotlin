package com.cactus.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import platform.Foundation.NSURL
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.darwin.NSObject
import platform.UniformTypeIdentifiers.UTTypeAudio
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePickerLauncher(
    onFileSelected: (String?) -> Unit
): FilePickerLauncher {
    val scope = rememberCoroutineScope()
    
    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                val documentTypes = listOf(UTTypeAudio)
                val documentPicker = UIDocumentPickerViewController(
                    forOpeningContentTypes = documentTypes
                )
                
                documentPicker.delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>
                    ) {
                        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                        if (url != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val didStartAccessing = url.startAccessingSecurityScopedResource()
                                    
                                    try {
                                        val fileManager = NSFileManager.defaultManager
                                        // Always use a unique filename to avoid file conflicts
                                        val originalFileName = url.lastPathComponent ?: "audio.wav"
                                        val fileExtension = originalFileName.substringAfterLast(".", "wav")
                                        val uniqueId = NSUUID().UUIDString
                                        val fileName = "audio_${uniqueId}.${fileExtension}"
                                        val tempDir = NSTemporaryDirectory()
                                        val destinationPath = "$tempDir$fileName"
                                        val destinationURL = NSURL.fileURLWithPath(destinationPath)
                                        
                                        val success = fileManager.copyItemAtURL(url, destinationURL, null)
                                        
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                onFileSelected(destinationPath)
                                            } else {
                                                onFileSelected(null)
                                            }
                                        }
                                    } finally {
                                        if (didStartAccessing) {
                                            url.stopAccessingSecurityScopedResource()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        onFileSelected(null)
                                    }
                                }
                            }
                        } else {
                            onFileSelected(null)
                        }
                    }
                    
                    override fun documentPickerWasCancelled(
                        controller: UIDocumentPickerViewController
                    ) {
                        onFileSelected(null)
                    }
                }
                
                val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
                rootViewController?.presentViewController(
                    documentPicker,
                    animated = true,
                    completion = null
                )
            }
        }
    }
}
