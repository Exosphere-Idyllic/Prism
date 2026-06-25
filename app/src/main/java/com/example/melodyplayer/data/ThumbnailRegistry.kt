package com.example.melodyplayer.data

/**
 * ThumbnailRegistry has been removed.
 *
 * Previously it used global mutableStateMapOf<Long, Boolean> / mutableStateMapOf<String, Boolean>
 * objects that caused full-list recompositions on every thumbnail write.
 *
 * Thumbnail state is now exposed as StateFlow<Set<Long>> / StateFlow<Set<String>> from
 * LibraryViewModel, and consumed in composables via derivedStateOf so only the individual
 * item that gained a thumbnail recomposes.
 *
 * See: LibraryViewModel.albumThumbnail128Ids / albumThumbnail256Ids /
 *      songThumbnail128Ids / songThumbnail256Ids
 */