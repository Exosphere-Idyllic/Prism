package com.example.prism

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.prism.core.util.StartupMetrics
import com.example.prism.navigation.MainNavigation
import com.example.prism.ui.theme.PrismTheme
import org.koin.compose.KoinContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        StartupMetrics.mark("MainActivity.onCreate done")  // ← Phase 6

        enableEdgeToEdge()
        setContent {
            StartupMetrics.mark("setContent entry")        // ← Phase 7
            KoinContext {
                PrismTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MainNavigation()
                        StartupMetrics.mark("first composition") // ← Phase 8
                        // LaunchedEffect fires after the first frame is committed to the display.
                        LaunchedEffect(Unit) {
                            StartupMetrics.mark("first frame drawn")
                            StartupMetrics.dump()           // ← Final table
                        }
                    }
                }
            }
        }
    }
}
