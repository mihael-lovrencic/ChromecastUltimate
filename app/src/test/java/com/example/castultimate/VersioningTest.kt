package com.example.castultimate

import org.junit.Assert.assertEquals
import org.junit.Test

class VersioningTest {

    @Test
    fun compareVersions_handlesEqualVersions() {
        assertEquals(0, Versioning.compareVersions("1.2.3", "1.2.3"))
    }

    @Test
    fun compareVersions_handlesNewerVersion() {
        assertEquals(1, Versioning.compareVersions("1.2.4", "1.2.3"))
    }

    @Test
    fun compareVersions_handlesShorterVersion() {
        assertEquals(-1, Versioning.compareVersions("1.2", "1.2.1"))
    }

    @Test
    fun compareVersions_ignoresNonNumericSegments() {
        assertEquals(0, Versioning.compareVersions("1.2-beta", "1.2.0"))
    }
}
