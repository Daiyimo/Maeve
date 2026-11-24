package com.daiyimo.maeve.core.cap.phone

import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenPort

internal class RecordingScreen : ScreenPort by FakeScreen() {
    val paths = mutableListOf<List<List<Pair<Int, Int>>>>()
    val durations = mutableListOf<Long>()
    val holds = mutableListOf<Long>()
    val rests = mutableListOf<Long>()
    override suspend fun gesture(
        paths: List<List<Pair<Int, Int>>>, durationMs: Long, holdMs: Long, restMs: Long,
    ): ActionResult {
        this.paths += paths; durations += durationMs; holds += holdMs; rests += restMs
        return ActionResult.Ok(changed = true)
    }
}
