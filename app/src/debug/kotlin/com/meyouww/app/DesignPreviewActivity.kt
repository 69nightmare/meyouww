package com.meyouww.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.meyouww.core.designsystem.component.kasukabeBackdrop
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.feature.chat.ChatDesignSample
import java.io.File

/**
 * Debug only. Shows screens with made-up data so CI can screenshot them:
 * adb shell am start -n com.meyouww.app/.DesignPreviewActivity --es level partner
 */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val level = RelationshipLevel.fromWire(intent.getStringExtra("level") ?: "partner")
        val wallpaper = sampleWallpaper()
        setContent {
            MeyouwwTheme {
                Box(Modifier.fillMaxSize().kasukabeBackdrop()) {
                    ChatDesignSample(level, wallpaper)
                }
            }
        }
    }

    /** A sunset gradient with a sun, standing in for a sent wallpaper. */
    private fun sampleWallpaper(): File? = runCatching {
        val file = File(cacheDir, "design_preview_wallpaper.png")
        if (!file.isFile) {
            val bitmap = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val sky = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, 960f, intArrayOf(0xFF6F5BD8.toInt(), 0xFFF8A5BF.toInt(), 0xFFFFC72C.toInt()), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, 540f, 960f, sky)
            canvas.drawCircle(270f, 640f, 120f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF4E6.toInt() })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        file
    }.getOrNull()
}
