package com.lumenkin.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.lumenkin.MainActivity
import com.lumenkin.util.WallpaperFileManager
import java.io.IOException

/**
 * LumenkinWallpaperService
 *
 * Serviço oficial de Live Wallpaper.
 *
 * Princípios constitucionais aplicados:
 * - MediaPlayer pausado em onVisibilityChanged e ACTION_SCREEN_OFF
 * - Volume 0f por padrão (só sobe se o utilizador ativar)
 * - Ficheiro único e fixo: lumenkin_active_wallpaper.mp4
 * - Destruição completa da instância anterior antes de carregar novo vídeo
 * - Zero dependências pesadas
 */
class LumenkinWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = LumenkinEngine()

    inner class LumenkinEngine : Engine() {

        private var mediaPlayer: MediaPlayer? = null
        private var isVisible = false
        private var isScreenOn = true

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        pausePlayback()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        if (isVisible) resumePlayback()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            // Regista receiver para eventos de ecrã
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenReceiver, filter)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            setupMediaPlayer(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releaseMediaPlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisible = visible
            if (visible && isScreenOn) {
                resumePlayback()
            } else {
                pausePlayback()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) { /* já desregistado */ }
            releaseMediaPlayer()
        }

        private fun setupMediaPlayer(holder: SurfaceHolder) {
            // Garante destruição total da instância anterior
            releaseMediaPlayer()

            val videoFile = WallpaperFileManager.getActiveWallpaperFile(this@LumenkinWallpaperService)
            if (!videoFile.exists()) return

            try {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(holder.surface)
                    setDataSource(videoFile.absolutePath)
                    isLooping = true

                    // Áudio mudo por padrão (constituição do projeto)
                    val soundEnabled = getSharedPreferences(
                        MainActivity.PREFS_NAME,
                        Context.MODE_PRIVATE
                    ).getBoolean(MainActivity.KEY_SOUND_ENABLED, false)

                    if (soundEnabled) {
                        setVolume(1f, 1f)
                    } else {
                        setVolume(0f, 0f)
                    }

                    setOnPreparedListener { mp ->
                        if (isVisible && isScreenOn) {
                            mp.start()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        releaseMediaPlayer()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                releaseMediaPlayer()
            }
        }

        private fun resumePlayback() {
            mediaPlayer?.let { mp ->
                if (!mp.isPlaying) {
                    try {
                        mp.start()
                    } catch (_: IllegalStateException) { /* estado inválido */ }
                }
            }
        }

        private fun pausePlayback() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    try {
                        mp.pause()
                    } catch (_: IllegalStateException) { /* estado inválido */ }
                }
            }
        }

        private fun releaseMediaPlayer() {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                    mp.reset()
                    mp.release()
                } catch (_: Exception) { /* ignore */ }
            }
            mediaPlayer = null
        }
    }
}
