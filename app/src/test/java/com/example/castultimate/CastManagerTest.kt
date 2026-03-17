package com.example.castultimate

import org.junit.Test
import org.junit.Assert.*

class CastManagerTest {

    @Test
    fun testIsConnected_whenNoSession_returnsFalse() {
        assertFalse(CastManager.isConnected())
    }

    @Test
    fun testGetVolume_whenNoSession_returnsZero() {
        assertEquals(0.0, CastManager.getVolume(), 0.0)
    }

    @Test
    fun testControl_withInvalidAction_doesNotThrow() {
        try {
            CastManager.control("invalid_action")
        } catch (e: Exception) {
            fail("Should not throw exception")
        }
    }

    @Test
    fun testCastVideo_withEmptyUrl_doesNotThrow() {
        try {
            CastManager.castVideo("")
        } catch (e: Exception) {
            fail("Should not throw exception")
        }
    }
}
