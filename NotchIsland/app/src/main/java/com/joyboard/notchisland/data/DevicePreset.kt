package com.joyboard.notchisland.data

import com.joyboard.notchisland.island.Hole

enum class HoleShape(val label: String) {
    PUNCH_HOLE("Punch hole"),
    PILL("Pill cutout"),
    WATERDROP("Waterdrop notch"),
}

/** Which edge the hole's position is measured from. */
enum class HoleAnchor { CENTER, LEFT, RIGHT }

/**
 * Where a phone's camera sits. What is reliable here is the placement — centred, top-left,
 * top-right — and the shape. The sizes are close starting points rather than measurements, so
 * the island nudges them into place with *Detect from this phone* or the calibrator, which read
 * the exact cutout from the hardware itself.
 *
 * Positions against an edge are stored as an inset rather than a screen coordinate, because the
 * same phone family ships in different widths.
 */
data class DevicePreset(
    val id: String,
    val maker: String,
    val name: String,
    /** Exact Build.MODEL values; a trailing '*' matches a prefix, for regional model codes. */
    val models: List<String>,
    val shape: HoleShape,
    val anchor: HoleAnchor,
    /** For LEFT or RIGHT: dp from that edge to the hole's centre. Ignored for CENTER. */
    val inset: Int = 0,
    /** dp from the top of the screen to the hole's centre. */
    val holeTop: Int,
    val holeWidth: Int,
    val holeHeight: Int = holeWidth,
) {
    fun hole(screenWidthDp: Float): Hole = Hole(
        centerX = when (anchor) {
            HoleAnchor.CENTER -> 0f
            HoleAnchor.LEFT -> -(screenWidthDp / 2f - inset)
            HoleAnchor.RIGHT -> screenWidthDp / 2f - inset
        },
        centerY = holeTop.toFloat(),
        width = holeWidth.toFloat(),
        height = holeHeight.toFloat(),
    )

    fun matches(model: String): Boolean = models.any { pattern ->
        if (pattern.endsWith("*")) model.startsWith(pattern.dropLast(1), ignoreCase = true)
        else model.equals(pattern, ignoreCase = true)
    }

    /**
     * The settings this phone implies. A centred camera gets an island wrapped around it; a
     * corner camera leaves the island centred on the screen, where it would be on an iPhone, and
     * simply keeps content away from the hole should the two ever meet.
     */
    fun applyTo(settings: IslandSettings, screenWidthDp: Float): IslandSettings =
        settings.fittedTo(hole(screenWidthDp), CameraSource.PRESET).copy(devicePresetId = id)
}

/**
 * Records the hole and shapes the island to it. A camera near the middle gets the island wrapped
 * around it, iPhone-style. A corner camera does not drag the island to the corner: the island
 * stays centred, where people expect it, and just keeps its content clear should they meet.
 */
fun IslandSettings.fittedTo(hole: Hole, source: CameraSource): IslandSettings {
    val recorded = withHole(hole, source)
    if (kotlin.math.abs(hole.centerX) > CENTRED_WITHIN_DP) return recorded
    val height = (hole.height + 12f).toInt().coerceIn(24, 60)
    return recorded.copy(
        collapsedHeight = height,
        collapsedWidth = maxOf(112, (hole.width + 88f).toInt()),
        cornerRadius = height / 2,
        offsetX = hole.centerX.toInt(),
        offsetY = (hole.centerY - height / 2f).toInt().coerceAtLeast(0),
        positionMode = PositionMode.OVERLAP_STATUS_BAR,
        preset = IslandPreset.CUSTOM,
    )
}

/** How far off centre a camera can be and still have the island wrapped around it. */
const val CENTRED_WITHIN_DP = 40f

object DevicePresets {

    private fun centred(id: String, maker: String, name: String, models: List<String>, top: Int, size: Int) =
        DevicePreset(id, maker, name, models, HoleShape.PUNCH_HOLE, HoleAnchor.CENTER, 0, top, size)

