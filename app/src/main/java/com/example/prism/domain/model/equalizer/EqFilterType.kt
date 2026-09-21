package com.example.prism.domain.model.equalizer

import kotlinx.serialization.Serializable

@Serializable
enum class EqFilterType {
    BELL,
    LOW_SHELF,
    HIGH_SHELF,
    LOW_PASS,
    HIGH_PASS,
    NOTCH
}
