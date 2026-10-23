package com.elizierdias.lumenkin.wallpaper

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
import com.elizierdias.lumenkin.MainActivity
import com.elizierdias.lumenkin.util.WallpaperFileManager

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
                Log.e(TAG, "registerReceiver failed", e)
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
            if (mediaPlayer != null && holder.surface.isValid) {
                try {
                    mediaPlayer?.setSurface(holder.surface)
                } catch (e: Exception) {
                    Log.e(TAG, "setSurface failed", e)
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
                Log.w(TAG, "Surface invalid")
                return
            }

            val videoFile = WallpaperFileManager.getActiveWallpaperFile(this@LumenkinWallpaperService)
            if (!videoFile.exists() || videoFile.length() == 0L) {
                Log.w(TAG, "No video file at ${videoFile.absolutePath}")
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
                val vol = if (soundEnabled) 1f else 0f
                player.setVolume(vol, vol)

                player.setOnPreparedListener { mp ->
                    isPrepared = true
                    if (isVisible && isScreenOn) {
                        try {
                            mp.start()
                        } catch (e: Exception) {
                            Log.e(TAG, "start failed", e)
                        }
                    }
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    isPrepared = false
                    releaseMediaPlayer()
                    true
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
                if (!mp.isPlaying) mp.start()
            } catch (e: Exception) {
                Log.e(TAG, "resume failed", e)
            }
        }

        private fun pausePlayback() {
            val mp = mediaPlayer ?: return
            if (!isPrepared) return
            try {
                if (mp.isPlaying) mp.pause()
            } catch (e: Exception) {
                Log.e(TAG, "pause failed", e)
            }
        }

        private fun releaseMediaPlayer() {
            val mp = mediaPlayer ?: return
            mediaPlayer = null
            isPrepared = false
            try {
                mp.setOnPreparedListener(null)
                mp.setOnErrorListener(null)
                try {
                    if (mp.isPlaying) mp.stop()
                } catch (_: Exception) { }
                mp.reset()
                mp.release()
            } catch (e: Exception) {
                Log.e(TAG, "release failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "LumenkinWP"
    }
}
