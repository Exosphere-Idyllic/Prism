package com.example.prism.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.prism.R
import com.example.prism.data.artwork.AlbumArtworkParams
import com.example.prism.data.entity.Song
import com.example.prism.data.artwork.SongArtworkParams

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
 * URI resolution and caching is handled by [com.example.prism.data.artwork.AlbumArtFetcher] inside Coil.
 * When no artwork is available (fetcher returns null / error), a deterministic
 * default cover drawable is shown based on the song's ID.
 */
@Composable
fun SongArtwork(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 128,
    crossfade: Boolean = false,
) {
    val context = LocalContext.current

    val imageRequest = remember(context, song?.id, song?.artworkUri, song?.customArtworkUri, song?.dateModified, size, crossfade) {
        song?.let {
            ImageRequest.Builder(context)
                .data(SongArtworkParams(song = it, size = size))
                .crossfade(crossfade)
                .size(size)
                .build()
        }
    }

    if ((imageRequest != null) && (song != null)) {
        val fallbackPainter = painterResource(defaultCoverRes(song.id))

        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            fallback = fallbackPainter,
        )
    } else {
        // No song at all — show a static default
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
 * URI resolution and caching is handled by [com.example.prism.data.artwork.AlbumArtFetcher] inside Coil.
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

    val imageRequest = remember(context, albumId, coverUri, customCoverUri, size, crossfade) {
        ImageRequest.Builder(context)
            .data(
                AlbumArtworkParams(
                    albumId = albumId,
                    coverUri = coverUri,
                    customCoverUri = customCoverUri,
                    size = size,
                )
            )
            .crossfade(crossfade)
            .size(size)
            .build()
    }

    val fallbackPainter = painterResource(defaultCoverRes(albumId))

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        placeholder = fallbackPainter,
        error = fallbackPainter,
        fallback = fallbackPainter,
    )
}
