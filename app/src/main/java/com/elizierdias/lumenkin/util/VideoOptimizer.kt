package com.elizierdias.lumenkin.util

/**
 * Placeholder para compressão dinâmica futura (4K → 720p).
 */
object VideoOptimizer {
    const val TARGET_WIDTH = 720
    const val TARGET_HEIGHT = 1280
    const val TARGET_BITRATE = 2_500_000

    fun optimizeIfNeeded(sourcePath: String, targetPath: String): Boolean = false
}
