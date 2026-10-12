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

/**
 * Landscapist-powered image composable for use in detail/player screens.
 * Wraps CoilImage with consistent styling, crossfade animation, placeholder/error plugins,
 * and optional palette color extraction.
 *
 * Designed to avoid subcomposition overhead in fast-scrolling lists by only being adopted
 * in hero, player, and dialog preview contexts.
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
