package com.joyboard.notchisland.data

/**
 * Exact geometry for the hardware each preset imitates. iOS points and Android dp are both
 * roughly a 160th of an inch, so the numbers carry across one to one.
 */
enum class IslandPreset(
    val label: String,
    val collapsedWidth: Int,
    val collapsedHeight: Int,
    val cornerRadius: Int,
    val offsetY: Int,
    val compactWidth: Int,
    val expandedWidth: Int,
) {
    CUSTOM("Custom", 118, 30, 18, 4, 190, 330),

    /** 126 × 37 pt housing, 11 pt below the top edge. */
    IPHONE_14_PRO("iPhone 14 Pro / 15 Pro", 126, 37, 19, 11, 206, 371),
    IPHONE_14_PRO_MAX("iPhone 14 Pro Max / 15 Pro Max", 126, 37, 19, 11, 212, 391),

    /** The 16 Pro housing is fractionally shorter against a taller display. */
    IPHONE_16_PRO("iPhone 16 Pro", 125, 36, 18, 13, 208, 378),
    IPHONE_16_PRO_MAX("iPhone 16 Pro Max", 125, 36, 18, 13, 214, 398),

    /** A centred punch-hole camera, the common Android shape. */
    ANDROID_PUNCH_HOLE("Android punch-hole", 96, 28, 14, 6, 180, 330);

    /** The settings this preset implies, on top of whatever is already configured. */
    fun applyTo(settings: IslandSettings): IslandSettings = settings.copy(
        preset = this,
        collapsedWidth = collapsedWidth,
        collapsedHeight = collapsedHeight,
        cornerRadius = cornerRadius,
        offsetY = offsetY,
        compactWidth = compactWidth,
        expandedWidth = expandedWidth,
        mediumWidth = ((compactWidth + expandedWidth) / 2),
        positionMode = PositionMode.OVERLAP_STATUS_BAR,
        offsetX = 0,
    )

    val isApple: Boolean
        get() = this != CUSTOM && this != ANDROID_PUNCH_HOLE
}
