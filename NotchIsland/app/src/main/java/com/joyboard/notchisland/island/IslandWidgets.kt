package com.joyboard.notchisland.island

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.joyboard.notchisland.util.dp
import com.joyboard.notchisland.util.withAlpha

/** Small, stateless view factories shared by the island and its panels. */
internal class IslandWidgets(private val context: Context) {

    /** How strongly a button is drawn: the one thing to do, one of several, or out of the way. */
    enum class Emphasis { FILLED, TINTED, PLAIN }

    fun icon(res: Int) = ContextCompat.getDrawable(context, res)

    fun smallLabel(text: String) = TextView(context).apply {
        setTextColor(0x99FFFFFF.toInt())
        textSize = 11f
        this.text = text
    }

    /** A line of text under a heading: the detail an activity has beyond its title. */
    fun detail(text: String, color: Int = IslandColors.SECONDARY, size: Float = 13f) = TextView(context).apply {
        setTextColor(color)
        textSize = size
        this.text = text
    }

    fun spacer(size: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, 1)
    }

    /** A spacer that takes whatever width a row has left over. */
    fun flex() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }

    fun circleButton(
        iconRes: Int,
        size: Int,
        label: String? = null,
        onClick: () -> Unit,
    ): ImageView = circleButton(iconRes, size, label, Color.WHITE, IslandColors.FILL, onClick)

    /** A round button in its own colours — tinted for the main action, grey for the rest. */
    fun circleButton(
        iconRes: Int,
        size: Int,
        label: String?,
        foreground: Int,
        background: Int,
        onClick: () -> Unit,
    ): ImageView =
        ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageDrawable(icon(iconRes))
            contentDescription = label
            setColorFilter(foreground)
            val pad = size / 4
            setPadding(pad, pad, pad, pad)
            this.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(background)
            }
            isClickable = true
            setOnClickListener {
                press(this)
                onClick()
            }
        }

    /** A round button with a few characters on it instead of a glyph, such as "+1". */
    fun textCircleButton(
        text: String,
        size: Int,
        label: String,
        foreground: Int,
        background: Int,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, size)
        this.text = text
        contentDescription = label
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = MEDIUM
        setTextColor(foreground)
        this.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(background)
        }
        isClickable = true
        setOnClickListener {
            press(this)
            onClick()
        }
    }

    fun pillButton(
        label: String,
        accent: Int,
        filled: Boolean = false,
        onClick: () -> Unit,
    ): TextView = pillButton(label, accent, if (filled) Emphasis.FILLED else Emphasis.PLAIN, onClick)

    fun pillButton(
        label: String,
        accent: Int,
        emphasis: Emphasis,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        textSize = 13f
        typeface = MEDIUM
        maxLines = 1
        setTextColor(
            when (emphasis) {
                Emphasis.FILLED -> Color.BLACK
                Emphasis.TINTED -> accent
                Emphasis.PLAIN -> Color.WHITE
            }
        )
        minHeight = 34.dp
        setPadding(15.dp, 0, 15.dp, 0)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = 17f.dp
            setColor(
                when (emphasis) {
                    Emphasis.FILLED -> accent
                    Emphasis.TINTED -> accent.withAlpha(0.2f)
                    Emphasis.PLAIN -> IslandColors.FILL
                }
            )
        }
        isClickable = true
        setOnClickListener {
            press(this)
            onClick()
        }
    }

    fun roundOutline(radius: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    /**
     * A slider drawn the way the island's own are: a thick rounded track that fills with the
     * accent, and no knob — the whole bar is the handle.
     */
    fun styleSlider(seek: SeekBar, accent: Int) {
        val thickness = 6.dp
        val track = GradientDrawable().apply {
            cornerRadius = thickness / 2f
            setColor(IslandColors.FILL)
        }
        val fill = GradientDrawable().apply {
            cornerRadius = thickness / 2f
            setColor(accent)
        }
        seek.progressDrawable = LayerDrawable(
            arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL))
        ).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            for (i in 0 until numberOfLayers) {
                setLayerHeight(i, thickness)
                setLayerGravity(i, Gravity.CENTER_VERTICAL or Gravity.FILL_HORIZONTAL)
            }
        }
        seek.thumb = null
        seek.splitTrack = false
        // Tall enough to hit comfortably, though only the track is drawn.
        seek.setPadding(0, 10.dp, 0, 10.dp)
    }

    fun sliderRow(
        iconRes: Int,
        value: Int,
        max: Int,
        accent: Int,
        trailing: View? = null,
        label: String? = null,
        onChange: (Int) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
        }
        row.addView(ImageView(context).apply {
            setImageDrawable(icon(iconRes))
            setColorFilter(IslandColors.SECONDARY)
            layoutParams = LinearLayout.LayoutParams(18.dp, 18.dp)
        })
        row.addView(SeekBar(context).apply {
            this.max = max.coerceAtLeast(1)
            progress = value.coerceIn(0, max)
            contentDescription = label
            styleSlider(this, accent)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 12.dp }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        })
        trailing?.let { row.addView(it) }
        return row
    }

    private fun press(view: View) {
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }.start()
    }

    companion object {
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

        /** Digits that keep their width, so a running clock does not jitter. */
        const val TABULAR = "tnum"
    }
}
