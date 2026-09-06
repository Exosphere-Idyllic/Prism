package com.example.prism.domain.repository

/**
 * Unified facade repository combining library, playlist, and scanning operations.
 */
interface MusicRepository : LibraryRepository, PlaylistRepository, ScannerRepository
