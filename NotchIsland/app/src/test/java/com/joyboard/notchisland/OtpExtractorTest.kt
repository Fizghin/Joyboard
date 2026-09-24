package com.joyboard.notchisland

import com.joyboard.notchisland.island.OtpExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun `plain verification message`() {
        assertEquals("482915", OtpExtractor.extract("Your verification code is 482915"))
    }

    @Test
    fun `code split by spaces is joined`() {
        assertEquals("123456", OtpExtractor.extract("OTP: 123 456"))
    }

    @Test
    fun `hyphenated code is joined`() {
        assertEquals("998211", OtpExtractor.extract("Your login code: 998-211"))
    }

    @Test
    fun `title and body are considered together`() {
        assertEquals("5821", OtpExtractor.extract("Bank", "Use 5821 to authenticate"))
    }

    @Test
    fun `a number without verification wording is not a passcode`() {
        assertNull(OtpExtractor.extract("Your order 4829158 has shipped"))
        assertNull(OtpExtractor.extract("12 new messages from 4 chats"))
    }

    @Test
    fun `too short and too long runs are rejected`() {
        assertNull(OtpExtractor.extract("Your code is 12"))
        assertNull(OtpExtractor.extract("Your verification code is 1234567890123"))
    }

    @Test
    fun `blank input is safe`() {
        assertNull(OtpExtractor.extract(null, "", "   "))
    }
}
