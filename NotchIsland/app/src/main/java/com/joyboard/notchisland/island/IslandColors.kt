package com.joyboard.notchisland.island

/**
 * Colours that mean something — green for charging, red for low battery, orange for a timer —
 * in Apple's dark-mode system palette, since they sit on the island's black. An activity drawn
 * in one of these keeps it whichever accent is chosen, because the colour is the message.
 */
internal object IslandColors {
    const val GREEN = 0xFF30D158.toInt()
    const val RED = 0xFFFF453A.toInt()
    const val ORANGE = 0xFFFF9F0A.toInt()
    const val AMBER = 0xFFFFD60A.toInt()
    const val CYAN = 0xFF64D2FF.toInt()
    const val PURPLE = 0xFF7D7AFF.toInt()
    const val GRAY = 0xFF8E8E93.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    /** Text and glyphs that support rather than lead. */
    const val SECONDARY = 0xA6FFFFFF.toInt()

    /** The fill behind a neutral button or an empty track. */
    const val FILL = 0x26FFFFFF
}
