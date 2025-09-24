package com.cactus

import okio.FileSystem

actual fun getOkioFileSystem(): FileSystem {
    return FileSystem.SYSTEM
}
