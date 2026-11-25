package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.gate.ConfirmationRequest
import com.daiyimo.maeve.core.risk.AppTier
import com.daiyimo.maeve.core.risk.AppTiers
import com.daiyimo.maeve.core.risk.IrrevAction
import com.daiyimo.maeve.core.gate.Irreversible
import com.daiyimo.maeve.core.gate.IrreversibleDetector
import com.daiyimo.maeve.core.gate.Permit
import com.daiyimo.maeve.core.gate.SelfAppGuard
import com.daiyimo.maeve.core.guard.exactCallSignature
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import com.daiyimo.maeve.core.uistate.VisibleTextLine
import com.daiyimo.maeve.core.uistate.interfaces
import com.daiyimo.maeve.core.tool.ToolDispatcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.contentOrNull
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.run.RunProvenance
import com.daiyimo.maeve.core.run.RunOrigin

/** 一次动作执行前的审批结论。 */
sealed interface Decision {
    /** 不用判,或判过放行(含付款时用户的同意对上了)。 */
    data object Clear : Decision
    /** 放行,但找不到点在哪个元素上:运行记录里留痕。 */
    data class Traced(val detail: String) : Decision
    /** 找不到点在哪个元素上,这一来源不盲点。 */
    data object Unresolved : Decision
    /**
     * 这一步要用户这一次的同意(花钱,或 [ask] 不为空时是工具声明要问的不在屏上的一步),没有可用的。
     * [everyWrite] 为 true 时是每步确认名单里的 App(如微信)里的写操作:每一步都要问。
     * 有 [ask] 时调用方可问人就当场问(见 [FollowUp.approve])。
     */
    data class Required(val hit: Irreversible, val ask: ConfirmationRequest? = null, val everyWrite: Boolean = false) : Decision
    /** 有同意,但和这一步对不上;那次同意已作废。 */
    data class Mismatch(val hit: Irreversible, val reason: String) : Decision
    /** 要花钱,本次运行却问不到手机旁的用户,直接拒绝。 */
    data class Denied(val hit: Irreversible) : Decision
    /** 作用在 Maeve 自己身上,不做。 */
    data object OwnApp : Decision
    /** 作用在默认不碰名单(支付、银行、券商)里的 App 上,不做;自主模式也不例外。 */
    data object DeniedApp : Decision
    /** 密码 / 验证码页上要打字或清空:交还用户,不代填;自主模式也不例外。 */
    data object SecretPage : Decision
}

/**
 * 不可逆判定、审批与同意核对。整个代码库里只有这里调用 [IrreversibleDetector] 的判定和 [ConfirmationGate.permits]
 * (有测试守着),各来源只在 [OriginPolicy] 里写明哪里不同;问不问由 [ApprovalPolicy.decide] 定(付款、工具声明要问的、
 * 每步确认名单里的 App 的问)。
 */
