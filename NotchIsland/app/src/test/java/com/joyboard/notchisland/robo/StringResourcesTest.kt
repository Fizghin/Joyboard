package com.joyboard.notchisland.robo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.joyboard.notchisland.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every string resolves, and the escaped and formatted ones come out as written. */
@RunWith(RobolectricTestRunner::class)
class StringResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyStringResolvesToText() {
        R.string::class.java.fields.forEach { field ->
            val text = context.getString(field.getInt(null))
            assertTrue("${field.name} is blank", text.isNotBlank())
            assertTrue("${field.name} kept an escape: $text", !text.contains("\\'") && !text.contains("\\\""))
        }
    }

    @Test
    fun apostrophesAndQuotesSurvive() {
        assertEquals("You're on the latest version", context.getString(R.string.you_re_latest_version))
        // An escaped line break becomes a real paragraph break, not a literal backslash.
        assertTrue(context.getString(R.string.helper_disclosure_body).contains("anywhere.\n\nOn the next screen"))
    }

    @Test
    fun formattedStringsTakeTheirArguments() {
        assertEquals("Downloading 42%", context.getString(R.string.downloading, 42))
        assertEquals("Found a 24×30 dp cutout", context.getString(R.string.found_dp_cutout, 24, 30))
        assertEquals("Lap 3", context.getString(R.string.lap_n, 3))
        // A literal percent sign after a number, not a second format argument.
        assertEquals("76%", context.getString(R.string.percent, 76))
        assertEquals("Full in 1 h 5 min", context.getString(R.string.full_in, context.getString(R.string.duration_hours_minutes, 1, 5)))
    }

    @Test
    fun pluralsPickTheRightForm() {
        val res = context.resources
        assertEquals("1 notification", res.getQuantityString(R.plurals.notification_count, 1, 1))
        assertEquals("4 notifications", res.getQuantityString(R.plurals.notification_count, 4, 4))
        assertEquals(" · 1 app opens the island", res.getQuantityString(R.plurals.auto_expand_apps, 1, 1))
    }
}
