package com.example.prism.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class CoilBitmapLoaderTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val loader = CoilBitmapLoader(context, scope)

    @Test
    fun supportsMimeType_returnsTrueForImageMimeTypes() {
        assertTrue(loader.supportsMimeType("image/jpeg"))
        assertTrue(loader.supportsMimeType("image/png"))
        assertTrue(loader.supportsMimeType("image/webp"))
    }

    @Test
    fun supportsMimeType_returnsFalseForNonImageMimeTypes() {
        assertFalse(loader.supportsMimeType("audio/mpeg"))
        assertFalse(loader.supportsMimeType("video/mp4"))
        assertFalse(loader.supportsMimeType("application/json"))
    }
}