class ConfirmDecision(
    private val tools: ToolDispatcher,
    private val confirmations: ConfirmationGate?,
    private val approvals: ApprovalSettings = ApprovalSettings.ASK,
    private val risks: RiskJudge = ClassifiedRisk,
    private val recorder: ActionRecorder? = null,
    private val provenance: RunProvenance = RunProvenance(),
    /** 开工就是自主执行(没有确认关卡):付款也不问,界面目标猜测也不阻断动作;硬规则(不对 Maeve 自己动手、默认不碰名单、密码 / 验证码页)照旧。中途换成自主看 [ApprovalSettings.autonomous]。 */
    private val autonomous: Boolean = false,
    /** Maeve 自己的包名:作用在它上面的写操作一律不做(不自己给自己点同意、改设置)。 */
    private val selfPackage: String? = null,
) {
    /**
     * 刚判过的这一步是对外动作(发送、提交:判定命中、放行后要做的)时它所在的网站或 App(见 [OutwardPacing.siteOf]),
     * 执行入口据此和上一次对外动作隔开;不是为 null。自主执行时也认(按模型手里那一屏判,不另读屏),节奏不因此失效。
     */
    var outwardSite: String? = null
        private set
    var approvalDigest: String? = null
        private set

    /** 判定放在锁屏等待之后:按此刻重新读的一屏判。 */
    suspend fun judge(tool: String, args: JsonObject, origin: Origin): Decision {
        outwardSite = null
        approvalDigest = null
        // 这一步落在哪里由界面一侧给出;点看图找到的 @vN 时参数已换成它登记的坐标和找的文字(只读,不另读屏)
        val aim = tools.interfaces.actionTarget(tools.declared(tool), args)
        val checked = aim.args
        val writing = tools.writesScreen(tool)
        val verb = tools.verb(tool)
        // 打字、清空:密码 / 验证码页上交还用户(页上有秘密输入框时就不代填)
        val entering = writing && (verb == Verb.TypeText || verb == Verb.ClearText)
        // 硬规则先于一切放行(含下面自主模式的提前返回):不对 Maeve 自己动手、默认不碰名单里的 App 不写、
        // 密码 / 验证码页不代填;都按模型手里那一屏判,不另读屏
        if (SelfAppGuard.targetsSelf(selfPackage, writing, args, aim.aimed)) return Decision.OwnApp
        if (writing && AppTiers.tierOf(aim.aimed?.packageName) == AppTier.Deny) return Decision.DeniedApp
        if (entering && aim.aimed?.elements?.any { it.secret } == true) return Decision.SecretPage
        // 每步确认名单里的 App(微信):询问时每一步写操作都要用户同意;软规则,自主模式豁免
        val approvalScreen = if (writing && provenance.origin == RunOrigin.Amoo) aim.now() else null
        if (SelfAppGuard.targetsSelf(selfPackage, writing, args, approvalScreen)) return Decision.OwnApp
        if (AppTiers.tierOf(approvalScreen?.packageName) == AppTier.Deny) return Decision.DeniedApp
        if (entering && approvalScreen?.elements?.any { it.secret } == true) return Decision.SecretPage
        val everyWrite = writing && !approvals.autonomous() &&
            AppTiers.tierOf((approvalScreen ?: aim.aimed)?.packageName) == AppTier.ConfirmEveryWrite
        val policy = origin.policy
        val target = tools.gateTarget(tool)
        tools.approvalRisk(tool, checked)?.let { declared ->
            return approve(tool, checked, origin, null, declared, null, null, null, everyWrite) ?: Decision.Clear
        }
        val typing = if (verb == Verb.TypeText) (args["text"] as? JsonPrimitive)?.contentOrNull else null
        val needsCheck = IrreversibleDetector.needsCheck(target, args)
        val amooAutonomous = provenance.origin == RunOrigin.Amoo && approvals.autonomous()
        val app = approvalScreen?.packageName
        val riskScreen = if (amooAutonomous && approvalScreen != null && app != null &&
            AppTiers.tierOf(app) == AppTier.ConfirmEveryWrite && (needsCheck || entering)) {
            val evidence = if (approvalScreen.elements.isNotEmpty()) approvalScreen else {
                val frame = tools.interfaces.approvalFrame(tools.declared(tool), app, withImage = false) ?: return Decision.Unresolved
                approvalScreen.withVisualText(frame.text.orEmpty())
            }
            IrreversibleDetector.paymentPage(evidence)?.let { payment ->
                val hit = Irreversible(IrrevAction.pay, payment)
                return approve(tool, checked, origin, hit, Risk(ActionRisk.Irreversible, payment = true), evidence, typing, null)
                    ?: Decision.Denied(hit)
            }
            evidence
        } else approvalScreen
        // 每步确认名单里的 App(微信):每一步写操作都要用户同意,和看起来可不可逆无关;
        // 问不到人时按 Approval.Deny 停下(见 [ApprovalPolicy]),不擅自放行
        if (everyWrite && !needsCheck) {
            return approve(tool, checked, origin, null, Risk(ActionRisk.Change), approvalScreen ?: aim.aimed, typing, null, everyWrite) ?: Decision.Clear
        }
        // 没有可判的不可逆字样的动作(一般改动、界面间移动)不花钱,不用问
        if (!needsCheck) return Decision.Clear
        val snap = aim.aimed
        val fresh = riskScreen ?: aim.now()
        // 复看此刻的一屏:硬规则按它再判一次(动作之前换到了受保护的 App 或页面)
        if (SelfAppGuard.targetsSelf(selfPackage, writing, args, fresh)) return Decision.OwnApp
        if (writing && AppTiers.tierOf(fresh?.packageName) == AppTier.Deny) return Decision.DeniedApp
        if (entering && fresh?.elements?.any { it.secret } == true) return Decision.SecretPage
        // 复看确认过的所见文字(@vN 才有):读屏里没有文字时(自绘界面)付款的同意按它核对象
        val seen = aim.seen
        val hit = IrreversibleDetector.classify(target, checked, snap, fresh)
        val risk = risks.assess(verb, hit) { fresh ?: snap }
        outwardSite = outward(hit, fresh ?: snap)
        approve(tool, checked, origin, hit, risk, fresh ?: snap, typing, seen, everyWrite)?.let { return it }
        // 已确认的坐标只在用户确认过的同一页面上放行。
        if (provenance.origin == RunOrigin.Amoo && approvalDigest != null) return Decision.Clear
        if (hit != null || !IrreversibleDetector.unresolved(target, checked, snap, fresh)) return Decision.Clear
        // 有名字、经过定位与复看的视觉目标可继续；裸坐标/失效编号不能靠记日志后盲点。
        if (aim.bySight && !seen.isNullOrBlank() && fresh != null && fresh.packageName == snap?.packageName) return Decision.Clear
        // 自主执行:不可逆动作已在上面被拦下,走到这里说明这一下是安全的;自绘界面(canvas)上没有元素,
        // 裸坐标点击不该再被「目标确认不了」卡住,否则模型反复盲点、最后放弃。
        if (if (provenance.origin == RunOrigin.Amoo) amooAutonomous else autonomous) return Decision.Clear
        return Decision.Unresolved
    }

    /** 命中的是发送、提交时这一屏的网站或 App。 */
    private fun outward(hit: Irreversible?, screen: ScreenSnapshot?): String? =
        if (hit?.action == IrrevAction.send || hit?.action == IrrevAction.submit) OutwardPacing.siteOf(screen?.packageName, screen?.web != null) else null

    /** 按来源判;要花钱时核对用户的一次性同意。放行为 null。 */
    private suspend fun approve(
        tool: String, checked: JsonObject, origin: Origin, hit: Irreversible?, risk: Risk, screen: ScreenSnapshot?,
        typing: String?, seen: String?, everyWrite: Boolean = false,
    ): Decision? {
        val policy = origin.policy
        // 没命中不可逆判定的(工具自己声明的风险):有申报按申报记,否则按动作落在的元素记成「其他」
        val asked = hit ?: risk.ask?.let { Irreversible(it.action, it.content) } ?: Irreversible(IrrevAction.other,
            screen?.let { ClassifiedRisk.target(tools.verb(tool), checked, it) }?.name?.trim()?.takeIf { it.isNotEmpty() } ?: tool)
        // 对外、要花钱和没放行的都记下;界面上的改动不记
        fun audit(decision: String) {
            if (decision != "allowed" || risk.level.outward || risk.payment) recorder?.approval(origin, tool, asked, risk, decision)
        }
        when (ApprovalPolicy.decide(risk, provenance, everyWrite)) {
            Approval.Allow -> return null.also { audit("allowed") }
            Approval.Deny -> return Decision.Denied(asked).also { audit("denied") }
            Approval.Ask -> Unit
        }
        val ask = if (provenance.origin == RunOrigin.Amoo) {
            if (!policy.usesGrant) return Decision.Denied(asked).also { audit("denied") }
            screenRequest(tool, checked, screen, typing, seen) ?: return Decision.Unresolved
        } else risk.ask
        val required = Decision.Required(asked, ask, everyWrite)
        val gate = confirmations?.takeIf { policy.usesGrant } ?: return required.also { audit("required") }
        val granted = gate.granted
        val permit = ask?.let { gate.permitsExactly(it, origin.step) } ?: gate.permits(asked, screen, origin.step, typing, listOfNotNull(seen))
        return when (val p = permit) {
            Permit.Allowed -> null.also {
                approvalDigest = granted?.let { ActionEffectStore.digest(kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                    listOf(it.action.name, it.target, it.content, it.amount.orEmpty(), it.app))) }
                audit("allowed")
            }
            Permit.NoGrant -> required.also { audit("required") }
            is Permit.Mismatch -> Decision.Mismatch(asked, p.reason).also { audit("mismatch") }
        }
    }

    private suspend fun screenRequest(tool: String, args: JsonObject, screen: ScreenSnapshot?, typing: String?, seen: String?): ConfirmationRequest? {
        val app = screen?.packageName ?: return null
        if (AppTiers.tierOf(app) != AppTier.ConfirmEveryWrite) return null
        val binding = screenBinding(tool, args, screen) ?: return null
        val verb = tools.verb(tool)
        val element = ClassifiedRisk.target(verb, args, screen)
        val point = listOf("x", "y").map { (args[it] as? JsonPrimitive)?.contentOrNull }
        val target = element?.name?.takeIf(String::isNotBlank) ?: seen?.takeIf(String::isNotBlank)
            ?: if (point.all { it != null }) {
                val position = "屏幕坐标 (${point.joinToString(", ")})"
                val hint = (args["target"] as? JsonPrimitive)?.contentOrNull?.lineSequence()?.firstOrNull()?.take(80)
                if (hint.isNullOrBlank()) position else "$hint · $position"
            } else ""
        val content = when (verb) {
            Verb.Tap -> "点击"
            Verb.TypeText -> "输入文字：${typing.orEmpty()}"
            Verb.ClearText -> "清空输入框"
            Verb.Back -> "返回上一页"
            else -> "界面操作：$tool"
        }
        return ConfirmationRequest(IrrevAction.other, target.take(200), content, null, app, binding)
    }

    /** 显示确认后，发给工具前再次检查批准的调用和页面依然一致。 */
    suspend fun matchesApprovedScreen(tool: String, args: JsonObject, request: ConfirmationRequest): Boolean {
        val aim = tools.interfaces.actionTarget(tools.declared(tool), args)
        val screen = aim.now() ?: return false
        return screenBinding(tool, aim.args, screen) == request.screenBinding
    }

    private suspend fun screenBinding(tool: String, args: JsonObject, screen: ScreenSnapshot): String? {
        if (screen.protected || !screen.hasWindow || screen.changedDuringRead) return null
        val app = screen.packageName ?: return null
        if (AppTiers.tierOf(app) != AppTier.ConfirmEveryWrite) return null
        val page = if (screen.elements.isNotEmpty()) {
            if (IrreversibleDetector.paymentPage(screen) != null) return null
            val elements = screen.elements.map {
                listOf(it.role, it.name, it.bounds.joinToString(","), it.resourceId.orEmpty(),
                    it.focused.toString(), it.states.sorted().joinToString(","))
            }
            "tree:${screen.width}x${screen.height}:${Json.encodeToString(elements)}"
        } else {
            val frame = tools.interfaces.approvalFrame(tools.declared(tool), app, withImage = true) ?: return null
            val check = screen.withVisualText(frame.text.orEmpty())
            if (IrreversibleDetector.paymentPage(check) != null ||
                IrreversibleDetector.classify(tools.gateTarget(tool), args, check) != null) return null
            "frame:${frame.width}x${frame.height}:${ActionEffectStore.digest(frame.png ?: return null)}"
        }
        return ActionEffectStore.digest("${exactCallSignature(tool, args)}\n$app\n$page")
    }

    private fun ScreenSnapshot.withVisualText(lines: List<VisibleTextLine>): ScreenSnapshot = copy(
        elements = lines.mapIndexedNotNull { i, line ->
            val b = line.bounds.takeIf { it.size == 4 } ?: return@mapIndexedNotNull null
            UiElement("ocr$i", "Text", line.text, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, b,
                null, clickable = false, editable = false, scrollable = false)
        },
    )

    /** 执行了一个写操作:同意按它的规则作废或保留。 */
    fun noteWrite(tool: String) {
        confirmations?.noteWrite(tools.verb(tool))
    }

    /** 留痕:点的是哪个编号、那次读屏多久以前,或点的坐标。 */
    private fun unresolvedDetail(args: JsonObject, readAgeMs: Long?, freshRead: Boolean): String {
        fun arg(k: String) = args[k]?.let { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
        val ref = arg("ref")
        return if (ref != null) {
            val sid = arg("snapshotId")
            "ref=$ref snapshotId=$sid age=${readAgeMs?.let { "${it}ms" } ?: "unknown"}"
        } else "x=${arg("x")},y=${arg("y")} freshRead=$freshRead"
    }

    companion object {
        /**
         * 不读屏的预判(大小模型分工用):按上一次读屏,这一步可能是不可逆的,或不知道点的是什么(看图得来、找不到元素);
         * 这样的一步交主模型做。
         */
        fun mayBeIrreversible(tools: ToolDispatcher, tool: String, args: JsonObject): Boolean {
            val target = tools.gateTarget(tool)
            if (!IrreversibleDetector.needsCheck(target, args)) return false
            val aim = tools.interfaces.actionTarget(tools.declared(tool), args)
            val checked = aim.args
            val snap = aim.aimed
            return aim.bySight || IrreversibleDetector.classify(target, checked, snap) != null ||
                IrreversibleDetector.unresolved(target, checked, snap)
        }
    }
}
