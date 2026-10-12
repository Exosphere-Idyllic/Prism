package com.example.prism.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin

/**
 * Landscapist-powered image composable for use in detail/player screens.
 * Wraps CoilImage with consistent styling, crossfade animation, and placeholder/error plugins.
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
) {
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
