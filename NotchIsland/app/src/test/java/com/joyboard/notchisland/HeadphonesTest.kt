package com.joyboard.notchisland

import android.media.AudioDeviceInfo
import com.joyboard.notchisland.island.Headphones
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphonesTest {

    @Test
    fun `headphones of every kind count, speakers and the earpiece do not`() {
        assertTrue(Headphones.isHeadphones(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(Headphones.isHeadphones(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(Headphones.isHeadphones(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertTrue(Headphones.isHeadphones(26))
        assertFalse(Headphones.isHeadphones(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(Headphones.isHeadphones(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertFalse(Headphones.isHeadphones(AudioDeviceInfo.TYPE_HDMI))
    }

    @Test
    fun `one pair arriving as several outputs is announced once`() {
        assertTrue(Headphones.isRepeat("Pixel Buds", "Pixel Buds", 300))
        assertFalse(Headphones.isRepeat("Pixel Buds", "Pixel Buds", 5_000))
        assertFalse(Headphones.isRepeat("Pixel Buds", "Galaxy Buds", 300))
        assertFalse(Headphones.isRepeat("Pixel Buds", null, 300))
    }
}
