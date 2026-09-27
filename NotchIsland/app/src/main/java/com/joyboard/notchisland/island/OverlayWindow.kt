package com.joyboard.notchisland.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.data.hole
import com.joyboard.notchisland.util.dp
import androidx.annotation.VisibleForTesting

/**
 * The overlay window the island lives in: attaching it, where it sits, the touch strip that
 * catches taps below the status bar, whether it can take the keyboard, and translating the
 * camera hole into the island's own coordinates.
 *
 * This is the window-manager plumbing, split out of IslandController so that the controller is
 * left with the decisions — what to show and when — and this is left with putting it on screen.
 */
internal class OverlayWindow(
    private val context: Context,
    private val listener: IslandView.Listener,
    /** A touch outside the island while it is open: the controller decides what that means. */
    private val onOutsideTouch: () -> Unit,
    /** The status bar appeared or went away — an app went full screen, or came back. */
    private val onStatusBarVisibility: (Boolean) -> Unit = {},
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var settings = IslandSettings()
    private var root: LinearLayout? = null
    private var touchStrip: FrameLayout? = null
    private var attached = false

    var island: IslandView? = null
        private set

    fun applySettings(next: IslandSettings) {
        settings = next
        island?.applySettings(next)
        refresh()
        updateParams()
    }

    /** The status bar can change height with the configuration, so measure it again. */
    fun invalidateInsets() {
        cachedStatusBar = 0
    }

    /** Whether the island is on screen right now. For tests. */
    @VisibleForTesting
    internal val isShowing: Boolean get() = root?.visibility == View.VISIBLE

    fun setVisible(visible: Boolean) {
        root?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun attach(initial: IslandSettings) {
        settings = initial
        if (attached) return
        val view = IslandView(context, listener)
        // The window wraps the island tightly, so anything landing in the container's padding or
        // in the touch strip was aimed at the island — forward it instead of dropping it.
        val container = object : LinearLayout(context) {
            // Clicks are detected by the island's own onTouchEvent, which calls performClick.
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    onOutsideTouch()
                    return false
                }
                return view.onTouchEvent(event)
            }

            // The container only widens the island's hit area; a click on it is a click on the
            // island, and screen readers are pointed at the island itself.
            override fun performClick(): Boolean {
                super.performClick()
                return view.performClick()
            }
        }.apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            isClickable = true
        }

        watchStatusBar(container)

        val strip = FrameLayout(context).apply {
            addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = 2f.dp
                        setColor(0x4DFFFFFF)
                    }
                },
                FrameLayout.LayoutParams(26.dp, 3.dp, Gravity.CENTER)
            )
        }

        container.addView(
            view,
            LinearLayout.LayoutParams(settings.collapsedWidth.dp, settings.collapsedHeight.dp)
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        container.addView(
            strip,
            LinearLayout.LayoutParams(STRIP_WIDTH.dp, 0)
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        runCatching {
            windowManager?.addView(container, buildParams())
            root = container
            island = view
            touchStrip = strip
            attached = true
            view.applySettings(settings)
            view.snapToMode(IslandMode.PILL)
            refresh()
        }
    }

    /**
     * Every window is told when the system bars come and go, overlays included, so a video or a
     * game hiding the status bar is visible from here without any extra permission.
     */
    private fun watchStatusBar(container: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
                onStatusBarVisibility(insets.isVisible(WindowInsetsCompat.Type.statusBars()))
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            container.setOnSystemUiVisibilityChangeListener { flags ->
                onStatusBarVisibility(flags and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
            }
        }
    }

    fun detach() {
        val current = root ?: return
        runCatching { windowManager?.removeViewImmediate(current) }
        root = null
        island = null
        touchStrip = null
        attached = false
    }

    private fun buildParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = settings.offsetX.dp
            y = windowY()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

    /**
     * Overlay windows are always layered below the system status bar, and the status bar
     * consumes every touch inside its own band. Where the window starts therefore decides how
     * much of the island can be touched at all.
     */
    private fun windowY(): Int = when (settings.positionMode) {
        PositionMode.BELOW_STATUS_BAR -> settings.offsetY.dp + statusBarHeight()
        PositionMode.OVERLAP_STATUS_BAR -> settings.offsetY.dp
        PositionMode.CUSTOM -> settings.offsetY.dp
    }

    private var cachedStatusBar = 0

    /**
     * Height of the system status bar — the band where touches never reach us. Read from the
     * platform's insets rather than the private status_bar_height resource, which is not an API
     * and is not guaranteed to exist. Cached, and cleared when the configuration changes.
     */
    private fun statusBarHeight(): Int {
        if (cachedStatusBar > 0) return cachedStatusBar
        val fromMetrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                windowManager?.currentWindowMetrics?.windowInsets
                    ?.getInsets(WindowInsets.Type.statusBars())?.top
            }.getOrNull() ?: 0
        } else {
            0
        }
        val fromWindow = root?.let {
            ViewCompat.getRootWindowInsets(it)
                ?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
                ?.top
        } ?: 0
        val measured = maxOf(fromMetrics, fromWindow)
        // 24dp is the Material status bar height, and what these releases draw without a cutout.
        return if (measured > 0) measured.also { cachedStatusBar = it } else 24.dp
    }

    /**
     * Sizes the transparent strip under the island. It is measured from the resting pill rather
     * than the live height, so expanding and collapsing never re-lays-out the window.
     */
    fun refresh() {
        val container = root ?: return
        val strip = touchStrip ?: return
        val overlap = settings.positionMode == PositionMode.OVERLAP_STATUS_BAR
        // Above the island is only ever the status bar's dead band except in the default anchor,
        // so the offsets mean exactly "island top" everywhere else — which is also what the
        // calibrator draws, so the two can be trusted to agree.
        val topPadding = if (settings.positionMode == PositionMode.BELOW_STATUS_BAR) TOUCH_PADDING.dp else 0
        if (container.paddingTop != topPadding) {
            container.setPadding(TOUCH_PADDING.dp, topPadding, TOUCH_PADDING.dp, TOUCH_PADDING.dp)
        }
        val height = if (!overlap) 0 else {
            val islandBottom = windowY() + settings.collapsedHeight.dp
            (statusBarHeight() + settings.touchStripHeight.dp - islandBottom).coerceAtLeast(0)
        }
        val params = strip.layoutParams
        if (params.height != height) {
            params.height = height
            strip.layoutParams = params
        }
        strip.getChildAt(0)?.visibility =
            if (overlap && settings.showTouchHint && height > 6.dp) View.VISIBLE else View.INVISIBLE
        pushHoleToIsland()
    }

    /**
     * Hands the camera hole to the island in the island's own terms: an offset from its top
     * centre. The island is centred at offsetX and its top edge is the window's top plus the
     * container padding, both of which are known here and nowhere else.
     */
    private fun pushHoleToIsland() {
        val view = island ?: return
        val hole = settings.hole
        if (hole == null) {
            view.setHole(null, 0f, 0f)
            return
        }
        val density = context.resources.displayMetrics.density
        val islandTopDp = (windowY() + (root?.paddingTop ?: 0)) / density
        view.setHole(hole, hole.centerX - settings.offsetX, hole.centerY - islandTopDp)
    }

    private fun baseFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    fun updateParams() {
        val current = root ?: return
        val params = current.layoutParams as? WindowManager.LayoutParams ?: return
        val x = settings.offsetX.dp
        val y = windowY()
        var flags = baseFlags()
        val dim: Float
        if (settings.dimBackgroundWhenExpanded && island?.mode == IslandMode.EXPANDED) {
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dim = 0.32f
        } else {
            dim = 0f
        }
        // updateViewLayout forces a relayout of the whole window, so only call it on a real change.
        if (params.x == x && params.y == y && params.flags == flags && params.dimAmount == dim) {
            return
        }
        params.x = x
        params.y = y
        params.flags = flags
        params.dimAmount = dim
        runCatching { windowManager?.updateViewLayout(current, params) }
    }

    /**
     * The overlay is unfocusable so it never steals input, but an inline reply needs the
     * keyboard, so focus is granted only while the field is being used.
     */
    fun setFocusable(focusable: Boolean) {
        val current = root ?: return
        val params = current.layoutParams as? WindowManager.LayoutParams ?: return
        val wantFlags = if (focusable) {
            (baseFlags() and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
        } else {
            baseFlags()
        }
        if (params.flags == wantFlags) return
        params.flags = wantFlags
        params.softInputMode = if (focusable) {
            // The keyboard rises from the bottom and the island lives at the top, so nothing
            // needs resizing — just ask for the keyboard.
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }
        if (focusable) params.dimAmount = 0.4f
        runCatching { windowManager?.updateViewLayout(current, params) }
        if (!focusable) {
            island?.clearReplyFocus()
            updateParams()
        }
    }

    private companion object {
        /** Slop around the island so a near miss still counts as a tap. */
        const val TOUCH_PADDING = 14

        /** Width of the transparent strip that catches taps in overlap mode. */
        const val STRIP_WIDTH = 96
    }
}
