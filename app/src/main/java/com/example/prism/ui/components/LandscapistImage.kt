package com.example.prism.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kmpalette.palette.graphics.Palette
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppSurface3

/**
 * Landscapist-powered image composable for use in detail/player screens.
 *
 * ## Loading state
 * Shows an animated [ShimmerPlugin] with colors matching the app's dark palette
 * ([AppSurface2] base, [AppSurface3] highlight). This replaces the previous behaviour
 * where the default cover drawable was displayed momentarily while the real artwork
 * was being decoded — the shimmer is visually neutral and signals "loading" clearly.
 *
 * ## Error state
 * Only when artwork is definitively unavailable ([errorRes] != null) does the fallback
 * default-cover drawable appear via [PlaceholderPlugin.Failure].
 *
 * Designed to avoid subcomposition overhead in fast-scrolling lists; adopt this composable
 * only in hero, player, and detail-screen contexts where a richer loading animation
 * is appropriate. For list rows use [SongArtwork] / [AlbumArtwork] with Coil [AsyncImage].
 */
@Composable
fun PrismCoilImage(
    imageModel: () -> Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderRes: Int? = null,
    errorRes: Int? = null,
    crossfadeDuration: Int = 350,
    contentScale: ContentScale = ContentScale.Crop,
    onPaletteLoaded: ((Palette) -> Unit)? = null,
    onPrimaryColorExtracted: ((Color) -> Unit)? = null,
) {
    val currentOnPaletteLoaded by rememberUpdatedState(onPaletteLoaded)
    val currentOnColorExtracted by rememberUpdatedState(onPrimaryColorExtracted)

    val modelValue = imageModel()
    if (modelValue is coil3.request.ImageRequest) {
        CoilImage(
            imageRequest = { modelValue },
            modifier = modifier,
            imageOptions = ImageOptions(
                contentScale = contentScale,
                contentDescription = contentDescription,
            ),
            component = rememberImageComponent {
                if (crossfadeDuration > 0) {
                    +CrossfadePlugin(duration = crossfadeDuration)
                }

                if (placeholderRes != null) {
                    +PlaceholderPlugin.Loading(
                        source = painterResource(placeholderRes)
                    )
                } else {
                    +ShimmerPlugin(
                        shimmer = Shimmer.Flash(
                            baseColor = AppSurface2,
                            highlightColor = AppSurface3,
                        )
                    )
                }

                if (errorRes != null) {
                    +PlaceholderPlugin.Failure(
                        source = painterResource(errorRes)
                    )
                }

                if (currentOnPaletteLoaded != null || currentOnColorExtracted != null) {
                    +PalettePlugin { palette ->
                        currentOnPaletteLoaded?.invoke(palette)
                        currentOnColorExtracted?.let { callback ->
                            val swatch = palette.dominantSwatch
                                ?: palette.vibrantSwatch
                                ?: palette.darkVibrantSwatch
                                ?: palette.lightVibrantSwatch
                            swatch?.rgb?.let { rgb ->
                                callback(Color(rgb))
                            }
                        }
                    }
                }
            },
            failure = {
                if (errorRes != null) {
                    Image(
                        painter = painterResource(errorRes),
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    } else {
        CoilImage(
            imageModel = imageModel,
            modifier = modifier,
            imageOptions = ImageOptions(
                contentScale = contentScale,
                contentDescription = contentDescription,
            ),
            component = rememberImageComponent {
                if (crossfadeDuration > 0) {
                    +CrossfadePlugin(duration = crossfadeDuration)
                }

                if (placeholderRes != null) {
                    +PlaceholderPlugin.Loading(
                        source = painterResource(placeholderRes)
                    )
                } else {
                    +ShimmerPlugin(
                        shimmer = Shimmer.Flash(
                            baseColor = AppSurface2,
                            highlightColor = AppSurface3,
                        )
                    )
                }

                if (errorRes != null) {
                    +PlaceholderPlugin.Failure(
                        source = painterResource(errorRes)
                    )
                }

                if (currentOnPaletteLoaded != null || currentOnColorExtracted != null) {
                    +PalettePlugin { palette ->
                        currentOnPaletteLoaded?.invoke(palette)
                        currentOnColorExtracted?.let { callback ->
                            val swatch = palette.dominantSwatch
                                ?: palette.vibrantSwatch
                                ?: palette.darkVibrantSwatch
                                ?: palette.lightVibrantSwatch
                            swatch?.rgb?.let { rgb ->
                                callback(Color(rgb))
                            }
                        }
                    }
                }
            },
            failure = {
                if (errorRes != null) {
                    Image(
                        painter = painterResource(errorRes),
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    }
}
