package com.daiyimo.maeve.settings

import com.daiyimo.maeve.core.access.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppAccessStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file() = java.io.File(temp.root, "access.json")

    @Test fun newAppsAndClassificationNeverChangePermission() {
        val store = AppAccessStore(file())
        assertNull(store.current().denial("new.bank"))
        store.classify(mapOf("new.bank" to AppClassification(AppCategory.Finance)))
        assertNull(store.current().denial("new.bank"))
        store.setBlocked("new.bank", true)
        store.classify(mapOf("new.bank" to AppClassification(AppCategory.Other)))
        assertEquals("app_blocked", store.current().denial("new.bank")?.code)
        assertEquals(store.current().blocked, AppAccessStore(file()).current().blocked)
    }

    @Test fun sharingDoesNotUnblockAndReblockingClearsGrant() {
        val store = AppAccessStore(file())
        store.setBlocked("app", true)
        store.setShareAllowed("app", true)
        assertTrue(store.current().canShare("app"))
        assertNotNull(store.current().denial("app"))
        assertTrue(AppAccessStore(file()).current().canShare("app"))
        store.setShareAllowed("app", false)
        assertFalse(store.current().canShare("app"))
        store.setBlocked("app", false)
        assertNull(store.current().denial("app"))
        store.setBlocked("app", true)
        assertFalse(store.current().canShare("app"))
    }

    @Test fun manualClassificationSurvivesAutomaticUpdates() {
        val store = AppAccessStore(file())
        store.classify(mapOf("app" to AppClassification(AppCategory.Game, manual = true)))
        store.classify(mapOf("app" to AppClassification(AppCategory.Finance)))
        assertEquals(AppCategory.Game, store.current().classifications["app"]?.category)
    }

    @Test fun presetsDoNotApplyToFuturePackages() {
        val store = AppAccessStore(file())
        store.blockPackages(setOf("old.bank"))
        assertNull(store.current().denial("new.bank"))
        store.allowAll()
        assertTrue(store.current().blocked.isEmpty())
    }

    @Test fun corruptFileDoesNotAllowEveryApp() {
        file().writeText("{bad")
        val store = AppAccessStore(file())
        assertEquals("app_access_unavailable", store.current().denial("app")?.code)
        store.reset()
        assertNull(store.current().denial("app"))
    }

    @Test fun visualModeDefaultsToAllAppsAndSupportsOverrides() {
        val store = AppAccessStore(file())
        assertTrue(store.current().visualFirstDefault)
        assertTrue(store.current().visualFirst("com.tencent.jkchess"))
        assertFalse(store.current().visualFirst("com.stepos.account"))
        assertFalse(store.current().visualFirst("com.stepos.ailauncher"))
        assertFalse(store.current().visualFirst(null))

        val revision = store.current().revision
        store.setVisualFirstOverride("COM.TENCENT.JKCHESS", false)
        assertTrue(store.current().revision > revision)
        assertFalse(store.current().visualFirst("com.tencent.jkchess"))
        assertTrue(store.current().visualFirst("com.tencent.mm"))
        store.setVisualFirstDefault(false)
        assertFalse(store.current().visualFirst("com.tencent.mm"))
        assertFalse(store.current().visualFirst("com.tencent.jkchess"))
        store.setVisualFirstOverride("com.tencent.mm", true)
        assertTrue(store.current().visualFirst("com.tencent.mm"))
        store.setVisualFirstOverride("com.tencent.mm", null)
        assertFalse(store.current().visualFirst("com.tencent.mm"))
    }

    @Test fun visualModePersistsAndDoesNotChangeAccessRules() {
        val store = AppAccessStore(file())
        store.setBlocked("com.example.app", true)
        store.setVisualFirstDefault(false)
        store.setVisualFirstOverride("com.example.app", true)
        val loaded = AppAccessStore(file()).current()
        assertFalse(loaded.visualFirstDefault)
        assertTrue(loaded.visualFirst("com.example.app"))
        assertEquals(setOf("com.example.app"), loaded.blocked)
        assertEquals("app_blocked", loaded.denial("com.example.app")?.code)
        store.reset()
        assertTrue(store.current().visualFirstDefault)
        assertEquals(mapOf("com.stepos.account" to false, "com.stepos.ailauncher" to false), store.current().visualFirstOverrides)
    }

    @Test fun versionOneMigratesToVisualFirstDefaultAndKeepsExistingRules() {
        file().writeText("""{"version":1,"blocked":["com.blocked"],"shareAllowed":[],"classifications":{"com.example.app":{"category":"Game","reason":"用户选择","manual":true}}}""")
        val store = AppAccessStore(file())
        val state = store.current()
        assertTrue(state.available)
        assertTrue(state.visualFirstDefault)
        assertTrue(state.visualFirst("com.example.app"))
        assertFalse(state.visualFirst("com.stepos.account"))
        assertFalse(state.visualFirst("com.stepos.ailauncher"))
        assertEquals(setOf("com.blocked"), state.blocked)
        assertEquals(AppCategory.Game, state.classifications["com.example.app"]?.category)

        store.setVisualFirstDefault(false)
        val migrated = file().readText()
        assertTrue(migrated.contains("\"version\":2"))
        assertTrue(migrated.contains("com.blocked"))
        assertTrue(migrated.contains("com.example.app"))
    }

    @Test fun corruptVisualModeFailsClosed() {
        file().writeText("""{"version":2,"blocked":[],"shareAllowed":[],"classifications":{},"visualFirstDefault":"yes"}""")
        assertEquals("app_access_unavailable", AppAccessStore(file()).current().denial("app")?.code)
    }

    @Test fun livePolicyReferenceSeesChangesAndUnknownTargetIsSeparate() {
        val store = AppAccessStore(file())
        val policy: AppAccessPolicy = store
        assertNull(policy.current().denial("app"))
        store.setBlocked("app", true)
        assertEquals("app_blocked", policy.current().denial("app")?.code)
        assertEquals("app_access_unverified", policy.current().denial(null)?.code)
        assertEquals(setOf("app"), policy.current().blocked)
    }
}
