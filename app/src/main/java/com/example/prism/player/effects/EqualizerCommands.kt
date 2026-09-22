package com.example.prism.player.effects

/**
 * Constantes para comandos de sesión IPC ([androidx.media3.session.SessionCommand])
 * utilizados para comunicar la interfaz de usuario con [PlaybackService] a través de Media3 MediaSession.
 */
object EqualizerCommands {
    /** Comando para transferir la configuración completa del ecualizador serializada en JSON. */
    const val COMMAND_SET_EQ_CONFIG = "com.example.prism.command.SET_EQ_CONFIG"
    /** Comando para alternar el estado habilitado/deshabilitado. */
    const val COMMAND_SET_EQ_ENABLED = "com.example.prism.command.SET_EQ_ENABLED"
    /** Comando para actualizar la ganancia de preamplificación en dB. */
    const val COMMAND_SET_EQ_PREAMP = "com.example.prism.command.SET_EQ_PREAMP"
    /** Comando para actualizar una banda individual serializada en JSON. */
    const val COMMAND_SET_EQ_BAND = "com.example.prism.command.SET_EQ_BAND"
    /** Comando para aplicar un preset por nombre. */
    const val COMMAND_APPLY_PRESET = "com.example.prism.command.APPLY_PRESET"
    /** Comando para restablecer el ecualizador a los valores por defecto. */
    const val COMMAND_RESET_EQ = "com.example.prism.command.RESET_EQ"

    /** Clave extra en Bundle: configuración completa en formato JSON. */
    const val EXTRA_CONFIG_JSON = "extra_config_json"
    /** Clave extra en Bundle: estado booleano enabled. */
    const val EXTRA_ENABLED = "extra_enabled"
    /** Clave extra en Bundle: valor flotante de preamplificación en dB. */
    const val EXTRA_PREAMP_DB = "extra_preamp_db"
    /** Clave extra en Bundle: banda serializada en JSON. */
    const val EXTRA_BAND_JSON = "extra_band_json"
    /** Clave extra en Bundle: nombre del preset String. */
    const val EXTRA_PRESET_NAME = "extra_preset_name"
}