    private fun leftHole(id: String, maker: String, name: String, models: List<String>, inset: Int, top: Int, size: Int) =
        DevicePreset(id, maker, name, models, HoleShape.PUNCH_HOLE, HoleAnchor.LEFT, inset, top, size)

    val all: List<DevicePreset> = listOf(
        // ---- Google ----
        centred("pixel6", "Google", "Pixel 6", listOf("Pixel 6"), 24, 24),
        centred("pixel6pro", "Google", "Pixel 6 Pro", listOf("Pixel 6 Pro"), 24, 24),
        centred("pixel6a", "Google", "Pixel 6a", listOf("Pixel 6a"), 24, 24),
        centred("pixel7", "Google", "Pixel 7", listOf("Pixel 7"), 23, 23),
        centred("pixel7pro", "Google", "Pixel 7 Pro", listOf("Pixel 7 Pro"), 22, 22),
        centred("pixel7a", "Google", "Pixel 7a", listOf("Pixel 7a"), 23, 23),
        centred("pixel8", "Google", "Pixel 8", listOf("Pixel 8"), 22, 22),
        centred("pixel8pro", "Google", "Pixel 8 Pro", listOf("Pixel 8 Pro"), 22, 22),
        centred("pixel8a", "Google", "Pixel 8a", listOf("Pixel 8a"), 23, 23),
        centred("pixel9", "Google", "Pixel 9", listOf("Pixel 9"), 22, 22),
        centred("pixel9pro", "Google", "Pixel 9 Pro", listOf("Pixel 9 Pro"), 22, 22),
        centred("pixel9proxl", "Google", "Pixel 9 Pro XL", listOf("Pixel 9 Pro XL"), 22, 22),
        centred("pixel9a", "Google", "Pixel 9a", listOf("Pixel 9a"), 23, 23),
        leftHole("pixel4a", "Google", "Pixel 4a", listOf("Pixel 4a"), 30, 24, 24),
        leftHole("pixel5", "Google", "Pixel 5", listOf("Pixel 5"), 30, 24, 24),
        leftHole("pixel5a", "Google", "Pixel 5a", listOf("Pixel 5a"), 30, 24, 24),

        // ---- Samsung: model codes are prefixes because every region gets a different suffix ----
        centred("s23fe", "Samsung", "Galaxy S23 FE", listOf("SM-S711*"), 20, 20),
        centred("s23", "Samsung", "Galaxy S23", listOf("SM-S911*"), 18, 18),
        centred("s23plus", "Samsung", "Galaxy S23+", listOf("SM-S916*"), 18, 18),
        centred("s23ultra", "Samsung", "Galaxy S23 Ultra", listOf("SM-S918*"), 18, 18),
        centred("s24", "Samsung", "Galaxy S24", listOf("SM-S921*"), 18, 18),
        centred("s24plus", "Samsung", "Galaxy S24+", listOf("SM-S926*"), 18, 18),
        centred("s24ultra", "Samsung", "Galaxy S24 Ultra", listOf("SM-S928*"), 18, 18),
        centred("s24fe", "Samsung", "Galaxy S24 FE", listOf("SM-S721*"), 20, 20),
        centred("s25", "Samsung", "Galaxy S25", listOf("SM-S931*"), 18, 18),
        centred("s25ultra", "Samsung", "Galaxy S25 Ultra", listOf("SM-S938*"), 18, 18),
        centred("s22", "Samsung", "Galaxy S22", listOf("SM-S901*"), 18, 18),
        centred("s22ultra", "Samsung", "Galaxy S22 Ultra", listOf("SM-S908*"), 18, 18),
        centred("s21", "Samsung", "Galaxy S21", listOf("SM-G991*"), 18, 18),
        centred("s21fe", "Samsung", "Galaxy S21 FE", listOf("SM-G990*"), 20, 20),
        centred("a52", "Samsung", "Galaxy A52", listOf("SM-A525*", "SM-A526*", "SM-A528*"), 20, 20),
        centred("a53", "Samsung", "Galaxy A53", listOf("SM-A536*"), 20, 20),
        centred("a54", "Samsung", "Galaxy A54", listOf("SM-A546*"), 20, 20),
        centred("a55", "Samsung", "Galaxy A55", listOf("SM-A556*"), 20, 20),

        // ---- Honor ----
        centred("honorx9b", "Honor", "Honor X9b 5G", listOf("ALI-NX1", "ALI-NX3"), 22, 22),
        centred("honorx9a", "Honor", "Honor X9a", listOf("RMO-NX1"), 22, 22),
        DevicePreset(
            "honormagic6pro", "Honor", "Honor Magic6 Pro", emptyList(),
            HoleShape.PILL, HoleAnchor.CENTER, 0, holeTop = 22, holeWidth = 62, holeHeight = 22,
        ),

        // ---- OnePlus ----
        leftHole("oneplus9", "OnePlus", "OnePlus 9", listOf("LE2110", "LE2111", "LE2113", "LE2115", "LE2117"), 30, 22, 22),
        leftHole("oneplus9pro", "OnePlus", "OnePlus 9 Pro", listOf("LE2120", "LE2121", "LE2123", "LE2125", "LE2127"), 30, 22, 22),
        leftHole("oneplus10pro", "OnePlus", "OnePlus 10 Pro", listOf("NE2210", "NE2211", "NE2213", "NE2215", "NE2217"), 30, 22, 22),
        leftHole("oneplus11", "OnePlus", "OnePlus 11", listOf("CPH2447", "CPH2449", "CPH2451", "PHB110"), 30, 22, 22),
        leftHole("oneplus12", "OnePlus", "OnePlus 12", listOf("CPH2573", "CPH2581", "CPH2583", "PJD110"), 30, 22, 22),

        // ---- Nothing ----
        leftHole("nothing1", "Nothing", "Phone (1)", listOf("A063"), 30, 22, 22),
        leftHole("nothing2", "Nothing", "Phone (2)", listOf("A065"), 30, 22, 22),
        centred("nothing2a", "Nothing", "Phone (2a)", listOf("A142", "A142P"), 22, 22),

        // ---- Xiaomi and Motorola: model codes vary too widely to match, so choose by name ----
        centred("xiaomi13", "Xiaomi", "Xiaomi 13 / 14", emptyList(), 20, 20),
        centred("redminote", "Xiaomi", "Redmi Note 12 / 13", emptyList(), 20, 20),
        centred("pocof5", "Xiaomi", "Poco F5", emptyList(), 20, 20),
        centred("motoedge", "Motorola", "Moto Edge 40 / 50", emptyList(), 20, 20),

        // ---- For everything else: pick the shape, then detect or calibrate ----
        centred("generic_centre", "Any phone", "Centred punch hole", emptyList(), 22, 22),
        leftHole("generic_left", "Any phone", "Top-left punch hole", emptyList(), 30, 22, 22),
        DevicePreset(
            "generic_right", "Any phone", "Top-right punch hole", emptyList(),
            HoleShape.PUNCH_HOLE, HoleAnchor.RIGHT, inset = 30, holeTop = 22, holeWidth = 22,
        ),
        DevicePreset(
            "generic_waterdrop", "Any phone", "Waterdrop notch", emptyList(),
            HoleShape.WATERDROP, HoleAnchor.CENTER, 0, holeTop = 13, holeWidth = 34, holeHeight = 26,
        ),
    )

    fun byId(id: String?): DevicePreset? = all.firstOrNull { it.id == id }

    /**
     * The preset for the phone this is running on, if one is known. Exact names are checked
     * before prefixes, so "Pixel 6 Pro" is not claimed by "Pixel 6".
     */
    fun match(model: String): DevicePreset? =
        all.firstOrNull { preset -> preset.models.any { !it.endsWith("*") && it.equals(model, true) } }
            ?: all.firstOrNull { it.matches(model) }
}
