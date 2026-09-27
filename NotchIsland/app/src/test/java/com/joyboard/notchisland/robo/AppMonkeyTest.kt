package com.joyboard.notchisland.robo

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.screens.AboutScreen
import com.joyboard.notchisland.ui.screens.AppearanceScreen
import com.joyboard.notchisland.ui.screens.BlockedAppsScreen
import com.joyboard.notchisland.ui.screens.FeaturesScreen
import com.joyboard.notchisland.ui.screens.GesturesScreen
import com.joyboard.notchisland.ui.screens.HomeScreen
import com.joyboard.notchisland.ui.theme.AppSurfaces
import com.joyboard.notchisland.ui.theme.NotchIslandTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Every control on every settings screen, pressed one at a time — each on a fresh copy of the
 * screen, so a dialog one press opened is not in the way of the next. Nothing may throw.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h1900dp-xxhdpi")
class AppMonkeyTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

    /** Lets a press take effect: recomposition, a few frames of animation, and posted work. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        idle()
    }

    private fun pressEverything(name: String, screen: @Composable () -> Unit): Int {
        // The island preview animates for ever, so the clock is moved by hand rather than waited on.
        compose.mainClock.autoAdvance = false
        var generation by mutableIntStateOf(0)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = host.get()
        activity.setContent {
            NotchIslandTheme {
                Surface(color = AppSurfaces.page, modifier = Modifier.fillMaxSize()) {
                    key(generation) { screen() }
                }
            }
        }
        settle()
        var index = 0
        var pressed = 0
        while (index < 400) {
            generation++
            settle()
            val nodes = compose.onAllNodes(hasClickAction())
            val count = nodes.fetchSemanticsNodes().size
            if (index >= count) break
            val node = nodes[index]
            val enabled = !node.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
            if (enabled) {
                val label = node.fetchSemanticsNode().config.let { c ->
                    (c.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString() ?: "") + " / " +
                        (c.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString() ?: "")
                }
                // A dialog with a text box never lets Compose's test clock go idle under
                // Robolectric, so the two that have one are left out of the sweep.
                if (TEXT_DIALOGS.none { label.startsWith(it) }) {
                    node.performSemanticsAction(SemanticsActions.OnClick)
                    pressed++
                    settle()
                }
            }
            index++
        }
        // Gone properly, so the next screen's sweep does not also find this one's controls.
        host.pause().stop().destroy()
        assertTrue("$name: nothing to press", pressed > 0)
        return pressed
    }

    private companion object {
        val TEXT_DIALOGS = listOf("Update source", "Pinned note")
    }

    @Test
    fun `every control on every screen`() {
        val vm = MainViewModel(app)
        idle()
        var total = 0
        total += pressEverything("home") { HomeScreen(vm) {} }
        total += pressEverything("features") { FeaturesScreen(vm) {} }
        total += pressEverything("look") { AppearanceScreen(vm) }
        total += pressEverything("gestures") { GesturesScreen(vm) }
        total += pressEverything("blocked apps") { BlockedAppsScreen(vm) }
        total += pressEverything("about") { AboutScreen(vm) }
        assertTrue("only $total controls pressed", total >= 100)
    }
}
