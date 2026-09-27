package com.joyboard.notchisland.island

/** Directions, as read out of a maps app's notification. */
data class NavigationInfo(
    /** How far to the next turn — "250 m", "0.3 mi" — when the app says. */
    val distance: String?,
    /** What to do there: "Turn left onto High St". */
    val instruction: String,
    /** When you will arrive, or how long is left, when the app says. */
    val eta: String?,
)

/**
 * Reads directions out of a navigation notification. Apps lay them out differently — Google Maps
 * puts the distance in the title and the turn in the text, others put the turn first and the
 * distance after it — so this looks for a distance wherever it is and takes the rest as the turn.
 * Pure, so the rules are unit tested.
 */
object NavigationText {

    /** Maps apps whose ongoing notification is navigation even without the category set. */
    private val APPS = setOf(
        "com.google.android.apps.maps",
        "com.waze",
        "net.osmand",
        "net.osmand.plus",
        "app.organicmaps",
        "com.mapswithme.maps.pro",
        "com.here.app.maps",
        "com.sygic.aura",
        "com.tomtom.gplay.navapp",
        "ru.yandex.yandexnavi",
        "com.citymapper.app.release",
    )

    // A number, then a unit that stands alone: "250 m" and "1,5 km", never the "m" of "min".
    private val DISTANCE = Regex(
        """(?<![\w.,])\d+(?:[.,]\d+)?\s?(?:km|mi|m|ft|yd|metres|meters|miles|feet)(?!\w)""",
        RegexOption.IGNORE_CASE,
    )
    private val SEPARATORS = charArrayOf(' ', '·', '-', '–', '—', ',', '•')

    fun isNavigation(category: String?, packageName: String, ongoing: Boolean): Boolean =
        category == CATEGORY_NAVIGATION || (ongoing && packageName in APPS)

    fun parse(title: String, text: String, subText: String): NavigationInfo {
        val inTitle = DISTANCE.find(title)
        val inText = DISTANCE.find(text)
        val titleIsOnlyDistance = inTitle != null && title.removeRange(inTitle.range).trim(*SEPARATORS).isEmpty()
        val textIsOnlyDistance = inText != null && text.removeRange(inText.range).trim(*SEPARATORS).isEmpty()
        val distance = (inTitle ?: inText)?.value?.trim()
        val instruction = when {
            titleIsOnlyDistance -> text
            title.isNotBlank() -> title
            else -> text
        }.trim()
        // Whatever is left over — the app's own line of detail — stands in for an arrival time,
        // unless it only says the distance again ("in 300 m").
        val leftover = when {
            titleIsOnlyDistance || textIsOnlyDistance -> ""
            instruction != title.trim() -> ""
            inText != null && text.removeRange(inText.range).trim(*SEPARATORS).length <= SHORT_WORD -> ""
            else -> text
        }.trim()
        val eta = subText.trim().ifBlank { leftover }.ifBlank { null }
        return NavigationInfo(distance, instruction, eta)
    }

    /** Up to this long, what is left beside a distance is a preposition, not information. */
    private const val SHORT_WORD = 6

    /** Notification.CATEGORY_NAVIGATION, spelled out so this stays free of Android. */
    const val CATEGORY_NAVIGATION = "navigation"
}
