package com.example.castultimate

import org.junit.Test
import org.junit.Assert.*

class CastManagerTest {

    @Test
    fun testIsConnected_whenNoSession_returnsFalse() {
        val manager = CastManager()
        assertFalse(manager.isConnected())
    }

    @Test
    fun testGetVolume_whenNoSession_returnsZero() {
        val manager = CastManager()
        assertEquals(0.0, manager.getVolume(), 0.0)
    }

    @Test
    fun testControl_withInvalidAction_doesNotThrow() {
        val manager = CastManager()
        try {
            manager.control("invalid_action")
        } catch (e: Exception) {
            fail("Should not throw exception")
        }
    }

    @Test
    fun testCastVideo_withEmptyUrl_doesNotThrow() {
        val manager = CastManager()
        try {
            manager.castVideo("")
        } catch (e: Exception) {
            fail("Should not throw exception")
        }
    }
}
