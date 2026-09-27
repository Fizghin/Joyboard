package com.joyboard.notchisland.island

/**
 * When a hot battery is worth saying so: once as it crosses into hot, and again only after it has
 * cooled well below that. A battery hovering at the line would otherwise warn on every reading.
 */
class HeatWatch(private val hotC: Float = HOT_C, private val coolC: Float = COOL_C) {

    private var warned = false

    /** True when this reading should raise the warning. */
    fun update(temperatureC: Float?): Boolean {
        val t = temperatureC ?: return false
        if (warned) {
            if (t <= coolC) warned = false
            return false
        }
        if (t >= hotC) {
            warned = true
            return true
        }
        return false
    }

    companion object {
        /** Android's own charging safeguards start around here. */
        const val HOT_C = 45f
        const val COOL_C = 42f
    }
}
