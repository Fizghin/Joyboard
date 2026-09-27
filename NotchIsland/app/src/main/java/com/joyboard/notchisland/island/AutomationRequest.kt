package com.joyboard.notchisland.island

import com.joyboard.notchisland.R

/**
 * A message another app — Tasker, Automate, a shortcut — asked the island to show, already
 * cleaned up: lengths are capped, the duration is kept within reason, and anything malformed is
 * dropped rather than trusted.
 */
data class AutomationRequest(
    val title: String,
    val text: String?,
    val durationMs: Long,
    val color: Int?,
    val iconRes: Int,
    val expand: Boolean,
) {
    companion object {
        const val ACTION_SHOW = "com.joyboard.notchisland.action.SHOW"
        const val ACTION_DISMISS = "com.joyboard.notchisland.action.DISMISS"

        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_COLOR = "color"
        const val EXTRA_ICON = "icon"
        const val EXTRA_EXPAND = "expand"

        const val MAX_TITLE = 80
        const val MAX_TEXT = 240
        const val DEFAULT_SECONDS = 5L
        const val MAX_SECONDS = 600L

        /** The icons a request can name, so no app can point the island at arbitrary resources. */
        val ICONS: Map<String, Int> = mapOf(
            "bell" to R.drawable.ic_bell,
            "timer" to R.drawable.ic_timer,
            "music" to R.drawable.ic_music,
            "calendar" to R.drawable.ic_calendar,
            "battery" to R.drawable.ic_battery_full,
            "wifi" to R.drawable.ic_wifi,
            "bluetooth" to R.drawable.ic_bluetooth,
            "torch" to R.drawable.ic_torch,
            "island" to R.drawable.ic_island,
        )

        /**
         * Reads a request from intent extras, looked up through [extra] so this stays testable
         * without an Intent. Null when there is no usable title.
         */
        fun parse(extra: (String) -> Any?): AutomationRequest? {
            val title = (extra(EXTRA_TITLE) as? CharSequence)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return null
            val text = (extra(EXTRA_TEXT) as? CharSequence)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val seconds = when (val raw = extra(EXTRA_DURATION)) {
                is Number -> raw.toLong()
                is CharSequence -> raw.toString().trim().toLongOrNull()
                else -> null
            } ?: DEFAULT_SECONDS
            return AutomationRequest(
                title = title.take(MAX_TITLE),
                text = text?.take(MAX_TEXT),
                durationMs = seconds.coerceIn(1, MAX_SECONDS) * 1000,
                color = parseColor(extra(EXTRA_COLOR)),
                iconRes = ICONS[(extra(EXTRA_ICON) as? CharSequence)?.toString()?.trim()?.lowercase()]
                    ?: R.drawable.ic_bell,
                expand = when (val raw = extra(EXTRA_EXPAND)) {
                    is Boolean -> raw
                    is CharSequence -> raw.toString().trim().equals("true", ignoreCase = true)
                    else -> false
                },
            )
        }

        /** An int colour, or "#RRGGBB" / "#AARRGGBB"; anything else is ignored. Always opaque. */
        fun parseColor(raw: Any?): Int? {
            val value = when (raw) {
                is Int -> raw.toLong() and 0xFFFFFFFFL
                is Long -> raw and 0xFFFFFFFFL
                is CharSequence -> {
                    val hex = raw.toString().trim().removePrefix("#")
                    if (hex.length != 6 && hex.length != 8) return null
                    hex.toLongOrNull(16) ?: return null
                }
                else -> return null
            }
            return (value or 0xFF000000L).toInt()
        }
    }
}
