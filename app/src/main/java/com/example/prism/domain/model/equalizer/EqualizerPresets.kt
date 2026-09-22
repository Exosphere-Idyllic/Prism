package com.example.prism.domain.model.equalizer

/**
 * Catálogo de presets o perfiles predefinidos de ecualización para el modo [EqualizerMode.SIMPLE].
 * Cada perfil contiene 10 valores de ganancia en dB correspondientes a las 10 frecuencias ISO estándar.
 */
object EqualizerPresets {
    val presets: Map<String, List<Float>> = mapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Rock" to listOf(4.5f, 3f, -1.5f, -2.5f, -1f, 1f, 3f, 4f, 4.5f, 4.5f),
        "Pop" to listOf(-1.5f, 1f, 2.5f, 3.5f, 2f, -1f, -1.5f, -1.5f, -1f, -1f),
        "Bass Boost" to listOf(7f, 5.5f, 4f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f),
        "Classical" to listOf(4.5f, 3.5f, 3f, 2.5f, -1.5f, -1.5f, 0f, 2f, 3f, 3.5f),
        "Vocal" to listOf(-2f, -1.5f, -1f, 1f, 3.5f, 3.5f, 2f, 0.5f, -1f, -2f),
        "Electronic" to listOf(4f, 3.5f, 1.5f, 0f, -2f, 2f, 1f, 2.5f, 4f, 4.5f),
        "Acoustic" to listOf(3.5f, 2.5f, 1.5f, 1f, 1f, 1.5f, 2.5f, 3.5f, 3f, 2f),
    )

    /**
     * Obtiene la lista de ganancias en dB para el preset dado, o null si no existe.
     */
    fun getGains(presetName: String): List<Float>? = presets[presetName]

    /**
     * Lista de nombres de todos los presets disponibles.
     */
    val presetNames: List<String> = presets.keys.toList()
}
