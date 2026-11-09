package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.capability.CapabilityKey
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.tool.FAILURE_MESSAGES
import com.daiyimo.maeve.core.tool.HINT_NOT_SETTLED
import com.daiyimo.maeve.core.tool.HINT_TAP_UNCHANGED
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.refExpired
import com.daiyimo.maeve.core.tool.okObj
import com.daiyimo.maeve.core.tool.switchHint
import com.daiyimo.maeve.core.vision.LocateRegistry
import com.daiyimo.maeve.core.vision.Locator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * 界面状态挂进工具上下文后的几个取用口:开局由 [Interfaces.attach] 放进去,有界面的工具(手机、网页,以及通用工具里点名了屏幕的)从这里取。
 * 工具引擎(core/tool)不认得这些,只经 [com.daiyimo.maeve.core.tool.SurfaceLayer] 使唤界面一侧。
 */

/** 界面状态在工具上下文里用的键。 */
internal val INTERFACES = CapabilityKey<Interfaces>("interfaces")

/** 本任务的界面状态,就是开局放进来的那一份。 */
internal val ToolContext.interfaces: Interfaces get() = this[INTERFACES] ?: error("interfaces not attached")

/** 分发器所属任务的界面状态(执行入口、回放按读屏判定时用)。 */
internal val ToolDispatcher.interfaces: Interfaces get() = ctx.interfaces

/** 每块屏的读屏状态,按能力 id 索引。 */
internal val ToolContext.sessions: Map<String, ScreenSession> get() = interfaces.sessions

/** 最近一次操作落在哪块屏上。 */
internal val ToolContext.focus: SurfaceFocus get() = interfaces.focus

/** 此刻在操作的那块屏的读屏状态(分发器每次调用前按工具所属的屏记下,见 [focus])。 */
internal val ToolContext.session: ScreenSession get() = sessions.getValue(focus.current.id)
internal val ToolContext.screen: ScreenPort get() = session.screen

/** 此刻在操作的是哪块屏。 */
internal val ToolContext.surface: Surface get() = focus.current
internal val ToolContext.lastSnapshot: ScreenSnapshot? get() = session.lastSnapshot

/** 此刻那块屏上 find_visually 找到的点(@vN);那块屏上任何写操作之后清空。 */
internal val ToolContext.located: LocateRegistry get() = session.located
internal val ToolContext.locatedInfo: MutableMap<String, Located> get() = session.locatedInfo

/** 上次写操作之后看过 describe:这期间的坐标点击,按看图得来的点对待。 */
internal var ToolContext.described: Boolean
    get() = session.described
    set(v) { session.described = v }
internal val ToolContext.locator: Locator? get() = session.locator

internal fun ToolContext.clearLocated() = session.clearLocated()

/** 把端口的动作结果转成工具结果:没变化、被切到别的 App、页面没静下来,都写进提示;失败按错误码给说明。 */
internal fun ToolContext.action(r: ActionResult): ToolExecution = when (r) {
    is ActionResult.Ok -> {
        // 被切到别的 App(不是目标 App 自己调起的付款页之类):付款申报就此作废
        if (r.appSwitched != null && !r.switchedByTarget) refusedPay = null
        val notes = notesOf(r)
        ToolExecution(okObj {
            r.changed?.let { put("changed", it) }
            r.appSwitched?.let { put("app_switched", it) }
            if (r.appSwitched != null && r.switchedByTarget) put("opened_by_target", true)
            if (r.relocated) put("relocated", true)
            r.relocatedTo?.takeIf { r.relocated }?.let { put("relocatedTo", it) }
            if (r.settled == false) put("settled", false)
            r.coveredBy?.let { put("coveredBy", it) }
            r.chooser?.let { put("chooser", it.toJson()) }
            r.opened?.let { put("opened", it.toJson()) }
            r.inProfile?.let { put("inProfile", it) }
            r.profileUser?.let { put("profileUser", it) }
            r.requestedUser?.let { wanted -> r.profileUser?.let { put("requestedCopy", it == wanted) } }
            if (notes.isNotEmpty()) put("hint", notes.joinToString(" "))
        }, changed = r.changed)
    }
    is ActionResult.Failed -> failWith(r)
}

/** 一次成功动作后要捎给模型的话:没变化、被切走、没静下来、被遮住、开到别处、系统提问,外加分身说明与端口自带提示。 */
private fun ToolContext.notesOf(r: ActionResult.Ok): List<String> = listOfNotNull(
    HINT_TAP_UNCHANGED.takeIf { r.changed == false && !reportsEffect && r.opened == null },
    // 开到用户主屏上又挪回来的,由下面那句说清,这里不再提被切走
    r.appSwitched?.takeIf { r.opened == null }?.let { switchHint(it, r.switchedByTarget) },
    HINT_NOT_SETTLED.takeIf { r.settled == false },
    r.coveredBy?.let { Feedback.format("covered_by", "pkg" to it) },
    r.opened?.let { openedHint(it) },
    // 系统在用户屏上发问的,留给用户选:不说该怎么选
    r.chooser?.takeIf { r.opened?.picker != true }?.let { Feedback.format("launch.chooser", "pkg" to it.packageName) },
) + profileNotes(r) + listOfNotNull(r.hint)

