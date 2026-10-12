package com.example.prism.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
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

/**
 * Displays artwork for a [Song].
 * Uses highly optimized [AsyncImage] to avoid subcomposition overhead and frame-drops.
 * When [onPaletteLoaded] is provided, extracts dynamic theme colors asynchronously via [androidx.palette.graphics.Palette].
 */
@Composable
fun SongArtwork(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 160,
    crossfade: Boolean = false,
    onPaletteLoaded: ((androidx.palette.graphics.Palette) -> Unit)? = null,
) {
    val context = LocalContext.current

    if (song != null) {
        val imageLoader = remember(context) { SingletonImageLoader.get(context) }
        val currentOnPaletteLoaded by rememberUpdatedState(onPaletteLoaded)
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
            placeholder = painterResource(defaultCoverRes(song.id)),
            error = painterResource(defaultCoverRes(song.id)),
            fallback = painterResource(defaultCoverRes(song.id)),
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
 * When no artwork is available, a deterministic default cover drawable is shown
 * based on the album's ID.
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
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }

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
        placeholder = painterResource(defaultCoverRes(albumId)),
        error = painterResource(defaultCoverRes(albumId)),
        fallback = painterResource(defaultCoverRes(albumId)),
        modifier = modifier,
    )
}

/**
 * Displays artwork for the main player screen using Landscapist [PrismCoilImage].
 *
 * Configured specifically for high-res display (512px), smooth CrossfadePlugin transitions,
 * and automatic palette extraction via PalettePlugin to drive dynamic background theming.
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
            placeholderRes = defaultCoverRes(song.id),
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

