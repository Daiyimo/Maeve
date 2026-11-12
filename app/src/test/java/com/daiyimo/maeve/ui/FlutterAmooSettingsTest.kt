package com.daiyimo.maeve.ui

import com.daiyimo.maeve.intercept.AmooSetup
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FlutterAmooSettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun enabledBridgeDoesNotPretendSetupWasVerified() {
        val status = FlutterAmooSettings.snapshot(AmooSetup.Stage.Waiting, true, tmp.root, true, true)
        assertEquals("waiting", status["stage"])
        assertEquals(true, status["bridgeEnabled"])
        assertNull(status["lastResultAt"])
    }

    @Test fun reportsOnlyActualResultFilesWithoutReadingTheirContents() {
        val out = File(tmp.root, "amoo/out").apply { mkdirs() }
        File(out, "older.json").apply { writeText("private result"); setLastModified(10_000) }
        File(out, "newer.json").apply { writeText("private result"); setLastModified(20_000) }
        File(out, "pending.tmp").apply { writeText("partial"); setLastModified(30_000) }
        File(out, "directory.json").apply { mkdirs(); setLastModified(40_000) }
        val status = FlutterAmooSettings.snapshot(AmooSetup.Stage.Idle, false, tmp.root, false, false)
        assertEquals(20_000L, status["lastResultAt"])
        assertFalse(status.toString().contains("private result"))
        assertEquals(false, status["installed"])
    }

    @Test fun unavailableExternalStorageHasNoFabricatedActivity() {
        val status = FlutterAmooSettings.snapshot(AmooSetup.Stage.Failed, true, null, true, false)
        assertEquals("failed", status["stage"])
        assertNull(status["lastResultAt"])
        assertEquals(false, status["accessibility"])
    }
}
