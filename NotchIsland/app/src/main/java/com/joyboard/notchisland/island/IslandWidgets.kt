package com.joyboard.notchisland.island

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
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

/** Small, stateless view factories shared by the island and its panels. */
internal class IslandWidgets(private val context: Context) {

    fun icon(res: Int) = ContextCompat.getDrawable(context, res)

    fun smallLabel(text: String) = TextView(context).apply {
        setTextColor(0x99FFFFFF.toInt())
        textSize = 11f
        this.text = text
    }

    fun spacer(size: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, 1)
    }

    fun circleButton(
        iconRes: Int,
        size: Int,
        label: String? = null,
        onClick: () -> Unit,
    ): ImageView =
        ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageDrawable(icon(iconRes))
            contentDescription = label
            setColorFilter(Color.WHITE)
            val pad = size / 4
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x1FFFFFFF)
            }
            isClickable = true
            setOnClickListener {
                animate().scaleX(0.86f).scaleY(0.86f).setDuration(90).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
                onClick()
            }
        }

    fun pillButton(
        label: String,
        accent: Int,
        filled: Boolean = false,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        textSize = 12f
        setTextColor(if (filled) Color.BLACK else Color.WHITE)
        setPadding(14.dp, 8.dp, 14.dp, 8.dp)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = 16f.dp
            setColor(if (filled) accent else 0x1FFFFFFF)
        }
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun roundOutline(radius: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    fun sliderRow(
        iconRes: Int,
        value: Int,
        max: Int,
        accent: Int,
        onChange: (Int) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
        }
        row.addView(ImageView(context).apply {
            setImageDrawable(icon(iconRes))
            setColorFilter(0xCCFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(18.dp, 18.dp)
        })
        row.addView(SeekBar(context).apply {
            this.max = max.coerceAtLeast(1)
            progress = value.coerceIn(0, max)
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x4DFFFFFF)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 10.dp }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        })
        return row
    }
}
