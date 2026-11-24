package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.gate.Irreversible
import com.daiyimo.maeve.core.guard.DIAGNOSTIC_VERBS
import com.daiyimo.maeve.core.guard.GuardVerdict
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.guard.PAGE_READ_VERBS
import com.daiyimo.maeve.core.guard.GuardScope
import com.daiyimo.maeve.core.guard.readFingerprint
import com.daiyimo.maeve.core.guard.screenFingerprint
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.Redactor
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.replay.Anchors
import com.daiyimo.maeve.core.replay.effectiveWrite
import com.daiyimo.maeve.core.uistate.Described
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.interfaces
import com.daiyimo.maeve.core.tool.ActionDelivery
import com.daiyimo.maeve.core.tool.ActionPass
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.parseArgs
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.daiyimo.maeve.core.tool.SubActions
import com.daiyimo.maeve.core.guard.OffScreenGuard
import com.daiyimo.maeve.core.run.RunProvenance
import com.daiyimo.maeve.core.run.SKIPPED_FOR_NEW_MESSAGE
import com.daiyimo.maeve.core.run.waitOrSteer
import com.daiyimo.maeve.core.tool.failCode
import com.daiyimo.maeve.core.capability.Paced

/**
 * 所有改变外部世界的动作唯一的执行入口:[ToolDispatcher.execute] 要一张 [ActionPass],只有这里有。
 * 每个来源都按同一顺序:操作守卫 → 锁屏/被拿走的关卡(写操作)→ 不可逆判定与同意核对(按过关卡之后此刻的屏幕)→
 * 执行 → 守卫核对(失败熔断、画面变化、重读)→ 运行记录。来源之间有意的不同只写在 [OriginPolicy] 里。
 */
