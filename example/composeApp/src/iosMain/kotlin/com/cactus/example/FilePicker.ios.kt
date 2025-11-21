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
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePickerLauncher(
    onFileSelected: (String?) -> Unit,
    mimeType: String
): FilePickerLauncher {
    val scope = rememberCoroutineScope()
    
    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                if (mimeType.startsWith("image")) {
                    // Use PHPicker for images (photos library)
                    val config = PHPickerConfiguration()
                    config.selectionLimit = 1
                    config.filter = platform.PhotosUI.PHPickerFilter.imagesFilter
                    
                    val picker = PHPickerViewController(configuration = config)
                    picker.delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                        override fun picker(
                            picker: PHPickerViewController,
                            didFinishPicking: List<*>
                        ) {
                            picker.dismissViewControllerAnimated(true, null)
                            
                            val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                            if (results.isEmpty()) {
                                onFileSelected(null)
                                return
                            }
                            
                            val result = results.first()
                            val itemProvider = result.itemProvider
                            
                            // Try to load image data
                            val typeIdentifier = "public.image"
                            if (itemProvider.hasItemConformingToTypeIdentifier(typeIdentifier)) {
                                itemProvider.loadDataRepresentationForTypeIdentifier(typeIdentifier) { data, error ->
                                    if (error != null || data == null) {
                                        println("Error loading image data: $error")
                                        onFileSelected(null)
                                        return@loadDataRepresentationForTypeIdentifier
                                    }
                                    
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val uniqueId = NSUUID().UUIDString
                                            val fileName = "image_${uniqueId}.jpg"
                                            val tempDir = NSTemporaryDirectory()
                                            val destinationPath = "$tempDir$fileName"
                                            
                                            // Create UIImage from data
                                            val originalImage = platform.UIKit.UIImage.imageWithData(data)
                                            if (originalImage != null) {
                                                // Resize image to max 512x512 while maintaining aspect ratio
                                                val maxDimension = 512.0
                                                val (originalWidth, originalHeight) = originalImage.size.useContents {
                                                    width to height
                                                }
                                                
                                                var targetWidth = originalWidth
                                                var targetHeight = originalHeight
                                                
                                                if (originalWidth > maxDimension || originalHeight > maxDimension) {
                                                    val aspectRatio = originalWidth / originalHeight
                                                    if (originalWidth > originalHeight) {
                                                        targetWidth = maxDimension
                                                        targetHeight = maxDimension / aspectRatio
                                                    } else {
                                                        targetHeight = maxDimension
                                                        targetWidth = maxDimension * aspectRatio
                                                    }
                                                }
                                                
                                                // Create resized image
                                                val targetSize = platform.CoreGraphics.CGSizeMake(targetWidth, targetHeight)
                                                platform.UIKit.UIGraphicsBeginImageContextWithOptions(targetSize, false, 1.0)
                                                originalImage.drawInRect(platform.CoreGraphics.CGRectMake(0.0, 0.0, targetWidth, targetHeight))
                                                val resizedImage = platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
                                                platform.UIKit.UIGraphicsEndImageContext()
                                                
                                                // Convert to JPEG with quality 0.85
                                                val jpegData = if (resizedImage != null) {
                                                    platform.UIKit.UIImageJPEGRepresentation(resizedImage, 0.85)
                                                } else {
                                                    platform.UIKit.UIImageJPEGRepresentation(originalImage, 0.85)
                                                }
                                                
                                                if (jpegData != null) {
                                                    // Use NSFileManager to create and write file
                                                    val fileManager = NSFileManager.defaultManager
                                                    val success = fileManager.createFileAtPath(
                                                        destinationPath,
                                                        jpegData,
                                                        null
                                                    )
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        if (success) {
                                                            onFileSelected(destinationPath)
                                                        } else {
                                                            onFileSelected(null)
                                                        }
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        onFileSelected(null)
                                                    }
                                                }
                                            } else {
                                                // Fallback: save data directly (shouldn't happen for images)
                                                val fileManager = NSFileManager.defaultManager
                                                val success = fileManager.createFileAtPath(
                                                    destinationPath,
                                                    data,
                                                    null
                                                )
                                                
                                                withContext(Dispatchers.Main) {
                                                    if (success) {
                                                        onFileSelected(destinationPath)
                                                    } else {
                                                        onFileSelected(null)
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            println("Error saving image: ${e.message}")
                                            withContext(Dispatchers.Main) {
                                                onFileSelected(null)
                                            }
                                        }
                                    }
                                }
                            } else {
                                onFileSelected(null)
                            }
                        }
                    }
                    
                    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
                    rootViewController?.presentViewController(
                        picker,
                        animated = true,
                        completion = null
                    )
                } else {
                    // Use UIDocumentPicker for audio files
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
}
