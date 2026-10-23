package com.lumenkin

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

/**
 * Lumenkin - Main Activity
 *
 * Responsável por:
 * - Seleção de vídeo
 * - Processamento e gravação no caminho fixo
 * - Ativação do WallpaperService nativo
 * - Controlo de áudio (mudo por padrão)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedVideoUri: Uri? = null
    private var soundEnabled = false

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            binding.previewPlaceholder.text = "Vídeo selecionado ✓"
            binding.btnApply.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnSelectVideo.setOnClickListener {
            pickVideo.launch("video/*")
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
            // Persiste preferência para o WallpaperService
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SOUND_ENABLED, isChecked)
                .apply()
        }
    }

    private fun applyWallpaper() {
        val uri = selectedVideoUri ?: return

        binding.btnApply.isEnabled = false
        binding.btnApply.text = getString(R.string.processing)

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                WallpaperFileManager.prepareAndSaveVideo(
                    context = this@MainActivity,
                    sourceUri = uri
                )
            }

            if (success) {
                // Guarda preferência de som
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_SOUND_ENABLED, soundEnabled)
                    .apply()

                // Abre o seletor de wallpaper do sistema apontando para o nosso serviço
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, LumenkinWallpaperService::class.java)
                    )
                }
                startActivity(intent)

                Toast.makeText(this@MainActivity, R.string.success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }

            binding.btnApply.isEnabled = true
            binding.btnApply.text = getString(R.string.apply_wallpaper)
        }
    }

    companion object {
        const val PREFS_NAME = "lumenkin_prefs"
        const val KEY_SOUND_ENABLED = "sound_enabled"
    }
}
