package com.example.prism.domain.model.equalizer

import kotlinx.serialization.Serializable

/**
 * Tipos de filtros biquad de segundo orden (IIR) soportados por el motor DSP.
 *
 * Cada topología implementa las funciones de transferencia normalizadas derivadas de
 * Robert Bristow-Johnson Audio EQ Cookbook.
 */
@Serializable
enum class EqFilterType {
    /** Filtro de pico/campana paramétrico simétrico (Peaking EQ) con ganancia en dB y factor Q. */
    BELL,
    /** Filtro tipo estante (shelf) para realzar o atenuar el extremo grave por debajo de la frecuencia de corte. */
    LOW_SHELF,
    /** Filtro tipo estante (shelf) para realzar o atenuar el extremo agudo por encima de la frecuencia de corte. */
    HIGH_SHELF,
    /** Filtro pasabajas de segundo orden (-12 dB/octava) para atenuar frecuencias superiores al corte. */
    LOW_PASS,
    /** Filtro pasaaltas de segundo orden (-12 dB/octava) para atenuar frecuencias inferiores al corte. */
    HIGH_PASS,
    /** Filtro notch o supresor de banda estrecha con atenuación profunda en la frecuencia central. */
    NOTCH
}
