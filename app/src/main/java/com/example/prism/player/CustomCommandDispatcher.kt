package com.example.prism.player

import android.os.Bundle

/**
 * Functional interface to dispatch custom commands through MediaSession / MediaController IPC.
 * Adheres to Interface Segregation Principle (ISP) to avoid bloating [PlaybackManager].
 */
fun interface CustomCommandDispatcher {
    fun sendCustomCommand(action: String, args: Bundle)
}
