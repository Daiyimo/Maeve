package com.daiyimo.maeve.core.vision

import java.util.UUID
import com.daiyimo.maeve.core.uistate.VisibleTextLine

/** 0–999 归一化坐标,与 tap 同一坐标系。 */
data class Point(val x: Int, val y: Int)

/**
 * find_visually 找到的点:按 (ref, snapshotId) 登记,只留最近 [capacity] 个(先进先出),
 * 记下找到时那张截图的哈希,tap 时据此判断画面变没变。任何写操作后由调用方 [clear]。
 */
class LocateRegistry(private val capacity: Int = 20) {
    private class Entry(val point: Point, val hash: Long, val packageName: String?, val viewport: Pair<Int, Int>?)

    private var next = 1
    private val entries = LinkedHashMap<String, Entry>()

    /** 返回 (ref, snapshotId),形如 ("@v3", "loc_…")。编号在一次任务里不复用。 */
    fun put(x: Int, y: Int, hash: Long, packageName: String? = null, viewport: Pair<Int, Int>? = null): Pair<String, String> {
        val ref = "@v${next++}"
        val snapshotId = "loc_${UUID.randomUUID()}"
        entries[key(ref, snapshotId)] = Entry(Point(x, y), hash, packageName, viewport)
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return ref to snapshotId
    }

    fun get(ref: String, snapshotId: String): Pair<Point, Long>? = entries[key(ref, snapshotId)]?.let { it.point to it.hash }

    /** A unique exact label from the same frame can be used without a cloud call. */
    internal fun locateTextFrame(
        target: String, hash: Long, lines: List<VisibleTextLine>, packageName: String?, viewport: Pair<Int, Int>?,
    ): LocateResult? {
        val want = target.filterNot(Char::isWhitespace).lowercase()
        val hits = lines.filter { it.text.filterNot(Char::isWhitespace).lowercase() == want && it.bounds.size == 4 }.distinctBy { it.bounds }
        if (hits.size > 1) {
            val positions = hits.take(4).joinToString { line ->
                "(" + center(line.bounds[0], line.bounds[2]) + ", " + center(line.bounds[1], line.bounds[3]) + ")"
            }
            return LocateResult.NotFound("element_ambiguous",
                "Matching labels (" + hits.size + ") at " + positions + ". Specify the role or describe nearby content.")
        }
        val hit = hits.singleOrNull() ?: return null
        val b = hit.bounds
        val x = center(b[0], b[2])
        val y = center(b[1], b[3])
        val (ref, id) = put(x, y, hash, packageName, viewport)
        return LocateResult.Found(ref, id, x, y, b.toIntArray(), hit.text, "ocr", null, unverified = true)
    }

    private fun center(a: Int, b: Int): Int = (a + b) / 2

    internal fun matchesSurface(ref: String, snapshotId: String, packageName: String?, viewport: Pair<Int, Int>?): Boolean {
        val entry = entries[key(ref, snapshotId)] ?: return false
        return (entry.packageName == null || entry.packageName == packageName) &&
            (entry.viewport == null || entry.viewport == viewport)
    }

    fun clear() = entries.clear()

    /** [snapshotId] 是这里登记过、还留着的点的。 */
    fun has(snapshotId: String): Boolean = entries.keys.any { it.startsWith("$snapshotId|") }

    private fun key(ref: String, snapshotId: String) = "$snapshotId|$ref"
}
