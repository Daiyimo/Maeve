package com.daiyimo.maeve.wechat

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AmooExecutionRepairTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun unchangedPolicyKeepsBothFilesAndRepairsAMissingFile() {
        AmooExecutionPolicy.write(tmp.root, true)
        val policy = File(tmp.root, "execution-policy.json")
        val legacy = File(tmp.root, "wechat/control.json")
        val timestamp = 1_700_000_000_000L
        assertTrue(policy.setLastModified(timestamp))
        assertTrue(legacy.setLastModified(timestamp))

        AmooExecutionPolicy.write(tmp.root, true)
        assertEquals(timestamp, policy.lastModified())
        assertEquals(timestamp, legacy.lastModified())

        val legacyContents = legacy.readText()
        assertTrue(legacy.delete())
        AmooExecutionPolicy.write(tmp.root, true)
        assertEquals(timestamp, policy.lastModified())
        assertEquals(legacyContents, legacy.readText())
    }
}
