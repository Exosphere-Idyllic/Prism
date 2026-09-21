package com.example.prism.domain.model.equalizer

import kotlinx.serialization.Serializable

@Serializable
data class EqBand(
    val id: Int,
    val enabled: Boolean = true,
    val type: EqFilterType = EqFilterType.BELL,
    val frequencyHz: Float,
    val gainDb: Float = 0f,
    val q: Float = 1.414f,
)
