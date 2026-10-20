package com.example.prism.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.ImageRequest
import coil3.toBitmap
import com.example.prism.R
import com.example.prism.data.artwork.AlbumArtFetcher
import com.example.prism.data.artwork.AlbumArtworkParams
import com.example.prism.data.artwork.SongArtworkParams
import com.example.prism.data.entity.Song
import com.example.prism.ui.theme.AppSurface3

/**
 * Drawable resource IDs for the 5 default cover art variations.
 * A deterministic hash of the item's ID picks one, so the same song/album
 * always shows the same default, and it doesn't change on recomposition.
 */
private val DEFAULT_COVERS = intArrayOf(
    R.drawable.default_cover_1,
    R.drawable.default_cover_2,
    R.drawable.default_cover_3,
    R.drawable.default_cover_4,
    R.drawable.default_cover_5,
)

/**
 * Picks a deterministic default cover drawable based on a stable identifier.
 * Uses the hashCode so the same id always maps to the same drawable.
 */
internal fun defaultCoverRes(stableId: Any): Int {
    val index = (stableId.hashCode() and 0x7FFFFFFF) % DEFAULT_COVERS.size
    return DEFAULT_COVERS[index]
}

internal fun defaultCoverIndex(stableId: Any): Int {
    return (stableId.hashCode() and 0x7FFFFFFF) % DEFAULT_COVERS.size
}

@Composable
fun rememberDefaultCoverPainters(): List<Painter> {
    return listOf(
        painterResource(R.drawable.default_cover_1),
        painterResource(R.drawable.default_cover_2),
        painterResource(R.drawable.default_cover_3),
        painterResource(R.drawable.default_cover_4),
        painterResource(R.drawable.default_cover_5),
    )
}

/**
 * Static [ColorPainter] used as the loading placeholder for song/album artwork in lists.
 *
 * Using a [ColorPainter] here instead of [painterResource] has two important advantages:
 * 1. No XML parsing or VectorDrawable tree construction on the UI thread during fast scroll.
 * 2. Zero per-composition allocations — the same instance is reused by every list cell.
 *
 * The default cover drawables ([DEFAULT_COVERS]) are only shown when artwork definitively
 * fails to load (error / fallback), not during the transient loading state.
 */
private val ArtworkLoadingPlaceholder = ColorPainter(AppSurface3)

/**
 * Displays artwork for a [Song].
 *
 * Loading state: a neutral flat [AppSurface3] background — zero overhead, no XML parse.
 * Error / Fallback (song has no artwork): deterministic default cover drawable.
 *
 * When [onPaletteLoaded] is provided, extracts dynamic theme colors asynchronously
 * via [androidx.palette.graphics.Palette].
 */
