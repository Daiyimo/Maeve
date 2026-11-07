package com.daiyimo.maeve.core.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityListTest {
    private val maeve = PermissionCommands.accessibilityService("com.daiyimo.maeve")
    private val maeveLong = "com.daiyimo.maeve/com.daiyimo.maeve.a11y.MaeveAccessibilityService"
    private val other = "com.example.reader/.ReaderService"
    private val other2 = "org.sample.keys/org.sample.keys.KeyService"

    @Test
    fun emptyOrUnsetValues() {
        listOf(null, "", "   ", "null", "null\n", ":", " : ").forEach { raw ->
            assertEquals("raw=$raw", emptyList<String>(), AccessibilityList.entries(raw))
            assertFalse(AccessibilityList.contains(raw, maeve))
            assertEquals(maeve, AccessibilityList.with(raw, maeve))
            assertEquals("", AccessibilityList.without(raw, maeve))
        }
    }

    @Test
    fun appendKeepsOtherServicesInOrder() {
        assertEquals("$other:$maeve", AccessibilityList.with(other, maeve))
        assertEquals("$other:$other2:$maeve", AccessibilityList.with("$other:$other2\n", maeve))
    }

    @Test
    fun alreadyPresentIsNotDuplicated() {
        assertEquals("$other:$maeve", AccessibilityList.with("$other:$maeve", maeve))
        // 系统里存的是完整类名写法,也算已经有了
        assertEquals("$maeveLong:$other", AccessibilityList.with("$maeveLong:$other", maeve))
        assertTrue(AccessibilityList.contains(maeveLong, maeve))
    }

    @Test
    fun removeKeepsOthersAndHandlesBothSpellings() {
        assertEquals("$other:$other2", AccessibilityList.without("$other:$maeve:$other2", maeve))
        assertEquals(other, AccessibilityList.without("$maeveLong:$other", maeve))
        assertEquals(other, AccessibilityList.without(other, maeve))
        assertEquals("", AccessibilityList.without(maeve, maeve))
    }

    @Test
    fun strayColonsAndSpacesAreCleanedUp() {
        assertEquals("$other:$other2:$maeve", AccessibilityList.with(":$other:: $other2 :", maeve))
        assertEquals("$other:$other2", AccessibilityList.without("$other::$maeve:$other2:", maeve))
    }

    @Test
    fun differentServiceOfSamePackageIsNotConfused() {
        val sibling = "com.daiyimo.maeve/.a11y.OtherService"
        assertFalse(AccessibilityList.contains(sibling, maeve))
        assertEquals(sibling, AccessibilityList.without("$sibling:$maeve", maeve))
    }
}
