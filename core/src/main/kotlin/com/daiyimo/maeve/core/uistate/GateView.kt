package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.replay.Anchors
import com.daiyimo.maeve.core.replay.RouteAnchors
import com.daiyimo.maeve.core.replay.Selector
import com.daiyimo.maeve.core.tool.LOCATED_REF
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.argStr
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * 给执行入口(不可逆判定、运行记录、路线)看的一次调用的目标:按哪一屏判、@vN 换成什么坐标、端口编号是什么、
 * 写操作前的目标元素与锚点。都按调用所属那块屏的读屏状态([session])来看;只读,不执行任何动作。
 */
internal class GateView {
    fun snapshotFor(session: ScreenSession, args: JsonObject): ScreenSnapshot? {
        val snapshotId = args.argStr("snapshotId")
        // 旧一屏的编号仍在最新一屏里:动作落在最新一屏的那个它,按最新一屏判(编号相同,snapshotId 照模型给的)
        val modelRef = args.argStr("ref")
        val stabilizer = session.stabilizer
        if (stabilizer != null && snapshotId != null && modelRef != null && !LOCATED_REF.matches(modelRef)) {
            val resolved = stabilizer.resolve(modelRef, snapshotId)
            if (resolved is Resolved.Current && resolved.relocated) stabilizer.latest?.let { return it.screen.copy(snapshotId = snapshotId) }
        }
        if (snapshotId != null) return session.recentSnapshots.lastOrNull { it.snapshotId == snapshotId }
        return session.lastSnapshot
    }

    fun portRecord(session: ScreenSession, args: JsonObject): JsonObject {
        val stabilizer = session.stabilizer ?: return JsonObject(emptyMap())
        val modelRef = args.argStr("ref")
        val snapshotId = args.argStr("snapshotId")
        if (modelRef == null || snapshotId == null || LOCATED_REF.matches(modelRef)) return JsonObject(emptyMap())
        val resolved = stabilizer.resolve(modelRef, snapshotId) as? Resolved.Current ?: return JsonObject(emptyMap())
        return buildJsonObject { put("portRef", resolved.portRef); put("portSnapshotId", resolved.portSnapshotId) }
    }

    /**
     * 点 @vN 时换成它登记的坐标,连同找的是什么(target)、原文(text)、视觉模型看到的字(seen),按坐标点击来判;
     * 往 @vN 上打字、清空时同样换成坐标,其余参数(回车)照留。
     * 其余的原样返回(不认识的 @vN 到执行时会是 ref_expired,点不下去)。
     */
    fun gateArgs(session: ScreenSession, verb: Verb?, args: JsonObject): JsonObject {
        val located = locatedPair(verb, args) ?: return args
        val (point, _) = session.located.get(located.first, located.second) ?: return args
        val record = session.locatedInfo[located.first]
        return buildJsonObject {
            if (verb != Verb.Tap) args.forEach { (k, v) -> if (k != "ref" && k != "snapshotId") put(k, v) }
            put("x", point.x); put("y", point.y)
            (record?.target ?: (args["target"] as? JsonPrimitive)?.content)?.let { put("target", it) }
            record?.text?.let { put("text", it) }
            record?.seen?.let { put("seen", it) }
        }
    }

    /**
     * 登记过的 @vN,或看过 describe 之后带着 target 的坐标点击(不带 target 的会被参数校验退回)。
     * 这类点击按大小模型分工交给主模型做(见 [com.daiyimo.maeve.core.action.ConfirmDecision.mayBeIrreversible])。
     */
    fun visionSourced(session: ScreenSession, verb: Verb?, args: JsonObject): Boolean {
        if (verb != Verb.Tap) return false
        locatedPair(verb, args)?.let { return session.located.get(it.first, it.second) != null }
        val wanted = args.argStr("target")
        return session.described && args["ref"] == null && args["x"] != null && !wanted.isNullOrBlank()
    }

    fun confirmedSeen(session: ScreenSession, verb: Verb?, args: JsonObject): String? {
        val located = locatedPair(verb, args) ?: return null
        if (session.located.get(located.first, located.second) == null) return null
        return session.locatedInfo[located.first]?.seen?.takeIf { it.isNotBlank() }
    }

    /** 点击,以及往一点上打字、清空,接受 @vN。 */
    private fun locatedPair(verb: Verb?, args: JsonObject): Pair<String, String>? {
        if (verb != Verb.Tap && verb != Verb.TypeText && verb != Verb.ClearText) return null
        val ref = args.argStr("ref")?.takeIf(LOCATED_REF::matches) ?: return null
        val sid = args.argStr("snapshotId") ?: return null
        return ref to sid
    }

    /** 路线用:写操作执行前,从当时的读屏取下的目标元素、锚点与那一屏。 */
    class Capture(val target: Selector?, val before: Anchors, val snapshot: ScreenSnapshot?)
    var capture: Capture? = null

    fun anchorsAfter(after: ScreenSnapshot): Anchors? = capture?.let { RouteAnchors.after(it.snapshot, after) }

    fun captureTarget(session: ScreenSession, verb: Verb, o: JsonObject): Capture {
        // @vN 按登记的坐标来认
        val gated = gateArgs(session, verb, o)
        val modelRef = gated.argStr("ref")
        val aimed = snapshotFor(session, gated)
        val at = aimed ?: session.lastSnapshot
        fun coordinate(k: String): Int? = when (val v = gated[k]) {
            is JsonPrimitive -> v.content.toDoubleOrNull()?.roundToInt()
            is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }.takeIf { it.size == 2 }?.average()?.roundToInt()
            else -> null
        }
        val x = coordinate("x"); val y = coordinate("y")
        val element = when {
            modelRef != null -> aimed?.elements?.firstOrNull { it.ref == modelRef }
            verb == Verb.Tap && x != null && y != null -> at?.let { RouteAnchors.elementAt(it, x, y) }
            // 输入、清空作用在有焦点的输入框上
            verb == Verb.TypeText || verb == Verb.ClearText -> at?.elements?.firstOrNull { it.focused && it.editable }
            else -> null
        }
        return Capture(element?.let { RouteAnchors.selector(at!!, it) }, RouteAnchors.before(at, element), at)
    }
}
