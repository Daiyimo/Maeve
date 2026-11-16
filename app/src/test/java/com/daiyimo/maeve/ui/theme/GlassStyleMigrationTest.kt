package com.daiyimo.maeve.ui.theme

import com.daiyimo.maeve.settings.FeatureFlagsCapsuleTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 旧「减少透明效果」键迁移到液态玻璃档位:开过的落清透,迁移后旧键清除。 */
class GlassStyleMigrationTest {
    @Test fun legacyTrueFallsToClearAndCleansUp() {
        val prefs = FeatureFlagsCapsuleTest.MemoryPrefs()
        prefs.edit().putBoolean("reduceTransparency", true).commit()
        assertEquals("clear", resolveGlassStyle(prefs))
        assertEquals("clear", prefs.getString("glassStyle", null))
        assertFalse(prefs.contains("reduceTransparency"))
    }

    @Test fun legacyFalseFallsToBalanced() {
        val prefs = FeatureFlagsCapsuleTest.MemoryPrefs()
        prefs.edit().putBoolean("reduceTransparency", false).commit()
        assertEquals("balanced", resolveGlassStyle(prefs))
        assertEquals("balanced", prefs.getString("glassStyle", null))
        assertFalse(prefs.contains("reduceTransparency"))
    }

    @Test fun existingChoiceWinsOverLegacy() {
        val prefs = FeatureFlagsCapsuleTest.MemoryPrefs()
        prefs.edit().putString("glassStyle", "dense").putBoolean("reduceTransparency", true).commit()
        assertEquals("dense", resolveGlassStyle(prefs))
        assertFalse(prefs.contains("reduceTransparency"))
    }

    @Test fun freshInstallDefaultsBalanced() {
        val prefs = FeatureFlagsCapsuleTest.MemoryPrefs()
        assertEquals("balanced", resolveGlassStyle(prefs))
        assertEquals("balanced", prefs.getString("glassStyle", null))
    }

    @Test fun secondReadDoesNotRewrite() {
        val prefs = FeatureFlagsCapsuleTest.MemoryPrefs()
        prefs.edit().putString("glassStyle", "clear").commit()
        assertEquals("clear", resolveGlassStyle(prefs))
        assertEquals("clear", resolveGlassStyle(prefs))
        assertEquals("clear", prefs.getString("glassStyle", null))
    }
}