@Composable
fun SongArtwork(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 160,
    crossfade: Boolean = false,
    defaultCoverPainters: List<Painter>? = null,
    onPaletteLoaded: ((androidx.palette.graphics.Palette) -> Unit)? = null,
) {
    val context = LocalContext.current

    if (song != null) {
        val imageLoader = remember(context) { SingletonImageLoader.get(context) }
        val currentOnPaletteLoaded by rememberUpdatedState(onPaletteLoaded)
        val defaultCoverPainter = if (defaultCoverPainters != null) {
            defaultCoverPainters[defaultCoverIndex(song.id)]
        } else {
            painterResource(defaultCoverRes(song.id))
        }
        val imageRequest = remember(song.id, song.artworkUri, song.customArtworkUri, song.dateModified, currentOnPaletteLoaded != null) {
            ImageRequest.Builder(context)
                .data(SongArtworkParams(song = song, size = AlbumArtFetcher.MASTER_ARTWORK_SIZE))
                .size(size)
                .crossfade(crossfade)
                .apply {
                    if (currentOnPaletteLoaded != null) {
                        allowHardware(false) // Software bitmap required to read pixels for Palette generation
                    }
                }
                .build()
        }

        AsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            // Loading: neutral surface color — no XML parsing, no default cover flash
            placeholder = ArtworkLoadingPlaceholder,
            // Error / Fallback: song has no artwork at all → show default cover
            error = defaultCoverPainter,
            fallback = defaultCoverPainter,
            onSuccess = { state ->
                currentOnPaletteLoaded?.let { callback ->
                    val bitmap = state.result.image.toBitmap()
                    androidx.palette.graphics.Palette.from(bitmap)
                        .maximumColorCount(16)
                        .generate { palette ->
                            if (palette != null) {
                                callback(palette)
                            }
                        }
                }
            },
            modifier = modifier,
        )
    } else {
        // No song at all — show a static default.
        Image(
            painter = painterResource(DEFAULT_COVERS[0]),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/**
 * Displays artwork for an album using lightweight [AsyncImage].
 *
 * Loading state: neutral [AppSurface3] flat color — no XML parsing overhead.
 * Error / Fallback (album has no artwork): deterministic default cover drawable.
 */
@Composable
fun AlbumArtwork(
    albumId: Long,
    coverUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    customCoverUri: String = "",
    size: Int = 256,
    crossfade: Boolean = false,
    defaultCoverPainters: List<Painter>? = null,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val defaultCoverPainter = if (defaultCoverPainters != null) {
        defaultCoverPainters[defaultCoverIndex(albumId)]
    } else {
        painterResource(defaultCoverRes(albumId))
    }

    val imageRequest = remember(albumId, coverUri, customCoverUri) {
        ImageRequest.Builder(context)
            .data(
                AlbumArtworkParams(
                    albumId = albumId,
                    coverUri = coverUri,
                    customCoverUri = customCoverUri,
                    size = AlbumArtFetcher.MASTER_ARTWORK_SIZE,
                )
            )
            .size(size)
            .crossfade(crossfade)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        // Loading: neutral surface color — no default cover flash while image loads
        placeholder = ArtworkLoadingPlaceholder,
        // Error / Fallback: album has no artwork → show default cover
        error = defaultCoverPainter,
        fallback = defaultCoverPainter,
        modifier = modifier,
    )
}

/**
 * Displays artwork for the main player screen using Landscapist [PrismCoilImage].
 *
 * Configured specifically for high-res display (512px), smooth CrossfadePlugin transitions,
 * ShimmerPlugin as loading placeholder (animated shimmer — visually rich in a full-screen context),
 * and automatic palette extraction via PalettePlugin to drive dynamic background theming.
 * The default cover is only shown if the song has no artwork (error state).
 */
@Composable
fun PlayerSongArtwork(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onPrimaryColorExtracted: ((androidx.compose.ui.graphics.Color) -> Unit)? = null,
    onPaletteLoaded: ((com.kmpalette.palette.graphics.Palette) -> Unit)? = null,
) {
    if (song != null) {
        val context = LocalContext.current
        val imageRequest = remember(song.id, song.artworkUri, song.customArtworkUri, song.dateModified) {
            ImageRequest.Builder(context)
                .data(SongArtworkParams(song = song, size = AlbumArtFetcher.MASTER_ARTWORK_SIZE))
                .size(512)
                .allowHardware(false) // Software bitmap required for Palette pixel reading
                .build()
        }

        PrismCoilImage(
            imageModel = { imageRequest },
            contentDescription = contentDescription,
            modifier = modifier,
            // No placeholderRes — ShimmerPlugin handles loading state inside PrismCoilImage
            errorRes = defaultCoverRes(song.id),
            crossfadeDuration = 400,
            onPaletteLoaded = onPaletteLoaded,
            onPrimaryColorExtracted = onPrimaryColorExtracted,
        )
    } else {
        Image(
            painter = painterResource(DEFAULT_COVERS[0]),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/**
 * Displays hero artwork for an album in [com.example.prism.ui.album.AlbumDetailScreen]
 * using Landscapist [PrismCoilImage].
 *
 * ShimmerPlugin handles the loading state with an animated shimmer effect.
 * The default cover is only shown if the album has no artwork (error state).
 */
@Composable
fun AlbumHeroArtwork(
    albumId: Long,
    coverUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    customCoverUri: String = "",
    size: Int = 256,
) {
    val context = LocalContext.current
    val imageRequest = remember(albumId, coverUri, customCoverUri) {
        ImageRequest.Builder(context)
            .data(
                AlbumArtworkParams(
                    albumId = albumId,
                    coverUri = coverUri,
                    customCoverUri = customCoverUri,
                    size = AlbumArtFetcher.MASTER_ARTWORK_SIZE,
                )
            )
            .size(size)
            .build()
    }

    PrismCoilImage(
        imageModel = { imageRequest },
        contentDescription = contentDescription,
        modifier = modifier,
        // No placeholderRes — ShimmerPlugin handles loading state inside PrismCoilImage
        errorRes = defaultCoverRes(albumId),
        crossfadeDuration = 350,
    )
}
