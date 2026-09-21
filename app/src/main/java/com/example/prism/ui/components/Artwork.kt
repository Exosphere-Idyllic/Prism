package com.example.prism.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.example.prism.R
import com.example.prism.data.artwork.AlbumArtworkParams
import com.example.prism.data.artwork.SongArtworkParams
import com.example.prism.data.entity.Song
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage

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
private fun defaultCoverRes(stableId: Any): Int {
    val index = (stableId.hashCode() and 0x7FFFFFFF) % DEFAULT_COVERS.size
    return DEFAULT_COVERS[index]
}

/**
 * Displays artwork for a [Song].
 *
 * Internally uses Landscapist [CoilImage] backed by the app-wide [coil3.SingletonImageLoader]
 * configured in [com.example.prism.PrismApplication] — no new ImageLoader is ever created here,
 * preserving all custom Keyers, Fetchers, and caches (memory + disk).
 *
 * When no artwork is available (fetcher returns null / error), a deterministic
 * default cover drawable is shown based on the song's ID.
 *
 * The [crossfade] parameter is retained for call-site API compatibility; crossfade
 * animations at the player level are handled externally via Compose [androidx.compose.animation.Crossfade]
 * to avoid double-animation on [SongArtwork] instances inside animated containers.
 */
@Composable
fun SongArtwork(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 128,
    crossfade: Boolean = false,
    onPaletteLoaded: ((com.kmpalette.palette.graphics.Palette) -> Unit)? = null,
) {
    val context = LocalContext.current

    if (song != null) {
        val fallbackPainter = painterResource(defaultCoverRes(song.id))
        // Re-use the singleton loader configured in PrismApplication (keyers, fetchers, caches).
        val imageLoader = SingletonImageLoader.get(context)
        val imageRequest = remember(context, song.id, song.artworkUri, song.customArtworkUri, song.dateModified, size) {
            ImageRequest.Builder(context)
                .data(SongArtworkParams(song = song, size = size))
                .size(size)
                .build()
        }

        val component = com.skydoves.landscapist.components.rememberImageComponent {
            if (onPaletteLoaded != null) {
                +com.skydoves.landscapist.palette.PalettePlugin { palette ->
                    onPaletteLoaded(palette)
                }
            }
        }

        CoilImage(
            imageModel = { imageRequest },
            imageLoader = { imageLoader },
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
            component = component,
            modifier = modifier,
            failure = {
                Image(
                    painter = fallbackPainter,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = modifier,
                )
            },
            previewPlaceholder = painterResource(defaultCoverRes(song.id)),
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
 * Displays artwork for an album.
 *
 * Internally uses Landscapist [CoilImage] backed by the app-wide [coil3.SingletonImageLoader]
 * configured in [com.example.prism.PrismApplication] — no new ImageLoader is ever created here,
 * preserving all custom Keyers, Fetchers, and caches (memory + disk).
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
    val fallbackPainter = painterResource(defaultCoverRes(albumId))
    val imageLoader = SingletonImageLoader.get(context)

    val imageRequest = remember(context, albumId, coverUri, customCoverUri, size) {
        ImageRequest.Builder(context)
            .data(
                AlbumArtworkParams(
                    albumId = albumId,
                    coverUri = coverUri,
                    customCoverUri = customCoverUri,
                    size = size,
                )
            )
            .size(size)
            .build()
    }

    CoilImage(
        imageModel = { imageRequest },
        imageLoader = { imageLoader },
        imageOptions = ImageOptions(contentScale = ContentScale.Crop),
        modifier = modifier,
        failure = {
            Image(
                painter = fallbackPainter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
        },
        previewPlaceholder = painterResource(defaultCoverRes(albumId)),
    )
}

