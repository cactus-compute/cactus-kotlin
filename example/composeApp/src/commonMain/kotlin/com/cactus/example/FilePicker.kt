package com.cactus.example

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePickerLauncher(
    onFileSelected: (String?) -> Unit,
    mimeType: String
): FilePickerLauncher

interface FilePickerLauncher {
    fun launch()
}
