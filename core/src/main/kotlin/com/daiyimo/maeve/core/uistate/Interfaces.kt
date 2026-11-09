package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.replay.Anchors
import com.daiyimo.maeve.core.replay.Selector
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.LOCATED_REF
import com.daiyimo.maeve.core.tool.SurfaceLayer
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.tool.ToolConfig
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.ToolIndex
import com.daiyimo.maeve.core.tool.Until
import com.daiyimo.maeve.core.tool.UntilOutcome
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.argStr
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.failCode
import com.daiyimo.maeve.core.tool.writesScreen
import com.daiyimo.maeve.core.visual.Shots
import com.daiyimo.maeve.core.vision.LocateResult
import com.daiyimo.maeve.core.vision.Locator
import com.daiyimo.maeve.core.vision.PointAudit
import com.daiyimo.maeve.core.vision.TapVisionCheck
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 一次动作究竟落在哪里,由界面一侧给出;执行入口判不可逆、走审批时只读这里的数:
 * [args] 是拿来判定的参数(点看图找到的 @vN 时,已换成它登记的坐标与所见),[aimed] 是模型那一眼对应的读屏(按 snapshotId 找到的,
 * 没给编号时就是坐标换算要用的那一屏),[bySight] 说明落点来自看图,[seen] 是视觉模型复看时确认到的文字,
 * [readAgeMs] 是那一屏距今多久(不清楚就 null)。[now] 是此刻重新看一眼(只在判不可逆时读,不登记编号)。
 */
class ActionTarget internal constructor(
    val args: JsonObject,
    val aimed: ScreenSnapshot?,
    val bySight: Boolean,
    val seen: String?,
    val readAgeMs: Long?,
    private val look: suspend () -> ScreenSnapshot?,
) {
    suspend fun now(): ScreenSnapshot? = look()
}

/** 按描述指目标时给的参数:元素显示的字、名字或输入框的提示语;角色可另配。 */
const val DESCRIBED_ELEMENT = "element"
const val DESCRIBED_ROLE = "role"
/** 和描述一起给就不知道按谁来了。只有坐标算真冲突;编号/快照不过是模型捎带的提示,按描述重读一屏即可,犯不着为此停一步。 */
private val DESCRIBED_CONFLICTS = listOf("x", "y")
/** 页面弹框正开着时的错误码(参见 cap/web 的 DIALOG_OPEN)。 */
private const val DIALOG_OPEN_CODE = "dialog_open"
/** 对得上的不止一个时,最多列出几个。 */
private const val DESCRIBED_LISTED = 6
/** 元素名字长到这个数,多半是屏上几段文字拼起来的,几何中心不可靠,点它之前要视觉核查。 */
private const val SUSPICIOUS_NAME_CHARS = 20
/** reason 里被引号括住的名字(中英文引号都认):模型写「点击"更多热搜"看榜单」时,更多热搜 就是它自报的期望。 */
private val QUOTED_LABEL = Regex("[\"“”]([^\"“”]{1,40})[\"“”]")
/** 树里对不上就改看图找的动作:打字找输入框、点击找要点的东西(仅当树缺了一块或不能用时才兜底)。 */
private val SIGHT_FALLBACK_VERBS = setOf(Verb.TypeText, Verb.Tap)
/** 看图找过了却没找到或没确认的原因(剩下的都是画面拿不到、不能看)。 */
private val SIGHT_MISSED = setOf("not_found", "name_mismatch", "not_confirmed", "unverified_grounding", "vision_unavailable")

/** 按描述找目标的结果:[Resolved] 带上换成编号后的参数与找到的元素,[Failed] 是交给模型的失败结果。 */
sealed interface Described {
    /**
     * [note]:按开头或包含对上时,附进动作结果的说明(描述了什么、实际对上了谁);名字完全相同则为 null。
     * [element] 为 null:树里没找到,是看图找到的点(参数已换成 @vN)。
     */
    class Resolved(val args: JsonObject, val element: UiElement?, val note: JsonObject? = null) : Described
    class Failed(val result: ToolExecution) : Described
}

