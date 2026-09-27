package com.joyboard.notchisland.island

/**
 * When a low battery is worth saying so: at 15 %, again at 10 % and at 5 % — the way Android
 * itself warns — rather than on every battery broadcast in between, which arrive whenever the
 * temperature or voltage moves. Plugging in, or climbing back above the first step, starts over.
 */
class LowBatteryWatch(private val steps: List<Int> = STEPS) {

    /** The lowest step already warned about, or null when none has been. */
    private var warnedStep: Int? = null

    /** True when this reading should raise the warning. */
    fun update(level: Int, plugged: Boolean): Boolean {
        if (plugged || level > steps.first()) {
            warnedStep = null
            return false
        }
        val step = steps.filter { level <= it }.minOrNull() ?: return false
        val warned = warnedStep
        if (warned != null && step >= warned) return false
        warnedStep = step
        return true
    }

    companion object {
        val STEPS = listOf(15, 10, 5)
    }
}
