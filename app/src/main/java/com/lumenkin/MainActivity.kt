package com.lumenkin

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lumenkin.databinding.ActivityMainBinding
import com.lumenkin.util.WallpaperFileManager
import com.lumenkin.wallpaper.LumenkinWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedVideoUri: Uri? = null
    private var soundEnabled = false

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            binding.previewPlaceholder.text = "Vídeo selecionado ✓"
            binding.btnApply.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupListeners()

            // Restaura preferência de som
            soundEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_SOUND_ENABLED, false)
            binding.switchSound.isChecked = soundEnabled
            binding.switchSound.text = if (soundEnabled) {
                getString(R.string.sound_enabled)
            } else {
                getString(R.string.sound_disabled)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            Toast.makeText(this, "Erro ao iniciar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        binding.btnSelectVideo.setOnClickListener {
            try {
                pickVideo.launch("video/*")
            } catch (e: Exception) {
                Toast.makeText(this, "Não foi possível abrir o seletor de vídeos", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "pickVideo failed", e)
            }
        }

        binding.btnApply.setOnClickListener {
            applyWallpaper()
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            soundEnabled = isChecked
            binding.switchSound.text = if (isChecked) {
                getString(R.string.sound_enabled)
            } else {
                getString(R.string.sound_disabled)
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SOUND_ENABLED, isChecked)
                .apply()
        }
    }

    private fun applyWallpaper() {
        val uri = selectedVideoUri
        if (uri == null) {
            Toast.makeText(this, "Seleciona um vídeo primeiro", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnApply.isEnabled = false
        binding.btnApply.text = getString(R.string.processing)

        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    WallpaperFileManager.prepareAndSaveVideo(
                        context = this@MainActivity,
                        sourceUri = uri
                    )
                }

                if (success) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_SOUND_ENABLED, soundEnabled)
                        .apply()

                    // Abre o seletor oficial de live wallpaper do sistema
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(
                                this@MainActivity,
                                LumenkinWallpaperService::class.java
                            )
                        )
                    }

                    try {
                        startActivity(intent)
                        Toast.makeText(this@MainActivity, R.string.success, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        // Fallback: abre definições de wallpaper genéricas
                        Log.e(TAG, "CHANGE_LIVE_WALLPAPER failed, fallback", e)
                        try {
                            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                            Toast.makeText(
                                this@MainActivity,
                                "Escolhe 'Lumenkin Live Wallpaper' na lista",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e2: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                "Vídeo guardado. Define o wallpaper em Definições → Ecrã",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(this@MainActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "applyWallpaper failed", e)
                Toast.makeText(
                    this@MainActivity,
                    "Erro: ${e.message ?: "falha ao processar vídeo"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnApply.isEnabled = true
                binding.btnApply.text = getString(R.string.apply_wallpaper)
            }
        }
    }

    companion object {
        private const val TAG = "LumenkinMain"
        const val PREFS_NAME = "lumenkin_prefs"
        const val KEY_SOUND_ENABLED = "sound_enabled"
    }
}
