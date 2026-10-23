package com.lumenkin.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * WallpaperFileManager
 *
 * Gestão rigorosa de ficheiros conforme a constituição do Lumenkin:
 * - Caminho e nome FIXOS: lumenkin_active_wallpaper.mp4
 * - Antes de gravar: apaga o ficheiro anterior
 * - Garante zero ficheiros órfãos / duplicados
 */
object WallpaperFileManager {

    private const val ACTIVE_WALLPAPER_FILENAME = "lumenkin_active_wallpaper.mp4"

    /**
     * Retorna o ficheiro único e fixo do wallpaper ativo.
     */
    fun getActiveWallpaperFile(context: Context): File {
        return File(context.filesDir, ACTIVE_WALLPAPER_FILENAME)
    }

    /**
     * Prepara e grava o vídeo no caminho fixo.
     *
     * Fluxo constitucional:
     * 1. Apaga qualquer instância anterior do ficheiro
     * 2. Copia o novo vídeo para o caminho fixo
     * 3. Retorna true apenas se a operação for bem-sucedida
     *
     * Nota: A compressão dinâmica de resolução (4K → 720p) pode ser
     * adicionada aqui no futuro usando MediaCodec / MediaMuxer.
     * Por agora mantemos a cópia direta para máxima estabilidade e leveza.
     */
    fun prepareAndSaveVideo(context: Context, sourceUri: Uri): Boolean {
        val targetFile = getActiveWallpaperFile(context)

        // 1. Destruição forçada do ficheiro anterior (anti-sobreposição)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.exists() && targetFile.length() > 0
        } catch (e: IOException) {
            e.printStackTrace()
            // Em caso de erro, garante que não fica ficheiro parcial
            if (targetFile.exists()) targetFile.delete()
            false
        }
    }

    /**
     * Limpa completamente o wallpaper ativo (útil para reset).
     */
    fun clearActiveWallpaper(context: Context) {
        val file = getActiveWallpaperFile(context)
        if (file.exists()) {
            file.delete()
        }
    }
}
