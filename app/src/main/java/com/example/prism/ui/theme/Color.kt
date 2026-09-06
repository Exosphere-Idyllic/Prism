package com.example.prism.ui.theme

import androidx.compose.ui.graphics.Color

// ── Prism App Design System ────────────────────────────────────────────────────
// Single source of truth for all recurring colors. Add new UI files here and
// import `com.example.prism.ui.theme.*` instead of declaring local vals.

val AppAccent      = Color(0xFF6366F1)   // Primary indigo accent
val AppAccentSoft  = Color(0xFF818CF8)   // Lighter indigo (selected state, playing bars)
val AppAccentBg    = Color(0x1A6366F1)   // Accent at ~10 % alpha (selected row bg)

val AppTextPrimary   = Color(0xFFF1F5F9) // High-emphasis text
val AppTextSecondary = Color(0xFF64748B) // Low-emphasis / muted text

// Surfaces — three close variants used across cards, buttons, and artwork placeholders
val AppSurface      = Color(0xFF131825)  // Card/mini-player surface
val AppSurface2     = Color(0xFF141B2D)  // Search bar / player button surface
val AppSurface3     = Color(0xFF151B26)  // Song-list artwork placeholder

val AppTrackBg      = Color(0xFF1E293B)  // Seek-bar inactive track
val AppTrackOverlay = Color(0x1AFFFFFF)  // MiniPlayer progress bar track (white 10 %)

// Backgrounds (darkest → dark, used in vertical gradient brushes)
val AppBgTop     = Color(0xFF0D1117)     // Player screen top
val AppBgBottom  = Color(0xFF070B10)     // Player screen bottom
val AppBgDeep    = Color(0xFF0F0F1A)     // Library screen gradient step 1
val AppBgDeeper  = Color(0xFF0B0B12)     // Library screen gradient step 2
val AppBgDarkest = Color(0xFF08080C)     // Library screen gradient step 3

val AppDeleteTint    = Color(0xFFEF4444) // Danger / delete actions
val AppFavoriteColor = Color(0xFFEF4444) // Active favorite heart (same hue as delete)
val AppIconInactive  = Color(0x59F1F5F9) // Inactive icon (35 % alpha white)
