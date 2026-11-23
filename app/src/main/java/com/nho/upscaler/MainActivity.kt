package com.nho.upscaler

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.nho.upscaler.ui.GameUpscalerApp

class MainActivity : ComponentActivity() {
    private lateinit var prefsMgr: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsMgr = PrefsManager(this)

        // Edge-to-edge window setup for Jetpack Compose Material 3
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Shizuku physical display query on startup
        if (ShizukuHelper.ensureReady(this)) {
            ShizukuHelper.fetchPhysicalDisplay { w, h, den ->
                // Physical display cached in ShizukuHelper
            }
        }

        setContent {
            GameUpscalerApp()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ShizukuHelper.ensureReady(this)) {
            ShizukuHelper.fetchPhysicalDisplay { _, _, _ -> }
        }
    }
}
