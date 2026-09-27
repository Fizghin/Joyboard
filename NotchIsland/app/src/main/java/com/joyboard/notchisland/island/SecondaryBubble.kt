package com.joyboard.notchisland.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.joyboard.notchisland.R
import androidx.core.view.isVisible

/**
 * The small circle that sits beside the compact island when two long-lived activities are
 * running at once — music and a timer, say — the way the Dynamic Island splits in two. It shows
 * the second activity's picture; a tap swaps it to the front.
 */
@SuppressLint("ViewConstructor")
class SecondaryBubble(context: Context, onTap: () -> Unit) : FrameLayout(context) {

    private val fill = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.BLACK)
    }
    private val icon = ImageView(context)

    /** What it is showing, so a render that changes nothing does not replay the entrance. */
    private var shownKey: String? = null

    init {
        background = fill
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
        }
        addView(icon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isClickable = true
        setOnClickListener { onTap() }
        visibility = GONE
    }

    /**
     * Shows [p] in the bubble, or hides it for null.
     * @param surface the island's own colour, so the two read as one piece
     */
    fun bind(p: Presentation?, surface: Int, animate: Boolean) {
        if (p == null) {
            shownKey = null
            if (visibility != GONE) {
                animate().cancel()
                visibility = GONE
            }
            return
        }
        fill.setColor(surface)
        val key = "${p.kind}|${p.title}|${p.leadingBitmap?.generationId}|${p.leadingTint}"
        if (key == shownKey && isVisible) return
        val appearing = !isVisible
        shownKey = key
        contentDescription = context.getString(R.string.show_activity, p.title ?: p.kind.name.lowercase())

        val size = layoutParams?.width?.takeIf { it > 0 } ?: 0
        when {
            p.leadingBitmap != null -> {
                icon.setImageBitmap(p.leadingBitmap)
                icon.scaleType = ImageView.ScaleType.CENTER_CROP
                icon.clearColorFilter()
                icon.setPadding(0, 0, 0, 0)
            }
            else -> {
                icon.setImageDrawable(p.leadingIcon)
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                // A glyph is tinted and inset; an app's own icon keeps its colours.
                p.leadingTint?.let { icon.setColorFilter(it) } ?: icon.clearColorFilter()
                val pad = if (p.leadingTint != null) size / 4 else size / 6
                icon.setPadding(pad, pad, pad, pad)
            }
        }
        visibility = VISIBLE
        if (appearing && animate) {
            scaleX = 0.4f
            scaleY = 0.4f
            alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                .setInterpolator(OvershootInterpolator(2f)).start()
        }
    }
}
