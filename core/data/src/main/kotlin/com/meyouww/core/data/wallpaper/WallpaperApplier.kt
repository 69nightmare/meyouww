package com.meyouww.core.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import com.meyouww.core.model.WallpaperTarget
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The only way meyouww changes a wallpaper: WallpaperManager.setStream with
 * FLAG_SYSTEM / FLAG_LOCK. The system decodes the file, so meyouww never holds
 * a full-size bitmap in memory.
 */
object WallpaperApplier {
    private const val HISTORY_DIR = "wallpapers"
    private const val HOME_BASE = "home_base.webp"

    fun canApply(context: Context): Boolean {
        val wm = WallpaperManager.getInstance(context)
        return wm.isWallpaperSupported && wm.isSetWallpaperAllowed
    }

    fun flags(target: WallpaperTarget): Int = when (target) {
        WallpaperTarget.Home -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.Lock -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.Both -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }

    /** Applies an image. Throws on failure; the caller reports the reason. */
    fun apply(context: Context, bytes: ByteArray, target: WallpaperTarget) {
        WallpaperManager.getInstance(context).setStream(ByteArrayInputStream(bytes), null, false, flags(target))
    }

    fun historyDir(context: Context): File = File(context.filesDir, HISTORY_DIR).apply { mkdirs() }

    /** Keeps a copy so Undo can go back to it later. */
    fun saveToHistory(context: Context, commandId: String, bytes: ByteArray, extension: String): File {
        val safe = commandId.filter { it.isLetterOrDigit() || it == '-' }.take(64)
        val file = File(historyDir(context), "$safe.$extension")
        file.writeBytes(bytes)
        return file
    }

    /** The owner's own favourite picture, re-applied by "Restore my wallpaper". */
    fun homeBase(context: Context): File = File(context.filesDir, HOME_BASE)

    fun hasHomeBase(context: Context): Boolean = homeBase(context).isFile
}
