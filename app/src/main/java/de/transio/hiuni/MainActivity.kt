package de.transio.hiuni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.core.design.HiUniTheme
import de.transio.hiuni.navigation.AppNavGraph
import de.transio.hiuni.ui.responsive.AdaptiveScaffold

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HiUniTheme {
                val navController = rememberNavController()
                val windowSize = calculateWindowSizeClass(this)
                AdaptiveScaffold(
                    navController = navController,
                    windowSizeClass = windowSize
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        AppNavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
