package com.daiyimo.maeve.core.guard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.floor

/** 键排序后的稳定序列化,使参数顺序不影响签名。 */
private fun stable(e: JsonElement): String = when (e) {
    is JsonObject -> e.keys.sorted().joinToString(",", "{", "}") { "${JsonPrimitive(it)}:${stable(e[it]!!)}" }
    is JsonArray -> e.joinToString(",", "[", "]") { stable(it) }
    else -> e.toString()
}

private fun num(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
private val SWIPE_KEYS = listOf("startX", "startY", "endX", "endY")

/** poki 的同调用签名:工具名加完整稳定 JSON,只忽略对象键顺序。 */
fun exactCallSignature(tool: String, args: JsonObject): String = "$tool\n${stable(JsonObject(args))}"

/** 动作签名:忽略 reason,坐标按 50 取整,滑动只记方向。 */
fun actionSignature(tool: String, args: JsonObject): String {
    val n = args.toMutableMap()
    n.remove("reason")
    val x = num(n["x"]); val y = num(n["y"])
    if (x != null && y != null) {
        n["x"] = JsonPrimitive(floor(x / 50).toLong())
        n["y"] = JsonPrimitive(floor(y / 50).toLong())
    }
    val s = SWIPE_KEYS.map { num(n[it]) }
    if (s.all { it != null }) {
        val dx = s[2]!! - s[0]!!; val dy = s[3]!! - s[1]!!
        val vertical = abs(dy) >= abs(dx)
        n["swipeDirection"] = JsonPrimitive("${if (vertical) "V" else "H"}${if ((if (vertical) dy else dx) < 0) "-" else "+"}")
        SWIPE_KEYS.forEach { n.remove(it) }
    }
    return "$tool|${stable(JsonObject(n))}"
}
