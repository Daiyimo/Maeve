package com.daiyimo.maeve.core.guard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFingerprintsTest {
    private val surface = JsonArray(listOf(JsonObject(mapOf(
        "ref" to JsonPrimitive("@e1"),
        "role" to JsonPrimitive("SurfaceView"),
        "name" to JsonPrimitive("Game view"),
        "bounds" to JsonArray(listOf(0, 0, 999, 999).map(::JsonPrimitive)),
    ))))

    private fun screen(vararg lines: String, offset: Int = 0) = buildJsonObject {
        put("screen", buildJsonObject {
            put("snapshotId", JsonPrimitive("changing"))
            put("base", JsonPrimitive("base-$offset"))
            put("treeQuality", JsonPrimitive("partial"))
            put("visibleText", JsonArray(lines.mapIndexed { i, text ->
                JsonObject(mapOf(
                    "text" to JsonPrimitive(text),
                    "bounds" to JsonArray(listOf(offset, i * 10, offset + 20, i * 10 + 8).map(::JsonPrimitive)),
                ))
            }))
        })
    }

    @Test fun surfaceViewProgressIncludesOcrText() {
        val before = screenFingerprint(screen("开始游戏", "KDA"), surface)
        val after = screenFingerprint(screen("魔典", "收集度 72%"), surface)
        assertNotEquals(before, after)
    }

    @Test fun ocrCoordinatesAndReadingMetadataDoNotCreateProgress() {
        val before = screenFingerprint(screen("魔典", "收集度 72%"), surface)
        val after = screenFingerprint(screen("收集度 72%", "魔典", offset = 3), surface)
        assertEquals(before, after)
    }

    @Test fun changingNetworkStatsDoNotCreateSemanticProgress() {
        val before = screenFingerprint(screen("9.62 % 4.75M/s", "魔典", "72%"), surface)
        val after = screenFingerprint(screen("13.22 % 5.14M/s", "魔典", "72%"), surface)
        assertEquals(before, after)
    }

    @Test fun topLevelPartialTreeAlsoUsesOcrProgress() {
        fun top(text: String) = buildJsonObject {
            put("treeQuality", JsonPrimitive("partial"))
            put("visibleText", (screen(text)["screen"] as JsonObject)["visibleText"]!!)
        }
        assertNotEquals(screenFingerprint(top("魔典"), surface), screenFingerprint(top("收集度 73%"), surface))
    }

    @Test fun businessTimeChangesAreSemanticProgress() {
        assertNotEquals(screenFingerprint(screen("预约 12:30"), surface), screenFingerprint(screen("预约 13:30"), surface))
    }

    @Test fun businessValueChangesAreSemanticProgress() {
        assertNotEquals(screenFingerprint(screen("收集度 72%"), surface), screenFingerprint(screen("收集度 73%"), surface))
    }

    @Test fun semanticOcrProgressKeepsSurfaceViewExplorationAlive() {
        val guards = OperationGuards()
        guards.frame("initial", screenFingerprint(screen("KDA"), surface)!!)
        val verdicts = (1..8).flatMap { i ->
            guards.noteAction()
            guards.effect("done")
            guards.frame("game-$i", screenFingerprint(screen("页面 $i", "魔典"), surface)!!)
        }
        assertTrue(verdicts.toString(), verdicts.none { it.code == "no_new_state" })
        assertNull(guards.stop)
    }

    @Test fun unchangedSurfaceWithoutOcrKeepsTheElementFingerprint() {
        assertEquals(elementsHash(surface), screenFingerprint(buildJsonObject { put("screen", buildJsonObject {}) }, surface))
    }
}