/**
 * 一个任务里,有界面的能力(手机、网页)共用的这份界面状态:每块屏一份读屏状态、此刻在操作哪块屏、给执行入口看的动作目标、路线要的目标与锚点。
 * 分发器每次调用前后都经它收尾界面上的事(动作前看一眼、比出实际效果、附上动作之后的界面、清掉看图得来的点);
 * 分发器与核心的循环都不直接认识它,只通过 [SurfaceLayer] 使唤。开局时由 [attach] 放进工具上下文,有界面的工具从那里取。
 */
internal class Interfaces(
    surfaces: List<Surface>,
    stableRefs: Boolean,
    private val config: ToolConfig,
    val shots: Shots,
    /** 最近一次读到的界面文字(几块屏共用);动作之后读到的若与它相同,就不再记一遍。 */
    lastRead: () -> String?,
) : SurfaceLayer {
    companion object {
        /**
         * 建出本任务的界面状态:[surfaces] 是接上了端口的屏,[stableRefs] 开着时跨读屏沿用稳定编号(动作自带屏幕、元素优先时总是开着)。
         * 存进 [ctx],有界面的工具从那里取用。
         */
        fun attach(ctx: ToolContext, surfaces: List<Surface>, stableRefs: Boolean): Interfaces =
            Interfaces(surfaces, stableRefs || ctx.config.inlineScreen || ctx.config.adaptiveVision, ctx.config, ctx.shots) {
                ctx.evidence.recentReads.lastOrNull()
            }.also { ctx[INTERFACES] = it }
    }

    val focus = SurfaceFocus(surfaces)
    val sessions: Map<String, ScreenSession> = focus.surfaces.associate { s ->
        s.id to ScreenSession(s, stableRefs, config.visionEnabled, config.recordFrames, lastRead, shots, config.vision, config.adaptiveVision,
            config.vision?.takeIf { config.tapVisionCheck }?.let { TapVisionCheck(it) })
    }
    private val gate = GateView()

    /** 点击之前的视觉锁定(核查落点);没有视觉模型或视觉核查关着时为 null。 */
    private val tapAudit = config.vision?.takeIf { config.tapVisionCheck }?.let { TapVisionCheck(it) }

    suspend fun checkAppAccess(tool: AgentTool?, after: Boolean = false) {
        if (tool == null || tool.offScreen || tool.capability !in sessions || tool.verb == Verb.ListApps) return
        sessionOf(tool).checkAppAccess(current = after || tool.verb !in APP_TARGET_VERBS)
    }

    /** 此刻在操作的那块屏,它的读屏状态。 */
    val session: ScreenSession get() = sessions.getValue(focus.current.id)

    override val current: String get() = focus.current.id

    override fun enter(id: String?) = focus.enter(id)

    override fun leave(succeeded: Boolean) = focus.leave(succeeded)

    /** 开着时,写操作执行前取下目标元素与锚点(回放开关打开时由循环来设)。 */
    override var captureTargets: Boolean = false

    /** 工具 [tool] 所在那块屏的读屏状态;不是屏幕工具时,取最近操作的那块。 */
    private fun sessionOf(tool: AgentTool?): ScreenSession = tool?.capability?.let(sessions::get) ?: session

    /** 这个工具作用在哪块屏;不是屏幕工具时为 null。 */
    override fun surfaceOf(tool: AgentTool?): String? = tool?.capability?.takeIf { it in sessions }

    /**
     * 拿另一块屏的编号来操作这块屏的工具:给出 wrong_surface,讲清编号归哪块屏、该换哪个工具。
     * 认不出编号属于哪块屏(过期太久、拼错)时返回 null,照常交给工具(按过期处理)。
     */
    override fun misaimed(tool: AgentTool, args: JsonObject, tools: ToolIndex): ToolExecution? {
        val mine = tool.capability?.let(sessions::get) ?: return null
        val snapshotId = args.argStr("snapshotId") ?: return null
        if (mine.owns(snapshotId)) return null
        val holder = sessions.values.firstOrNull { it.owns(snapshotId) }
            ?: sessions.values.firstOrNull { s -> snapshotId.startsWith(s.surface.family.snapshotPrefix) && snapshotId.drop(s.surface.family.snapshotPrefix.length).firstOrNull()?.isDigit() == true }
            ?: return null
        if (holder === mine) return null
        return fail("wrong_surface", Feedback.format("wrong_surface",
            "id" to snapshotId, "owner" to holder.surface.id, "tool" to tool.name, "surface" to mine.surface.id,
            "instead" to (tools.counterpart(tool.name, holder.surface.id) ?: tools.nameOf(Verb.Observe, holder.surface.id) ?: holder.surface.id),
            "observe" to (tools.nameOf(Verb.Observe, mine.surface.id) ?: tool.name)))
    }

    /** 一次调用之前,在界面上备好的:路线要的目标、改变界面的动作前的那一眼。最后由 [finishCall] 收尾。 */
    private class CallPrep(
        val session: ScreenSession,
        val probe: ScreenSession.EffectProbe?,
        val aimed: Pair<UiElement?, List<Int>?>?,
    ) {
        /** 这次调用的实际效果(effect)由界面一侧来报。 */
        val reportsEffect: Boolean get() = probe != null
    }

    override suspend fun around(tool: AgentTool, args: JsonObject, inlineScreen: Boolean, body: suspend (reportsEffect: Boolean) -> ToolExecution): ToolExecution {
        if (tool.offScreen || tool.verb == Verb.ListApps) return body(false)
        // 按目标启动得以离开受限应用;这时不采集旧页面,只在启动之后附上放行的页面。
        if (tool.verb in APP_TARGET_VERBS) {
            val raw = body(false)
            return finishCall(tool, args, CallPrep(session, null, null), raw, inlineScreen)
        }
        val prep = prepareCall(tool, args)
        preTapAudit(tool, args, prep)?.let { return it }
        return finishCall(tool, args, prep, body(prep.reportsEffect), inlineScreen)
    }

    /**
     * 点击之前的视觉锁定(见 [TapVisionCheck.auditPoint]):落点上是不是真要点的那个东西。要查的落点分三类——
     * 按编号去点,元素却是大容器、名字为空或长到像是屏上几段字拼起来的(几何中心靠不住,点下去多半点空);
     * 模型自报了标签(「要点更多热搜」),树上那个元素却没有名字(纯凭几何猜的,再小也查);
     * 以及给了 target 的坐标点击(坐标是模型自己算的);全量预检开着([com.daiyimo.maeve.core.tool.ToolConfig.tapVisionCheckAll])时,次次点击都查。
     * 视觉说落点落在空白处、或那儿的文字与期望的不是一个东西 → 不点,按失败交回去,让模型换个做法。
     * 看不了时(没有视觉模型、截图拿不到、受保护、出错)不拦路,照旧点。
     */
    private suspend fun preTapAudit(tool: AgentTool, o: JsonObject, prep: CallPrep): ToolExecution? {
        if (tool.verb != Verb.Tap || !config.tapVisionCheck) return null
        val audit = tapAudit ?: return null
        val shot = prep.probe?.png ?: return null
        val (x, y, wanted) = auditPoint(tool, o, prep) ?: return null
        return when (val checked = audit.auditPoint(shot, x, y, wanted)) {
            is PointAudit.Ok, is PointAudit.Unavailable -> null
            is PointAudit.Blank -> fail("tap_point_blank", Feedback["failure.tap_point_blank"])
            is PointAudit.OffTarget -> fail("tap_point_off_target", Feedback.format("failure.tap_point_off_target",
                "at" to checked.at.take(40), "want" to (wanted?.take(40) ?: "")))
        }
    }

    /**
     * 这次点击要查的落点(0–999),以及期望落点处的文字:按编号点的可疑元素 → 它的几何中心与元素名(没名字时用模型自报的标签);
     * 给了 target 的坐标点击 → 坐标与 target。不用查的返回 null(看图得来的 @vN 有自己的截图上变校验,不在这里重查一遍)。
     */
    private fun auditPoint(tool: AgentTool, o: JsonObject, prep: CallPrep): Triple<Int, Int, String?>? {
        val modelRef = o.argStr("ref")?.takeIf { !LOCATED_REF.matches(it) }
        if (modelRef != null) {
            val element = prep.aimed?.first ?: return null
            val bounds = element.bounds.takeIf { it.size == 4 } ?: return null
            val wanted = element.name.takeIf { it.isNotBlank() } ?: statedWanted(o)
            if (!config.tapVisionCheckAll && !suspectTarget(element, bounds, wanted)) return null
            return Triple((bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2, wanted)
        }
        fun number(k: String) = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        val x = number("x") ?: return null
        val y = number("y") ?: return null
        val wanted = o.argStr("target")?.takeIf { it.isNotBlank() } ?: return if (config.tapVisionCheckAll) Triple(x, y, null) else null
        return Triple(x, y, wanted)
    }

    /** 落点靠不住的点击目标:占了至少四分之一屏,名字却为空或长到像是屏上几段文字拼起来的;
     *  以及模型自报了标签(「要点更多热搜」)、树上那个元素却没有名字——正是凭几何猜的那类,再小也查。 */
    private fun suspectTarget(el: UiElement, b: List<Int>, want: String?): Boolean {
        if (want != null && el.name.isBlank()) return true
        val area = (b[2] - b[0]).toLong() * (b[3] - b[1])
        return area >= 999L * 999 / 4 && (el.name.isBlank() || el.name.length > SUSPICIOUS_NAME_CHARS)
    }

    /** 模型自己说这回点的是什么:坐标点击的 target,或 reason 里引号括住的名字(「点击"更多热搜"看榜单」)。 */
    private fun statedWanted(o: JsonObject): String? {
        o.argStr("target")?.takeIf { it.isNotBlank() }?.let { return it }
        val why = o.argStr("reason")?.takeIf { it.isNotBlank() } ?: return null
        return QUOTED_LABEL.find(why)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private suspend fun prepareCall(tool: AgentTool, o: JsonObject): CallPrep {
        val session = this.session
        gate.capture = if (captureTargets && tool.routable) gate.captureTarget(session, tool.verb, o) else null
        // 改变界面的动作:动作前先看一眼(元素表 + 画面),动作后比出实际效果;点击类还会同时截一张图,供点了之后视觉核对
        val probe = if (tool.reportsEffect) session.probe(o.argStr("snapshotId"), withPng = wantsTapPng(tool)) else null
        val aimed = probe?.let { aimTarget(session, tool.verb, o, it.before) }
        return CallPrep(session, probe, aimed)
    }

    /** 点击类动作、视觉核查开着、也有视觉模型:动作前截一张图,点了之后给视觉核查当「之前」用。 */
    private fun wantsTapPng(tool: AgentTool): Boolean =
        tool.verb == Verb.Tap && config.tapVisionCheck && config.vision != null

    /** 调用不在界面上(或没走到 [prepareCall]):路线要的目标不留。 */
    override fun skip() { gate.capture = null }

    /**
     * 一次调用之后:动作成功的,按 [inlineScreen] 附上界面差异;画面按观察策略附上,同时报回实际效果;
     * 动过这块屏之后(失败的也算,参数不对、没执行的不算),这块屏上原先看图得来的点不再可信。
     */
    private suspend fun finishCall(tool: AgentTool, o: JsonObject, prep: CallPrep, raw: ToolExecution, inlineScreen: Boolean): ToolExecution {
        val session = prep.session
        session.probeDone()
        val readsAtAction = session.reads
        var outcome = raw
        val isOk = outcome.content["ok"] == JsonPrimitive(true)
        if (inlineScreen && tool.showsScreenAfter && isOk) outcome = session.withScreen(outcome)
        // 旧模式下视觉主模型直接看动作后的画面;分级模式只在界面差异或下次 observe 需要时才补图。
        if (tool.showsScreenAfter && isOk && !config.adaptiveVision && outcome.images.isEmpty() && "screen" !in outcome.content) outcome = session.withFrame(outcome)
        val probe = prep.probe
        val aimed = prep.aimed
        if (isOk && probe != null && aimed != null) {
            val typed = if (tool.verb == Verb.TypeText) o.argStr("text") else null
            outcome = session.withEffect(outcome, probe, tool.verb, readsAtAction, aimed.first, aimed.second, typed)
        }
        if (tool.writesScreen && outcome.content["error"] != JsonPrimitive("invalid_arguments")) session.clearLocated()
        return outcome
    }

    /**
     * 判效果用的目标:按编号的动作,取模型那一屏里的那个元素;输入、清空没给编号时,取动作前拿着焦点的输入框;
     * 画面区域即目标的范围,坐标动作(含看图找到的点)取落点周围一小块。两者都没有时为 (null, null)。
     */
    private fun aimTarget(session: ScreenSession, verb: Verb, o: JsonObject, before: ScreenSnapshot?): Pair<UiElement?, List<Int>?> {
        val modelRef = o.argStr("ref")?.takeIf { !LOCATED_REF.matches(it) }
        val element = when {
            modelRef != null -> gate.snapshotFor(session, o)?.elements?.firstOrNull { it.ref == modelRef }
            verb == Verb.TypeText || verb == Verb.ClearText -> before?.elements?.firstOrNull { it.focused && it.editable }
            else -> null
        }
        fun number(a: JsonObject, k: String) = (a[k] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()?.toInt()
        val at = gate.gateArgs(session, verb, o).let { a -> number(a, "x")?.let { x -> number(a, "y")?.let { y -> x to y } } }
        val region = element?.bounds?.takeIf { it.size == 4 } ?: at?.let { (x, y) ->
            val d = EffectTuning.POINT_REGION
            listOf((x - d).coerceAtLeast(0), (y - d).coerceAtLeast(0), (x + d).coerceAtMost(999), (y + d).coerceAtMost(999))
        }
        return element to region
    }

    /**
     * 按描述指目标的点击、打字(给 [DESCRIBED_ELEMENT] 参数,且没有编号和坐标):此刻重读一屏(登记编号),找唯一对上的那个元素,
     * 把参数换成它的 ref + snapshotId,往后的判定、审批、执行、核实都与按编号的一般无二。
     * 点击或打字撞上树缺了一块、树不能用、树里没对得上的,就改看图找(见 [resolveBySight])。
     * 不是这类调用时返回 null;找不到、不止一个、参数冲突时,交给模型的是失败结果,不执行。
     */
    suspend fun described(tool: AgentTool?, args: JsonObject): Described? {
        val verb = tool?.verb
        if (verb != Verb.Tap && verb != Verb.TypeText) return null
        val session = tool.capability?.let(sessions::get) ?: return null
        val wanted = args.argStr(DESCRIBED_ELEMENT)
        if (wanted == null) {
            if (DESCRIBED_ROLE in args) return Described.Failed(fail("invalid_arguments", "$DESCRIBED_ROLE: only used together with $DESCRIBED_ELEMENT"))
            return null
        }
        DESCRIBED_CONFLICTS.firstOrNull { it in args }?.let {
            return Described.Failed(fail("invalid_arguments", "$DESCRIBED_ELEMENT: give either $DESCRIBED_ELEMENT or $it, not both"))
        }
        val roleFilter = args.argStr(DESCRIBED_ROLE)?.trim()?.ifEmpty { null }
        // 内部读的一屏:模型没瞧见,不算它读过(不动「手里那屏已过时」);这一屏留给接下来的动作当「动作前」
        val fresh = screenAttempt { session.snapshot(internal = true) }
            ?: return Described.Failed(fail("screen_unavailable", Feedback["screen_unavailable"]))
        // 页面弹框正挂着:页面钉死了,什么都点不到
        if (fresh.web?.dialog != null) {
            session.clearPrimed()
            return Described.Failed(failCode(DIALOG_OPEN_CODE))
        }
        val observeTool = session.surface.family.tool("observe")
        val shownText = wanted.trim().take(80)
        val match = DescribedTarget.match(fresh.elements, ElementQuery(wanted, roleFilter, field = verb == Verb.TypeText))
        if (match !is DescribedMatch.Found) session.clearPrimed()
        return when (match) {
            is DescribedMatch.Found -> Described.Resolved(
                JsonObject(args.filterKeys { it != DESCRIBED_ELEMENT && it != DESCRIBED_ROLE } +
                    mapOf("ref" to JsonPrimitive(match.element.ref), "snapshotId" to JsonPrimitive(fresh.snapshotId))),
                match.element,
                // 名字并非完全相同:如实讲出按描述对上的是哪一个
                if (match.exact) null else JsonObject(mapOf("resolvedFrom" to JsonPrimitive(shownText), "matched" to JsonPrimitive(match.element.name.take(80)),
                    "matchedRef" to JsonPrimitive(match.element.ref), "snapshotId" to JsonPrimitive(fresh.snapshotId))),
            )
            is DescribedMatch.Ambiguous -> Described.Failed(fail("element_ambiguous", Feedback.format("described.ambiguous",
                "text" to shownText, "count" to match.candidates.size, "snapshot" to fresh.snapshotId,
                "candidates" to match.candidates.take(DESCRIBED_LISTED).joinToString("; ") { candidateLine(it, verb, fresh.elements) })))
            DescribedMatch.None -> {
                // 点击、打字偏偏这一屏的树缺了一块或不能用:树里对不上,不等于屏上没有,改看图找(输入框或要点的那个)
                val vision = session.locator
                if (verb in SIGHT_FALLBACK_VERBS && vision != null && session.treeBlind(fresh)) resolveBySight(session, vision, args, shownText)
                else Described.Failed(fail("element_not_found", Feedback.format("described.none",
                    "text" to shownText, "role" to (roleFilter?.let { " with role $it" } ?: ""), "observe" to observeTool)))
            }
        }
    }

    /** 「不止一个」提示里候选元素的一行:编号、角色、名字、位置,再捎上邻居提示与可不可动。 */
    private fun candidateLine(e: UiElement, verb: Verb, all: List<UiElement>): String {
        val near = DescribedTarget.neighbour(e, all)?.let { " near 「${it.take(30)}」" }.orEmpty()
        val off = if (DescribedTarget.usable(e, verb == Verb.TypeText)) "" else " (not actionable)"
        return "${e.ref} ${e.role} 「${e.name.take(40)}」 at ${e.bounds}$near$off"
    }

    /**
     * 看图找按描述指的目标(输入框或要点的那个;与 find_visually 同一套核对:红框复看,没佐证的标出来),参数换成找到的 @vN:
     * 往后与模型自己传 @vN 一样:先核对画面没变,那一下点击要过不可逆判定,打字后按屏上文字核对点没点中。
     */
    private suspend fun resolveBySight(session: ScreenSession, locator: Locator, args: JsonObject, shown: String): Described =
        when (val r = locator.locate(shown, null)) {
            // 看了却没找到(或看图这一下出错):换个说法,或给个坐标;画面拿不到、不能看(受保护、还没开 App)时,说明与 find_visually 一致
            is LocateResult.NotFound -> Described.Failed(fail("element_not_found", Feedback.format("described.not_seen",
                "text" to shown, "reason" to r.reason,
                "next" to if (r.reason in SIGHT_MISSED) Feedback["described.not_seen.retry"] else r.message)))
            is LocateResult.Found -> {
                session.locatedInfo[r.ref] = Located(shown, null, r.seen, r.unverified)
                Described.Resolved(
                    JsonObject(args.filterKeys { it != DESCRIBED_ELEMENT && it != DESCRIBED_ROLE } +
                        mapOf("ref" to JsonPrimitive(r.ref), "snapshotId" to JsonPrimitive(r.snapshotId))),
                    null,
                    JsonObject(listOfNotNull("resolvedFrom" to JsonPrimitive(shown), "foundBySight" to JsonPrimitive(true),
                        r.seen?.let { "seen" to JsonPrimitive(it) }).toMap()),
                )
            }
        }

    /** 给执行入口看的这一步的目标(见 [ActionTarget]);只读,不执行任何动作。 */
    fun actionTarget(tool: AgentTool?, args: JsonObject): ActionTarget {
        val session = sessionOf(tool)
        val verb = tool?.verb
        val gated = gate.gateArgs(session, verb, args)
        return ActionTarget(gated, gate.snapshotFor(session, gated), gate.visionSourced(session, verb, args),
            gate.confirmedSeen(session, verb, args), readAgeOf(args.argStr("snapshotId")), ::peek)
    }

    /** 那次读屏距今多少毫秒;不在任何一块屏最近的几次里时为 null。 */
    private fun readAgeOf(snapshotId: String?): Long? = sessions.values.firstNotNullOfOrNull { s ->
        s.recentSnapshots.firstOrNull { it.snapshotId == snapshotId }?.let { snap -> s.readAt[snap.snapshotId]?.let { System.currentTimeMillis() - it } }
    }

    /** 那次读屏(按工具 [tool] 那块屏、参数里的 snapshotId 去找);不在最近几次里时为 null。 */
    fun snapshotFor(tool: AgentTool?, args: JsonObject): ScreenSnapshot? = gate.snapshotFor(sessionOf(tool), args)

    /** 给运行记录用:模型编号此刻对应的端口编号;稳定编号关着、没有 @eN 或编号过期时为空。 */
    override fun portRecord(tool: AgentTool?, args: JsonObject): JsonObject = gate.portRecord(sessionOf(tool), args)

    /** 上一次调用是写操作时,它的目标元素(没有或认不出时为 null)与动作前的锚点;否则为 null。 */
    fun lastActionTarget(): Pair<Selector?, Anchors>? = gate.capture?.let { it.target to it.before }

    /** 上一次写操作之前的那一屏,受系统防截屏保护。 */
    fun lastActionProtected(): Boolean = gate.capture?.snapshot?.protected == true

    /** 上一次写操作之后,此刻读一屏(不登记编号),取动作后新出现的文字;不是写操作或读不到时为 null。 */
    suspend fun actionAfter(): Anchors? {
        if (gate.capture == null) return null
        return peek()?.let(::anchorsAfter)
    }

    /** 同上,只是用调用方已经读好的那一屏。 */
    fun anchorsAfter(after: ScreenSnapshot): Anchors? = gate.anchorsAfter(after)

    /** 此刻重读最近操作的那块屏,不登记编号、也不改模型手里的读屏;读不到时为 null。 */
    suspend fun peek(): ScreenSnapshot? = session.peek()

    /** 等最近操作的那块屏稳定,最多等 [timeoutMs];读不到状态也照常返回。 */
    override suspend fun settle(timeoutMs: Long) {
        screenAttempt { session.screen.waitStable(timeoutMs) }
    }

    /** 最近操作的那块屏此刻的一帧(原图)与它的画面编号,登记进回看;受保护页面、付款页不截,截不到为 null。 */
    override suspend fun visibleText(): List<String> = peek()?.elements?.map { it.name }.orEmpty()

    override suspend fun waitUntil(until: Until, clock: () -> Long, interrupted: () -> Boolean): UntilOutcome =
        UntilWait(session.screen, ::peek, clock, interrupted).run(until)

    override suspend fun imageNow(): Pair<ByteArray, Int>? = session.currentFrame()

    /** 主模型能看图时,动作之后发的那一屏:画面与元素表是同一时刻的;屏上还没有 App 时为 null。 */
    override suspend fun viewNow(): ToolDispatcher.Glance? = session.currentView()

    /** 模型此刻还看得到哪些读屏(裁剪、压缩之后);差异的 base 不在其中时,发全量。 */
    override fun setVisible(ids: Set<String>) { sessions.values.forEach { it.visible = ids } }

    /** 运行状态里写的这块屏此刻的版本(网页的手机版、电脑版);没有或读不到时为 null。 */
    override suspend fun mode(): String? = focus.surfaces.firstNotNullOfOrNull { it.mode }?.invoke()
}

private val APP_TARGET_VERBS = setOf(Verb.LaunchApp, Verb.OpenLink, Verb.ShareText, Verb.SetAlarm, Verb.SetTimer, Verb.ShowAlarms, Verb.AddCalendarEvent)
