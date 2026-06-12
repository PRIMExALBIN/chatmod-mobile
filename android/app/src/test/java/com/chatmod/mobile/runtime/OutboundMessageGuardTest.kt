package com.chatmod.mobile.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundMessageGuardTest {
    @Test
    fun trimsValidMessages() {
        val result = OutboundMessageGuard().validate("  Hello chat  ")

        assertTrue(result.accepted)
        assertEquals("Hello chat", result.acceptedText)
    }

    @Test
    fun rejectsBlankMessages() {
        val result = OutboundMessageGuard().validate("   ")

        assertFalse(result.accepted)
        assertEquals("blank", result.rejectionReason)
        assertNull(result.acceptedText)
    }

    @Test
    fun rejectsControlCharacters() {
        val result = OutboundMessageGuard().validate("hello\nchat")

        assertFalse(result.accepted)
        assertEquals("control_characters", result.rejectionReason)
    }

    @Test
    fun rejectsMessagesOverTheYouTubeLimit() {
        val result = OutboundMessageGuard(maxLength = 5).validate("123456")

        assertFalse(result.accepted)
        assertEquals("too_long", result.rejectionReason)
    }
}
