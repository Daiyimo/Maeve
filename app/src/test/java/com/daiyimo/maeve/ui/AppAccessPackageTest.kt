package com.daiyimo.maeve.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppAccessPackageTest {
    @Test fun packageNamesAreNormalizedBeforeUse() {
        assertEquals("com.example.app", requireAppAccessPackage("  COM.Example.App  ", "com.daiyimo.maeve"))
    }

    @Test fun ownPackageCannotBeChangedThroughCaseOrWhitespaceVariants() {
        assertThrows(IllegalArgumentException::class.java) {
            requireAppAccessPackage("  COM.DAIYIMO.MAEVE  ", "com.daiyimo.maeve")
        }
    }

    @Test fun malformedPackageNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            requireAppAccessPackage("not a package", "com.daiyimo.maeve")
        }
    }
}
