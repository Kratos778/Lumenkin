package com.lumenkin.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.lumenkin.MainActivity
import com.lumenkin.util.WallpaperFileManager

/**
 * LumenkinWallpaperService — motor nativo de live wallpaper.
 *
 * Corrigido para:
 * - Android 13+ (RECEIVER_NOT_EXPORTED)
 * - MediaPlayer só inicia depois de prepared
 * - Surface válida antes de setSurface
 * - Nunca chama isPlaying() em estado inválido
 */
class LumenkinWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = LumenkinEngine()

    inner class LumenkinEngine : Engine() {

        private var mediaPlayer: MediaPlayer? = null
        private var isVisible = false
        private var isScreenOn = true
        private var isPrepared = false
        private var surfaceHolder: SurfaceHolder? = null

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
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    registerReceiver(screenReceiver, filter)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register screen receiver", e)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceHolder = holder
            setupMediaPlayer(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceHolder = holder
            // Se o player já existe e a surface mudou, reassocia
            if (mediaPlayer != null && holder.surface.isValid) {
                try {
                    mediaPlayer?.setSurface(holder.surface)
                } catch (e: Exception) {
                    Log.e(TAG, "setSurface on change failed", e)
                    setupMediaPlayer(holder)
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releaseMediaPlayer()
            surfaceHolder = null
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisible = visible
            if (visible && isScreenOn) {
                // Se ainda não há player (ex: ficheiro gravado depois), tenta criar
                if (mediaPlayer == null) {
                    surfaceHolder?.let { setupMediaPlayer(it) }
                } else {
                    resumePlayback()
                }
            } else {
                pausePlayback()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) { }
            releaseMediaPlayer()
        }

        private fun setupMediaPlayer(holder: SurfaceHolder) {
            releaseMediaPlayer()

            if (!holder.surface.isValid) {
                Log.w(TAG, "Surface not valid, skip setup")
                return
            }

            val videoFile = WallpaperFileManager.getActiveWallpaperFile(this@LumenkinWallpaperService)
            if (!videoFile.exists() || videoFile.length() == 0L) {
                Log.w(TAG, "No active wallpaper file")
                return
            }

            try {
                val player = MediaPlayer()
                mediaPlayer = player
                isPrepared = false

                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )

                player.setSurface(holder.surface)
                player.setDataSource(videoFile.absolutePath)
                player.isLooping = true

                val soundEnabled = getSharedPreferences(
                    MainActivity.PREFS_NAME,
                    Context.MODE_PRIVATE
                ).getBoolean(MainActivity.KEY_SOUND_ENABLED, false)

                player.setVolume(
                    if (soundEnabled) 1f else 0f,
                    if (soundEnabled) 1f else 0f
                )

                player.setOnPreparedListener { mp ->
                    isPrepared = true
                    Log.d(TAG, "MediaPlayer prepared")
                    if (isVisible && isScreenOn) {
                        try {
                            mp.start()
                        } catch (e: Exception) {
                            Log.e(TAG, "start after prepare failed", e)
                        }
                    }
                }

                player.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    isPrepared = false
                    releaseMediaPlayer()
                    true
                }

                player.setOnCompletionListener {
                    // looping=true normalmente não chega aqui, mas por segurança
                    try {
                        if (isPrepared && isVisible && isScreenOn) {
                            it.seekTo(0)
                            it.start()
                        }
                    } catch (_: Exception) { }
                }

                player.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "setupMediaPlayer failed", e)
                releaseMediaPlayer()
            }
        }

        private fun resumePlayback() {
            val mp = mediaPlayer ?: return
            if (!isPrepared) return
            try {
                if (!mp.isPlaying) {
                    mp.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "resumePlayback failed", e)
            }
        }

        private fun pausePlayback() {
            val mp = mediaPlayer ?: return
            if (!isPrepared) return
            try {
                if (mp.isPlaying) {
                    mp.pause()
                }
            } catch (e: Exception) {
                Log.e(TAG, "pausePlayback failed", e)
            }
        }

        private fun releaseMediaPlayer() {
            val mp = mediaPlayer
            mediaPlayer = null
            isPrepared = false
            if (mp == null) return
            try {
                mp.setOnPreparedListener(null)
                mp.setOnErrorListener(null)
                mp.setOnCompletionListener(null)
                try {
                    if (mp.isPlaying) mp.stop()
                } catch (_: Exception) { }
                mp.reset()
                mp.release()
            } catch (e: Exception) {
                Log.e(TAG, "releaseMediaPlayer failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "LumenkinWP"
    }
}