/** 端口报的失败翻成工具失败:reason 是 ref_expired 按编号退役办,形如错误码的当错误码用,其余统一 action_failed。 */
private fun ToolContext.failWith(r: ActionResult.Failed): ToolExecution {
    if (r.reason == "ref_expired") return refExpired(Feedback["ref_expired"], "element_lost")
    val code = r.reason.takeIf { Regex("^[a-z][a-z0-9_]*$").matches(it) } ?: "action_failed"
    val message = FAILURE_MESSAGES[code] ?: Feedback.format("action_failed", "reason" to r.reason)
    return fail(code, r.hint?.let { "$message $it" } ?: message)
}

/** 分身、工作资料那一份的说明:开出的正是要的那份,不必再提;开出的不是要的那份,照实说。 */
private fun profileNotes(r: ActionResult.Ok): List<String> = listOfNotNull(
    (r.opened?.inProfile ?: r.inProfile)?.takeIf { it == OpenedElsewhere.OTHER_PROFILE && r.requestedUser == null }?.let { Feedback["profile.other"] },
    r.requestedUser?.takeIf { r.profileUser != null && r.profileUser != it }?.let { wanted ->
        if (r.inProfile == OpenedElsewhere.MAIN_PROFILE) Feedback["profile.main_not_requested"]
        else Feedback.format("profile.copy_not_requested", "user" to "${r.profileUser}", "wanted" to "$wanted")
    },
)

/**
 * 动作让 App 开到用户主屏上时,给模型的那句话:挪回来了,就说在 Maeve 的屏上接着做、别再点一次;系统在用户屏上问打开哪一个的,
 * 说留给用户选、别再点、也别替用户选,下一个动作会等用户选完;用户关了那个问题、用户亲手处理完了的,照实说;
 * 要用户亲手处理的弹窗(登录验证一类)留在用户屏上,说别挪回来、请用户在那里点;留在用户屏上没动的(用户正用着),
 * 说别再来、请用户处理。
 */
internal fun openedHint(o: OpenedElsewhere): String {
    val who = o.app ?: o.packageName
    return when {
        o.closed -> Feedback["spill.picker_closed"]
        o.byUser -> Feedback.format("spill.user_done", "app" to who)
        o.picker && o.onUserScreen -> Feedback.format("spill.user_picking", "app" to who)
        o.needsUser && o.onUserScreen -> Feedback.format("spill.user_popup", "app" to who)
        o.onUserScreen -> Feedback.format("spill.in_use", "app" to who)
        else -> Feedback.format("spill.pulled_back", "app" to who)
    }
}

/**
 * 稳定编号开着时,先把模型编号翻成端口编号再交给 [act]:编号已退役的返回 ref_expired,不执行;
 * 旧一屏的编号仍在最新一屏上的,照常执行并标上 relocated。运行记录里另记端口编号。
 */
internal suspend fun ToolContext.withPortRef(ref: Pair<String, String>?, act: suspend (Pair<String, String>?) -> ToolExecution): ToolExecution {
    val stable = session.stabilizer
    if (stable == null || ref == null) return act(ref)
    val resolved = when (val r = stable.resolve(ref.first, ref.second)) {
        is Resolved.Current -> r
        is Resolved.Stale -> return refExpired(Feedback["ref_expired"], r.cause.wire)
    }
    return act(resolved.portRef to resolved.portSnapshotId).renumbered(resolved)
}

/** 结果改记端口编号;执行成功且编号搬过家时,payload 里同时标上 relocated。 */
private fun ToolExecution.renumbered(resolved: Resolved.Current): ToolExecution {
    val payload = if (resolved.relocated && content["ok"] == JsonPrimitive(true)) JsonObject(content + ("relocated" to JsonPrimitive(true))) else content
    return ToolExecution(payload, changed = changed, outcome = outcome, frame = frame, images = images,
        record = JsonObject(record + buildJsonObject { put("portRef", resolved.portRef); put("portSnapshotId", resolved.portSnapshotId) }))
}

/** 坐标换算用的那一屏:最近一次读屏,还没读过就现读一次。 */
internal suspend fun ToolContext.lastOrSnapshot(): ScreenSnapshot = session.lastSnapshot ?: session.snapshot()

/** 确认申报的 app,按当前那块屏的写法校验(手机上是包名,网页上是站点主机名)。 */
internal fun ToolContext.confirmTarget(app: String): String = surface.declaredTarget(app)