class ActionExecutor(
    private val tools: ToolDispatcher,
    private val guards: OperationGuards,
    confirmations: ConfirmationGate?,
    private val gate: suspend () -> Unit,
    val recorder: ActionRecorder,
    /** 不动屏幕的工具的守卫;没有为 null。 */
    private val offScreen: OffScreenGuard? = null,
    private val approvals: ApprovalSettings = ApprovalSettings.ASK,
    private val budget: com.daiyimo.maeve.core.run.RunBudget? = null,
    provenance: RunProvenance = RunProvenance(),
    selfPackage: String? = null,
    autonomous: Boolean = false,
    /** 同一网站、App 里连着做对外动作(发送、提交)时两次之间的间隔(见 [OutwardPacing]);默认不等。 */
    private val pacing: OutwardPacing = OutwardPacing.Off,
    private val effects: ActionEffects? = null,
    private val onActionResult: (Verb?, JsonObject, ToolExecution, Int) -> Unit = { _, _, _, _ -> },
    /** 主屏锁屏代次;等待后代次变化说明按旧屏幕准备的目标必须作废。 */
    private val screenGeneration: () -> Long = { 0L },
) {
    private val decision = ConfirmDecision(tools, confirmations, approvals, recorder = recorder, provenance = provenance, selfPackage = selfPackage, autonomous = autonomous)
    private val pass = ActionPass()

    /** 配了确认关卡(付款能经用户同意后做)。 */
    val confirms: Boolean = confirmations != null

    /** 不动屏幕的工具的守卫叫停的结论;没叫停为 null。 */
    val offScreenStop: GuardVerdict? get() = offScreen?.stop

    suspend fun run(call: Part.ToolCall, origin: Origin, followUp: FollowUp = FollowUp.NONE): ActionOutcome =
        run(call, origin, followUp, reads = null)

    /** [reads]:页面脚本一次调用里的读单独计数的范围(来源要求隔离时);那里的动作仍按任务计。 */
    private suspend fun run(call: Part.ToolCall, origin: Origin, followUp: FollowUp, reads: GuardScope?): ActionOutcome {
        val timing = ActionTiming { System.nanoTime() / 1_000_000 }
        try {
            return withContext(timing) { runMeasured(call, origin, followUp, reads) }
        } finally {
            recorder.event(RunEvent.ActionTiming(recorder.now(), origin.step, call.name, call.id,
                origin.policy.source, timing.elapsed(), timing.phases()))
        }
    }

    private suspend fun runMeasured(call: Part.ToolCall, origin: Origin, followUp: FollowUp, reads: GuardScope?): ActionOutcome {
        if (!runCatching { kotlinx.serialization.json.Json.parseToJsonElement(call.argsJson) is JsonObject }.getOrDefault(false)) {
            return ran(Step(call, JsonObject(emptyMap()), origin, emptyList(), writing = false, tools.offScreen(call.name), tools.verb(call.name), null, followUp, reads,
                preset = ToolExecution(buildJsonObject { put("ok", false); put("error", "invalid_arguments"); put("message", "arguments: not valid JSON object"); put("recoverable", true); put("executed", false); put("delivery", "not_sent") }, delivery = ActionDelivery.NotSent)))
        }
        val args = parseArgs(call.argsJson)
        // 不动屏幕的工具(沙箱一类):不进屏幕守卫,由各自的守卫另管
        val offScreen = tools.offScreen(call.name)
        val verb = tools.verb(call.name)
        // 用了另一块屏的编号的:不判不做、不记成在操作哪块屏,直接交给分发器说明该用哪个工具
        if (tools.misaimed(call.name, args) != null) {
            return ran(Step(call, args, origin, emptyList(), writing = false, offScreen, verb, null, followUp, reads))
        }
        // 之后的关卡、判定都按这次调用的那块屏(到这次调用结束)
        tools.aim(call.name, args)
        try {
            return aimed(call, origin, followUp, reads, args, offScreen, verb)
        } catch (e: com.daiyimo.maeve.core.access.AppAccessDenied) {
            val content = buildJsonObject {
                put("ok", false); put("error", e.code); put("message", e.message); put("retryable", false)
                e.packageName?.let { put("package", it) }
            }
            // 单步:作为这次工具调用的结果交给模型,让它换路线或向用户说明;批和脚本按 Refused 自己处理
            if (origin.policy.recordsRefusal) followUp.refused(content)
            recorder.result(origin.step, call.name, verb, content, content, null, null, 0, origin.policy.source, call.id)
            return ActionOutcome.Refused(e.code, content, emptyList())
        } finally {
            // 成功与否由分发器记;这里只结束这一层
            tools.release(false)
        }
    }

    private suspend fun aimed(
        given: Part.ToolCall, origin: Origin, followUp: FollowUp, reads: GuardScope?, givenArgs: JsonObject, offScreen: Boolean, verb: Verb?,
    ): ActionOutcome {
        // 按描述指的目标先换成此刻那一屏里的编号:之后的判定、审批、执行和按编号的完全一样;找不到或不唯一就不做
        var call = given
        var args = givenArgs
        var note: JsonObject? = null
        val policy = origin.policy
        timedActionPhase("appAccess") { tools.interfaces.checkAppAccess(tools.declared(call.name)) }
        val writing = tools.writesScreen(call.name)
        val preparedGeneration = screenGeneration()
        // 先过门禁再解析目标:开局解锁/恢复后,ref、坐标和按描述找到的控件都必须是新屏幕上的。
        if (writing && !policy.gatedByCaller) timedActionPhase("runGate") { gate() }
        val described = timedActionPhase("resolveTarget") { tools.interfaces.described(tools.declared(call.name), args) }
        when (val d = described) {
            is Described.Failed -> return ran(Step(call, args, origin, emptyList(), writing = false, offScreen, verb, null, followUp, reads, preset = d.result))
            is Described.Resolved -> { args = d.args; call = call.copy(argsJson = d.args.toString()); note = d.note }
            null -> Unit
        }
        val readScope = reads?.takeIf { verb in PAGE_READ_VERBS }
        val before = when {
            offScreen -> emptyList()
            readScope != null -> guards.beforeAction(call.name, givenArgs, verb in DIAGNOSTIC_VERBS, readScope, trackCall = false)
            else -> guards.beforeAction(call.name, givenArgs, verb in DIAGNOSTIC_VERBS, trackCall = reads == null || verb == Verb.WaitFor || verb == Verb.Wait)
        }
        if (guards.stop != null) return refuse("guard_stopped", origin, before)
        if (policy.stopOnGuardWarning && before.isNotEmpty()) return refuse("guard_warning", origin, before)
        // 没有可用的同意就不执行,也不计入失败熔断
        val approvalRevision = approvals.revision()
        var d = timedActionPhase("policy") { decision.judge(call.name, args, origin) }
        // 不在屏上、工具声明要问的一步(会改动东西的 root 命令):调用方问得到人就当场问,同意了按这次同意再判一次;
        // 问不到人(这次运行不能问、调用方不是模型单步)就不做,照被拦下记
        val ask = if (d is Decision.Required || d is Decision.Denied) tools.approvalRisk(call.name, args)?.ask else null
        if (ask != null) {
            val hit = Irreversible(ask.action, ask.content)
            val answer = (if (d is Decision.Required) timedActionPhase("approvalWait") { followUp.approve(ask) } else null)
                ?: return declined(call, args, origin, before, hit, ToolExecution(Refusals.unavailable()), followUp, "approval_unavailable")
            if (answer.content["ok"] != JsonPrimitive(true)) return declined(call, args, origin, before, hit, answer, followUp, "confirmation_declined")
            d = timedActionPhase("policy") { decision.judge(call.name, args, origin) }
        }
        when (d) {
            is Decision.Required -> return blocked(call, args, origin, before, d.hit, null, followUp, d.everyWrite)
            is Decision.Mismatch -> return blocked(call, args, origin, before, d.hit, d.reason, followUp)
            is Decision.Denied -> return approvalUnavailable(call, args, origin, before, d.hit, followUp)
            Decision.Unresolved -> return refusedCall(call, args, origin, before, followUp, "target_unresolved")
            Decision.OwnApp -> return ownApp(call, args, origin, before, followUp)
            Decision.DeniedApp -> return deniedApp(call, args, origin, before, followUp)
            Decision.SecretPage -> return secretPage(call, args, origin, before, followUp)
            else -> Unit
        }
        // 对外动作:和同一网站、App 里的上一次隔开(等的时候任务还能取消,动作还没开始)
        val site = decision.outwardSite
        val paced = site?.let { pacing.waitBefore(it, recorder.now()) }?.takeIf { it > 0 } ?: 0L
        // 等间隔时用户插了话:这次不做,先让模型读那句话。间隔从上一次做完算起,下次再做时余下的照样补上
        if (paced > 0 && timedActionPhase("pacing") { waitOrSteer { delay(paced) } } == null) {
            return ran(Step(call, args, origin, before, writing = false, offScreen, verb, null, followUp, reads, preset = failCode(SKIPPED_FOR_NEW_MESSAGE)))
        }
        // 改动屏幕的动作:按这块屏的能力的节奏先停一会儿(App 里见 AppDwell),单步、一批里的每一步都在这里停;
        // 组合动作(脚本)本身不停,里面的每一步经这里各自停。到了屏数上限、停留中用户插了话就不做
        val screenPaced = writing && !offScreen && !tools.composite(call.name)
        val dwelt = if (screenPaced) timedActionPhase("pacing") { tools.beforeWrite(call.name, outward = site != null) } else Paced.NONE
        dwelt.stop?.let { stop -> return ran(Step(call, args, origin, before, writing = false, offScreen, verb, null, followUp, reads, preset = stop, pacedMs = paced + dwelt.waitMs)) }
        if (approvals.revision() != approvalRevision) return refusedCall(call, args, origin, before, followUp, "approval_revoked")
        if (writing && !policy.gatedByCaller) {
            // 审批、对外动作间隔和 App 停留都可能跨过一次锁屏;真正投递前再过一次门禁并作废旧目标。
            timedActionPhase("runGate") { gate() }
        }
        // 回放的关卡由调用方在读屏前统一过,但这里仍要检查等待期间是否锁屏/解锁。
        if (writing && screenGeneration() != preparedGeneration) return ran(Step(call, args, origin, before, writing = false, offScreen, verb, null, followUp, reads, preset = screenChangedDuringLock()))
        if (writing) {
            guards.noteAction()
            decision.noteWrite(call.name)
        }
        val step = Step(call, args, origin, before, writing, offScreen, verb, (d as? Decision.Traced)?.detail, followUp, reads, note = note,
            pacedMs = paced + dwelt.waitMs, idleScrolls = dwelt.idleScrolls, outward = site != null, approvalDigest = decision.approvalDigest,
            requestedArgs = givenArgs)
        // 动作一旦开始就做完并记下:脚本超时、任务取消都不能把它掐在半中间、漏掉记录
        // 对外动作告诉这块屏的能力(网页的页数上限只管浏览,见 PageDwell)
        site?.let(tools::outwardStart)
        var done = false
        try {
            val outcome = if (policy.actionCapMs == null) ran(step) else withContext(NonCancellable) { ran(step) }
            if (screenPaced && outcome is ActionOutcome.Ran) tools.afterWrite(call.name, outcome.exec.content)
            if (site != null && outcome is ActionOutcome.Ran && outcome.exec.content["ok"] != JsonPrimitive(false)) {
                done = true
                pacing.noteDone(site, recorder.now())
            }
            return outcome
        } finally {
            site?.let { tools.outwardEnd(it, done) }
        }
    }

    private class Step(
        val call: Part.ToolCall, val args: JsonObject, val origin: Origin, val before: List<GuardVerdict>, val writing: Boolean,
        val offScreen: Boolean, val verb: Verb?, val traced: String?, val followUp: FollowUp, val reads: GuardScope?,
        /** 没交给工具就有的结果(按描述找不到目标)。 */
        val preset: ToolExecution? = null,
        /** 附进结果的说明(按描述对上的是谁)。 */
        val note: JsonObject? = null,
        /** 执行前为对外动作的间隔、这块屏的节奏等了多久;算进这次调用的等待。 */
        val pacedMs: Long = 0,
        /** 执行前的停留里轻轻滚了几下;只进运行记录。 */
        val idleScrolls: Int = 0,
        /** 不可逆判定认出是对外动作(发送、提交);记进这次的活动。 */
        val outward: Boolean = false,
        val approvalDigest: String? = null,
        val requestedArgs: JsonObject = args,
    )

    private suspend fun ran(s: Step): ActionOutcome {
        val call = s.call
        if (s.preset == null && !tools.composite(call.name)) budget?.step()
        val policy = s.origin.policy
        val n = s.origin.step
        // 页面脚本里的每个动作经这里回来,同样走完整个入口;脚本里的读只在这一次调用里计数
        val subVerdicts = mutableListOf<GuardVerdict>()
        val sub = if (tools.composite(call.name)) {
            val script = Origin.Script(n)
            val window = if (script.policy.isolatedReads) GuardScope(canStop = false) else null
            SubActions { c ->
                val o = run(c, script, FollowUp.NONE, window)
                subVerdicts += o.before
                if (o is ActionOutcome.Ran) subVerdicts += o.after
                o.execution()
            }
        } else null
        val started = recorder.now()
        // 改状态的动作:记下执行前账本的位置,之后(含这次结果自带的那一屏)有没有重新读过由回答前的核对来看
        val changes = tools.declaredRisk(call.name) != Risk.Read && (s.offScreen || ClassifiedRisk.changesState(s.verb))
        val mark = tools.ledgerMark()
        val raw = (s.preset ?: offScreen?.exhausted(call.name)?.let { ToolExecution(it) } ?: if (changes && !tools.composite(call.name) && effects != null) {
            effects.execute(call, s.origin, s.approvalDigest, outward = s.outward) { execute(s, sub) }
        } else execute(s, sub))
            .let { r -> s.note?.let { r.with(content = JsonObject(r.content + it)) } ?: r }
            .let { r -> if (s.pacedMs > 0) r.with(waitMs = r.waitMs + s.pacedMs) else r }
            .let { r -> if (s.idleScrolls > 0) r.with(record = JsonObject(r.record + ("idleScrolls" to JsonPrimitive(s.idleScrolls)))) else r }
            .let { r -> if (changes) r.withActionDelivery() else r }
        if (tools.composite(call.name) && s.preset == null) effects?.checkSettled()
        // 报了失败、也没说界面变了的动作什么也没改成:回答前不用为它再读一遍
        val failed = raw.content["ok"] == JsonPrimitive(false) && raw.changed != true
        if (changes && s.preset == null && !failed) recorder.noteChange(mark)
        val durationMs = recorder.now() - started
        val resolved = timedActionPhase("followUp") { s.followUp.resolve(raw) } ?: return ActionOutcome.Cancelled(s.before)
        tools.interfaces.checkAppAccess(tools.declared(call.name), after = true)
        val after = subVerdicts.toMutableList()
        after += if (s.offScreen) offScreen?.result(call.name, s.args, resolved.content).orEmpty() else guards.result(call.name, resolved.content)
        // 动作报回的实际效果先记(没有效果、换了地方),接着的画面就不再按指纹重复记
        (resolved.content["effect"] as? JsonObject)?.takeIf { !s.offScreen }?.let { effect ->
            val kind = (effect["kind"] as? JsonPrimitive)?.content ?: return@let
            after += guards.effect(kind, (effect["treeBlind"] as? JsonPrimitive)?.booleanOrNull == true)
        }
        val elements = resolved.screenElements ?: resolved.content["elements"] as? JsonArray
        val frameHash = screenFingerprint(resolved.content, elements)
        val beforeFrameProgress = guards.progressVersion
        if (frameHash != null || s.verb == Verb.WaitFor || s.verb == Verb.Wait) {
            after += guards.frame("$n:${call.id}", frameHash)
        }
        val frameProgressed = guards.progressVersion != beforeFrameProgress
        if (frameProgressed) after.removeAll { it.code == "no_progress" || it.code == "repeating_action" }
        if (!s.offScreen) {
            if (s.verb in PAGE_READ_VERBS) {
                // 新内容不算重读;frame 已确认换屏时只登记指纹,不再重复 progress 清掉本次调用观察。
                val fp = readFingerprint(resolved.content, elements)
                val beforeReadProgress = guards.progressVersion
                val fresh = if (s.reads != null) guards.firstSeen(fp, s.reads) else guards.firstSeen(fp)
                after += if (s.reads != null) guards.read(call.name, fresh, s.reads, fp, noteProgress = !frameProgressed)
                else guards.read(call.name, fresh, fingerprint = fp, noteProgress = !frameProgressed)
                if (guards.progressVersion != beforeReadProgress) after.removeAll { it.code == "no_progress" || it.code == "repeating_action" }
            } else if (s.writing && resolved.changed != false && resolved.content["ok"] == JsonPrimitive(true)) {
                guards.pageChanged(resetCalls = resolved.changed == true)
                after.removeAll { it.code == "repeating_action" }
            }
        }
        val (exec, content) = s.followUp.present(resolved, s.before + after)
        onActionResult(s.verb, s.requestedArgs, exec, n)

        // 画面:模型单步交给调用方按它的顺序存;子步界面变了才当场存;回放不存
        val picture = if (policy.frames == Frames.Caller && s.verb != Verb.ViewPastScreens && !s.offScreen) exec.image ?: exec.frame else null
        val shotId = when (policy.frames) {
            Frames.Caller -> picture?.let { recorder.nextShot() }
            Frames.WhenChanged -> (exec.image ?: exec.frame)?.takeIf { exec.changed != false }?.let(recorder::saveFrame)
            Frames.None -> null
        }
        recorder.pointer(n, call.name, exec.record, exec.recordFrame, policy.source, call.id)
        recorder.result(n, call.name, s.verb, content, exec.record, exec.changed, shotId, durationMs, policy.source, call.id, exec.waitMs)
        ActivityRecord.of(tools, call.name, s.args, exec, n, policy.source, call.id, recorder.now(), s.outward)?.let(recorder::event)
        if (policy.frames == Frames.WhenChanged && shotId != null) recorder.event(RunEvent.Media(recorder.now(), shotId, n, RunEvent.placeLabel(exec.place)))
        val seen = if (policy.routeTargets) routeTarget(call, s.verb, s.origin, exec) else null
        s.traced?.let { recorder.event(RunEvent.State(recorder.now(), "gate_unresolved", "${call.name} $it")) }
        return ActionOutcome.Ran(exec, s.before, after, content, durationMs, shotId.takeIf { picture != null }, picture, seen)
    }

    /** 旧工具没有显式投递状态时,按可证明的事实补;派发后超时永远是 unknown,不是 not_sent。 */
    private fun ToolExecution.withActionDelivery(): ToolExecution {
        val wire = (content["delivery"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val reported = delivery ?: ActionDelivery.entries.firstOrNull { it.wire == wire }
        val resolved = reported ?: when {
            content["executed"] == JsonPrimitive(false) -> ActionDelivery.NotSent
            content["submissionUnconfirmed"] == JsonPrimitive(true) || content["error"] == JsonPrimitive("action_timeout") -> ActionDelivery.Unknown
            content["effect"] is JsonObject -> ActionDelivery.Sent
            changed != null -> ActionDelivery.Sent
            content["ok"] == JsonPrimitive(true) -> ActionDelivery.Sent
            content["ok"] == JsonPrimitive(false) -> ActionDelivery.Unknown
            else -> null
        }
        if (resolved == null) return this
        return with(content = JsonObject(content + ("delivery" to JsonPrimitive(resolved.wire))), delivery = resolved)
    }

    private fun screenChangedDuringLock() = ToolExecution(buildJsonObject {
        put("ok", false); put("error", "screen_changed_during_lock"); put("executed", false)
        put("delivery", "not_sent"); put("counts_as_failure", false); put("recoverable", true)
        put("message", "The phone locked and was unlocked while this action was being prepared. The action was not sent. Read the current screen and verify the previous action before choosing a new target.")
    })

    private suspend fun execute(s: Step, sub: SubActions?): ToolExecution {
        val call = if (s.offScreen && offScreen != null) offScreen.capTimeout(s.call, s.args) else s.call
        val cap = s.origin.policy.actionCapMs ?: return tools.execute(call, pass, s.origin.step, sub)
        // 动作自己有上限
        return withTimeoutOrNull(cap) { tools.execute(call, pass, s.origin.step, sub) } ?: ToolExecution(buildJsonObject {
            put("ok", false); put("error", "action_timeout"); put("message", "the action did not finish within ${cap / 1000} s; read the screen before going on")
        })
    }

    /**
     * 开着回放时记路线要的目标元素和前后锚点,并返回用于核对的动作后快照。
     * 取不到目标、动作却生效了:路线不能缺步,标 incomplete,整条路线不记。
     */
    private suspend fun routeTarget(call: Part.ToolCall, verb: Verb?, origin: Origin, exec: ToolExecution): ScreenSnapshot? {
        if (!tools.captureTargets || tools.index[call.name]?.routable != true) return null
        // 只有端口明确说「不是密码框」才记原文
        val secret = (exec.content["secret"] as? JsonPrimitive)?.booleanOrNull != false
        val args = Redactor.args(verb, call.argsJson, secret)
        val effective = effectiveWrite(verb, exec.content)
        val source = origin.policy.source
        val none = Anchors(null, emptyList())
        val ui = tools.interfaces
        val captured = ui.lastActionTarget()
        if (captured == null) {
            if (effective) recorder.event(RunEvent.ActionTarget(recorder.now(), origin.step, call.name, args, null, none, none, true,
                callId = call.id, source = source, incomplete = true))
            return null
        }
        val seen = if (effective) ui.lastActionSnapshot() else null
        val after = seen?.let(ui::anchorsAfter) ?: none
        recorder.event(RunEvent.ActionTarget(recorder.now(), origin.step, call.name, args, captured.first, captured.second, after, effective,
            callId = call.id, protected = ui.lastActionProtected(), source = source))
        return seen
    }

    /** 作用在 Maeve 自己身上:不做;模型单步照常拿到说明、记进运行记录。 */
    private fun ownApp(call: Part.ToolCall, args: JsonObject, origin: Origin, before: List<GuardVerdict>, followUp: FollowUp): ActionOutcome {
        val content = Refusals.content(Refusals.OWN_APP, origin.policy.refusals, before, null, null)
        if (origin.policy.recordsRefusal) {
            followUp.refused(content)
            recorder.result(origin.step, call.name, tools.verb(call.name), content, tools.portRecord(call.name, args), null, null, 0, origin.policy.source, call.id)
        }
        return ActionOutcome.Refused(Refusals.OWN_APP, content, before)
    }

    /** 作用在默认不碰名单(支付、银行、券商)里的 App 上:不做;要用户自己来,或用户在设置里放开这名。 */
    private fun deniedApp(call: Part.ToolCall, args: JsonObject, origin: Origin, before: List<GuardVerdict>, followUp: FollowUp): ActionOutcome {
        val content = Refusals.content(Refusals.APP_DENIED, origin.policy.refusals, before, null, null)
        if (origin.policy.recordsRefusal) {
            followUp.refused(content)
            recorder.result(origin.step, call.name, tools.verb(call.name), content, tools.portRecord(call.name, args), null, null, 0, origin.policy.source, call.id)
        }
        return ActionOutcome.Refused(Refusals.APP_DENIED, content, before)
    }

    /** 密码 / 验证码页上要打字或清空:交还用户,不代填。 */
    private fun secretPage(call: Part.ToolCall, args: JsonObject, origin: Origin, before: List<GuardVerdict>, followUp: FollowUp): ActionOutcome {
        val content = Refusals.content(Refusals.SECRET_PAGE, origin.policy.refusals, before, null, null)
        if (origin.policy.recordsRefusal) {
            followUp.refused(content)
            recorder.result(origin.step, call.name, tools.verb(call.name), content, tools.portRecord(call.name, args), null, null, 0, origin.policy.source, call.id)
        }
        return ActionOutcome.Refused(Refusals.SECRET_PAGE, content, before)
    }

    private fun refuse(code: String, origin: Origin, before: List<GuardVerdict>) =
        ActionOutcome.Refused(code, Refusals.content(code, origin.policy.refusals, before, null, null), before)

    private fun refusedCall(call: Part.ToolCall, args: JsonObject, origin: Origin, before: List<GuardVerdict>, followUp: FollowUp, code: String): ActionOutcome {
        val content = Refusals.content(code, origin.policy.refusals, before, null, null)
        if (origin.policy.recordsRefusal) {
            followUp.refused(content)
            recorder.result(origin.step, call.name, tools.verb(call.name), content, tools.portRecord(call.name, args), null, null, 0, origin.policy.source, call.id)
        }
        return ActionOutcome.Refused(code, content, before)
    }

    private fun approvalUnavailable(
        call: Part.ToolCall, args: JsonObject, origin: Origin, before: List<GuardVerdict>, hit: Irreversible, followUp: FollowUp,
    ): ActionOutcome {
        val code = "approval_unavailable"
        val content = Refusals.content(code, origin.policy.refusals, before, hit, null)
        if (origin.policy.recordsRefusal) {
            followUp.refused(content)
            recorder.result(origin.step, call.name, tools.verb(call.name), content, tools.portRecord(call.name, args), null, null, 0, origin.policy.source, call.id)
        }
        return ActionOutcome.Refused(code, content, before, hit)
    }

    /** 要问的一步没有同意(用户没同意,或问不到人):[answer] 就是结果,照被拦下一样记。 */
    private fun declined(
        call: Part.ToolCall, args: JsonObject, origin: Origin, before: List<GuardVerdict>, hit: Irreversible, answer: ToolExecution, followUp: FollowUp,
        code: String,
    ): ActionOutcome {
        recorder.confirmation(hit, if (code == "confirmation_declined") "declined" else "denied")
        if (origin.policy.recordsRefusal) {
            followUp.refused(answer.content)
            recorder.result(origin.step, call.name, tools.verb(call.name), answer.content, tools.portRecord(call.name, args), null, null, 0, origin.policy.source, call.id)
        }
        return ActionOutcome.Refused(code, answer.content, before, hit)
    }

    private fun blocked(
        call: Part.ToolCall, args: JsonObject, origin: Origin, before: List<GuardVerdict>, hit: Irreversible, reason: String?, followUp: FollowUp,
        everyWrite: Boolean = false,
    ): ActionOutcome {
        recorder.confirmation(hit, if (reason != null) "mismatch" else "required")
        val code = if (reason != null) "confirmation_mismatch" else "confirmation_required"
        val content = Refusals.content(code, origin.policy.refusals, before, hit, reason, everyWrite)
        if (origin.policy.recordsRefusal) {
            followUp.refused(content)
            recorder.result(origin.step, call.name, tools.verb(call.name), content, tools.portRecord(call.name, args), null, null, 0, origin.policy.source, call.id)
        }
        return ActionOutcome.Refused(code, content, before, hit)
    }
}
