package com.cactus

import android.content.Context

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}
