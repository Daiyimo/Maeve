package com.daiyimo.maeve.cap.phone.a11y

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackFrameGuardTest {
    @Test fun singleBlackFrameIsNotProtection() {
        var now = 0L
        val guard = BlackFrameGuard({ now })
        assertFalse(guard.isProtected(true))
        now = 100
        assertFalse(guard.isProtected(true))
    }

    @Test fun sustainedBlackFramesBecomeProtectionAfterGrace() {
        var now = 0L
        val guard = BlackFrameGuard({ now })
        assertFalse(guard.isProtected(true))
        now = 349
        assertFalse(guard.isProtected(true))
        now = 350
        assertTrue(guard.isProtected(true))
    }

    @Test fun readableFrameResetsTheBlackStreak() {
        var now = 0L
        val guard = BlackFrameGuard({ now })
        assertFalse(guard.isProtected(true))
        now = 300
        assertFalse(guard.isProtected(false))
        now = 400
        assertFalse(guard.isProtected(true))
        now = 749
        assertFalse(guard.isProtected(true))
        now = 750
        assertTrue(guard.isProtected(true))
    }

    @Test fun resetClearsThePreviousAppsBlackStreak() {
        var now = 0L
        val guard = BlackFrameGuard({ now })
        assertFalse(guard.isProtected(true))
        now = 350
        assertTrue(guard.isProtected(true))
        guard.reset()
        now = 700
        assertFalse(guard.isProtected(true))
        now = 1_050
        assertTrue(guard.isProtected(true))
    }

    @Test fun missingFrameIsNotProtectionAndResetsTheStreak() {
        var now = 0L
        val guard = BlackFrameGuard({ now })
        assertFalse(guard.isProtected(true))
        now = 300
        assertFalse(guard.isProtected(null))
        now = 400
        assertFalse(guard.isProtected(true))
    }
}
