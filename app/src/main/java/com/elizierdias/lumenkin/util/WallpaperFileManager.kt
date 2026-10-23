package com.elizierdias.lumenkin.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object WallpaperFileManager {

    private const val TAG = "LumenkinFiles"
    private const val ACTIVE_WALLPAPER_FILENAME = "lumenkin_active_wallpaper.mp4"

    fun getActiveWallpaperFile(context: Context): File {
        return File(context.applicationContext.filesDir, ACTIVE_WALLPAPER_FILENAME)
    }

    fun prepareAndSaveVideo(context: Context, sourceUri: Uri): Boolean {
        val appContext = context.applicationContext
        val targetFile = getActiveWallpaperFile(appContext)
        val tempFile = File(appContext.filesDir, "lumenkin_temp_wallpaper.mp4")

        if (targetFile.exists()) targetFile.delete()
        if (tempFile.exists()) tempFile.delete()

        return try {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var total = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                    }
                    output.flush()
                    Log.d(TAG, "Copied $total bytes")
                }
            } ?: return false

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return false
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            targetFile.exists() && targetFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "prepareAndSaveVideo failed", e)
            tempFile.delete()
            targetFile.delete()
            false
        }
    }

    fun clearActiveWallpaper(context: Context) {
        val file = getActiveWallpaperFile(context)
        if (file.exists()) file.delete()
    }
}
