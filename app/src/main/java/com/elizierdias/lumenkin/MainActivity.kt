package com.elizierdias.lumenkin

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elizierdias.lumenkin.databinding.ActivityMainBinding
import com.elizierdias.lumenkin.util.WallpaperFileManager
import com.elizierdias.lumenkin.wallpaper.LumenkinWallpaperService
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupListeners()

        soundEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_SOUND_ENABLED, false)
        binding.switchSound.isChecked = soundEnabled
        binding.switchSound.text = if (soundEnabled) {
            getString(R.string.sound_enabled)
        } else {
            getString(R.string.sound_disabled)
        }
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
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SOUND_ENABLED, isChecked)
                .apply()
        }
    }

    private fun applyWallpaper() {
        val uri = selectedVideoUri ?: run {
            Toast.makeText(this, "Seleciona um vídeo primeiro", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnApply.isEnabled = false
        binding.btnApply.text = getString(R.string.processing)

        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    WallpaperFileManager.prepareAndSaveVideo(this@MainActivity, uri)
                }

                if (!success) {
                    Toast.makeText(this@MainActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_SOUND_ENABLED, soundEnabled)
                    .apply()

                // Aplica o live wallpaper de forma nativa
                setLiveWallpaperNative()
            } catch (e: Exception) {
                Log.e(TAG, "applyWallpaper failed", e)
                Toast.makeText(
                    this@MainActivity,
                    "Erro: ${e.message ?: "falha ao processar"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnApply.isEnabled = true
                binding.btnApply.text = getString(R.string.apply_wallpaper)
            }
        }
    }

    /**
     * Aplica o WallpaperService de forma nativa, por ordem de preferência:
     * 1. WallpaperManager.setWallpaperComponent (direto, sem UI)
     * 2. Intent ACTION_CHANGE_LIVE_WALLPAPER (pré-visualização nativa do sistema)
     * 3. Intent ACTION_LIVE_WALLPAPER_CHOOSER (lista de live wallpapers)
     */
    private fun setLiveWallpaperNative() {
        val component = ComponentName(
            this,
            LumenkinWallpaperService::class.java
        )

        // 1) Tentativa direta via WallpaperManager (nativo, sem ecrã intermédio)
        try {
            val wm = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                // setWallpaperComponent existe desde API 16; em apps normais
                // pode lançar SecurityException — nesse caso caímos no intent.
                val method = wm.javaClass.getMethod(
                    "setWallpaperComponent",
                    ComponentName::class.java
                )
                method.invoke(wm, component)
                Toast.makeText(this, R.string.success, Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Wallpaper aplicado via setWallpaperComponent")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "setWallpaperComponent não disponível, a usar Intent", e)
        }

        // 2) Intent oficial do sistema — abre a pré-visualização nativa
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "Confirma o wallpaper no ecrã seguinte", Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_CHANGE_LIVE_WALLPAPER falhou", e)
        }

        // 3) Chooser de live wallpapers do sistema
        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Toast.makeText(
                this,
                "Escolhe «Lumenkin Live Wallpaper» na lista",
                Toast.LENGTH_LONG
            ).show()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Nenhum método de wallpaper disponível", e)
            Toast.makeText(
                this,
                "Vídeo pronto. Vai a Definições → Ecrã de bloqueio/início → Papel de parede ao vivo → Lumenkin",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private const val TAG = "LumenkinMain"
        const val PREFS_NAME = "lumenkin_prefs"
        const val KEY_SOUND_ENABLED = "sound_enabled"
    }
}
