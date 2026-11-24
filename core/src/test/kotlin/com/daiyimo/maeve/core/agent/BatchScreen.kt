package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import com.daiyimo.maeve.core.uistate.Verification
import com.daiyimo.maeve.core.tool.FakeScreen

/** 元素:名字 → 归一化 bounds;每次读屏换下一屏(读完最后一屏就停在它);动作结果按顺序取。 */
internal class BatchScreen(vararg val screens: List<Pair<String, List<Int>>>) : ScreenPort by FakeScreen() {
    val calls = mutableListOf<String>()
    var reads = 0
    val results = ArrayDeque<ActionResult>()
    /** 设了就一直显示这一屏(模拟等关卡期间屏幕换了)。 */
    var replaced: List<Pair<String, List<Int>>>? = null
    var settles = 0
    override suspend fun waitStable(timeoutMs: Long): Boolean { settles++; return true }
    private fun build(i: Int) = ScreenSnapshot("p${i + 1}", "com.x", (replaced ?: screens[minOf(i, screens.size - 1)]).mapIndexed { n, (name, b) ->
        UiElement("@e${n + 1}", "button", name, ((b[0] + b[2]) / 2) * 1079 / 999, ((b[1] + b[3]) / 2) * 2399 / 999, b, null, true, name.startsWith("输入"), false)
    }, 1080, 2400)
    override suspend fun snapshot(): ScreenSnapshot = build(reads++)
    override suspend fun peek(): ScreenSnapshot = build(maxOf(0, reads - 1))
    private fun next(): ActionResult = results.removeFirstOrNull() ?: ActionResult.Ok(changed = true)
    override suspend fun tapRef(ref: String, snapshotId: String): ActionResult { calls += "tapRef $ref $snapshotId"; return next() }
    override suspend fun tapXY(x: Int, y: Int): ActionResult { calls += "tapXY $x $y"; return next() }
    // 端口写完读回过、一致(真实端口都会读回)
    override suspend fun typeText(ref: String?, snapshotId: String?, text: String): ActionResult {
        calls += "type $text"; return results.removeFirstOrNull() ?: ActionResult.Ok(verification = Verification.MATCH)
    }
    override suspend fun back(): ActionResult { calls += "back"; return next() }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): ActionResult { calls += "swipe"; return next() }
}
