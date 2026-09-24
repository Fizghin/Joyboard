package com.joyboard.notchisland.island

/**
 * Finds one-time passcodes in notification text so the island can offer to copy them, the way
 * the keyboard suggestion strip does. Deliberately conservative: a bare number in a sentence is
 * not a passcode unless the text reads like a verification message.
 */
object OtpExtractor {

    // Four digits is the shortest real passcode, so the run in the middle can be as short as two.
    private val codePattern = Regex("""(?<![\w.])(\d[\d\- ]{2,9}\d)(?![\w.])""")

    private val contextWords = listOf(
        "otp", "one-time", "one time", "verification", "verify", "passcode", "pass code",
        "security code", "auth", "authentication", "2fa", "code is", "your code",
        "login code", "access code", "confirmation code", "pin is",
    )

    /** Returns the passcode, or null when the text does not look like a verification message. */
    fun extract(vararg parts: String?): String? {
        val text = parts.filterNotNull().joinToString(" ").takeIf { it.isNotBlank() } ?: return null
        val lower = text.lowercase()
        if (contextWords.none { lower.contains(it) }) return null
        return codePattern.findAll(text)
            .map { it.groupValues[1].filter(Char::isDigit) }
            .firstOrNull { it.length in 4..8 }
    }
}
