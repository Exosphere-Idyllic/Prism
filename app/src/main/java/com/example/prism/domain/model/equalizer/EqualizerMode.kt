package com.example.prism.domain.model.equalizer

import kotlinx.serialization.Serializable

/**
 * Modos de operación y presentación del ecualizador.
 */
@Serializable
enum class EqualizerMode {
    /** Modo clásico gráfico con 10 bandas de frecuencias normalizadas ISO y perfiles (presets). */
    SIMPLE,
    /** Modo paramétrico interactivo de 8 bandas con curva continua y control total de ganancia, Q y frecuencia en Canvas. */
    ADVANCED
}
