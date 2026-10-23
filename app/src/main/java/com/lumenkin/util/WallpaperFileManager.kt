package com.lumenkin.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Gestão rigorosa do ficheiro único de wallpaper.
 * Caminho fixo: filesDir/lumenkin_active_wallpaper.mp4
 */
object WallpaperFileManager {

    private const val TAG = "LumenkinFiles"
    private const val ACTIVE_WALLPAPER_FILENAME = "lumenkin_active_wallpaper.mp4"

    fun getActiveWallpaperFile(context: Context): File {
        return File(context.applicationContext.filesDir, ACTIVE_WALLPAPER_FILENAME)
    }

    /**
     * Copia o vídeo para o caminho fixo.
     * 1. Apaga o anterior
     * 2. Copia em stream (não carrega tudo na RAM)
     * 3. Valida tamanho > 0
     */
    fun prepareAndSaveVideo(context: Context, sourceUri: Uri): Boolean {
        val appContext = context.applicationContext
        val targetFile = getActiveWallpaperFile(appContext)
        val tempFile = File(appContext.filesDir, "lumenkin_temp_wallpaper.mp4")

        // Limpa restos
        if (targetFile.exists()) targetFile.delete()
        if (tempFile.exists()) tempFile.delete()

        return try {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024) // 64 KB – leve em RAM
                    var bytesRead: Int
                    var total = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                    }
                    output.flush()
                    Log.d(TAG, "Copied $total bytes")
                }
            } ?: run {
                Log.e(TAG, "openInputStream returned null for $sourceUri")
                return false
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "Temp file empty or missing")
                tempFile.delete()
                return false
            }

            // Rename atómico para o nome final
            if (!tempFile.renameTo(targetFile)) {
                // Fallback: copy + delete
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val ok = targetFile.exists() && targetFile.length() > 0
            Log.d(TAG, "Final file ok=$ok size=${targetFile.length()}")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "prepareAndSaveVideo failed", e)
            if (tempFile.exists()) tempFile.delete()
            if (targetFile.exists()) targetFile.delete()
            false
        }
    }

    fun clearActiveWallpaper(context: Context) {
        val file = getActiveWallpaperFile(context)
        if (file.exists()) file.delete()
    }
}
