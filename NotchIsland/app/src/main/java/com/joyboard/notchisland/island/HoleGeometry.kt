package com.joyboard.notchisland.island

/**
 * The phone's camera cutout, in screen terms. It is hardware: it never moves, so it is stored
 * against the screen rather than the island, and the island arranges itself around it.
 *
 * @param centerX dp from the horizontal centre of the screen; negative is left of centre
 * @param centerY dp from the top edge of the screen
 */
data class Hole(val centerX: Float, val centerY: Float, val width: Float, val height: Float) {
    val isRound: Boolean get() = width > 0f && height > 0f && width / height in 0.75f..1.33f
}

/** A cutout expressed in the island's own coordinates: dp from its left and top edges. */
data class LocalHole(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
}

/** Extra padding content needs to stay clear of the cutout, on top of the usual padding. */
data class Clearance(val start: Float = 0f, val end: Float = 0f, val top: Float = 0f) {
    companion object {
        val NONE = Clearance()
    }
}

/**
 * Keeps the island's content out from under the camera. The island is anchored at its top
 * centre, so the hole's position relative to that point is fixed whatever size the island is —
 * only the island's width changes where it lands in local terms.
 *
 * Pure arithmetic in dp, with no Android types, so every case can be tested.
 */
object HoleGeometry {

    /** Where the hole falls inside an island of [islandWidth], given its offset from the island's top centre. */
    fun locate(dxFromIslandCenter: Float, dyFromIslandTop: Float, hole: Hole, islandWidth: Float): LocalHole {
        val cx = islandWidth / 2f + dxFromIslandCenter
        return LocalHole(
            left = cx - hole.width / 2f,
            top = dyFromIslandTop - hole.height / 2f,
            right = cx + hole.width / 2f,
            bottom = dyFromIslandTop + hole.height / 2f,
        )
    }

    fun intersects(hole: LocalHole, islandWidth: Float, islandHeight: Float): Boolean =
        hole.right > 0f && hole.left < islandWidth && hole.bottom > 0f && hole.top < islandHeight

    /**
     * For a single row — the resting pill and the compact readout — with a leading block at the
     * start and a trailing block at the end. Whichever block the hole lands on is pushed past it;
     * a hole in the gap between them, which is where a centred camera sits, needs nothing.
     */
    fun rowClearance(
        hole: LocalHole,
        islandWidth: Float,
        islandHeight: Float,
        basePadding: Float,
        leadingWidth: Float,
        trailingWidth: Float,
        margin: Float,
    ): Clearance {
        if (!intersects(hole, islandWidth, islandHeight)) return Clearance.NONE
        val leadingEnd = basePadding + leadingWidth
        val trailingStart = islandWidth - basePadding - trailingWidth

        val hitsLeading = hole.left < leadingEnd + margin && hole.right > basePadding - margin
        val hitsTrailing = hole.right > trailingStart - margin && hole.left < islandWidth - basePadding + margin

        // A hole wide enough to touch both blocks is split by its centre rather than pushing
        // both blocks at once, which would leave nowhere for either.
        val (pushStart, pushEnd) = when {
            hitsLeading && hitsTrailing -> (hole.centerX < islandWidth / 2f) to (hole.centerX >= islandWidth / 2f)
            else -> hitsLeading to hitsTrailing
        }
        return Clearance(
            start = if (pushStart) (hole.right + margin - basePadding).coerceAtLeast(0f) else 0f,
            end = if (pushEnd) (islandWidth - hole.left + margin - basePadding).coerceAtLeast(0f) else 0f,
        )
    }

    /** For the open panel: content starts below the hole rather than running underneath it. */
    fun panelClearance(
        hole: LocalHole,
        islandWidth: Float,
        islandHeight: Float,
        basePaddingTop: Float,
        margin: Float,
    ): Clearance {
        if (!intersects(hole, islandWidth, islandHeight)) return Clearance.NONE
        return Clearance(top = (hole.bottom + margin - basePaddingTop).coerceAtLeast(0f))
    }
}
