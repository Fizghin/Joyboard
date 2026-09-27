package com.joyboard.notchisland.robo

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import com.joyboard.notchisland.ui.theme.AppSurfaces
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.joyboard.notchisland.MainActivity
import com.joyboard.notchisland.data.ThemeMode
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.screens.AboutScreen
import com.joyboard.notchisland.ui.screens.AppearanceScreen
import com.joyboard.notchisland.ui.screens.FeaturesScreen
import com.joyboard.notchisland.ui.screens.GesturesScreen
import com.joyboard.notchisland.ui.screens.HomeScreen
import com.joyboard.notchisland.ui.theme.NotchIslandTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Renders the settings app's screens, so how the app looks can be seen and compared, not just
 * how the island does. Tall viewports catch most of each scrolling screen in one image.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h1900dp-xxhdpi")
class AppScreenshotTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    private fun render(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            NotchIslandTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                // What the app's Scaffold provides: the page colour, and text colour to match.
                Surface(color = AppSurfaces.page, modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        idle()
        activity.window.decorView.captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    @Test
    fun screens() {
        val vm = MainViewModel(app)
        idle()
        for (dark in listOf(false, true)) {
            val suffix = if (dark) "dark" else "light"
            render("home_$suffix", dark) { HomeScreen(vm) {} }
            render("features_$suffix", dark) { FeaturesScreen(vm) {} }
            render("look_$suffix", dark) { AppearanceScreen(vm) }
            render("gestures_$suffix", dark) { GesturesScreen(vm) }
            render("about_$suffix", dark) { AboutScreen(vm) }
        }
    }

    @Test
    fun wholeApp() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        activity.window.decorView.captureRoboImage("build/outputs/roborazzi/app/main_activity.png")
    }
}
