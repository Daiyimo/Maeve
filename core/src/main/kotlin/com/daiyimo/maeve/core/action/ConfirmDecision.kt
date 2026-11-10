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
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.interfaces
import com.daiyimo.maeve.core.tool.ToolDispatcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.run.RunProvenance

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

    /** 判定放在锁屏等待之后:按此刻重新读的一屏判。 */
    suspend fun judge(tool: String, args: JsonObject, origin: Origin): Decision {
        outwardSite = null
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
        val everyWrite = writing && AppTiers.tierOf(aim.aimed?.packageName) == AppTier.ConfirmEveryWrite
        if (autonomous || approvals.autonomous()) {
            val target = tools.gateTarget(tool)
            if (IrreversibleDetector.needsCheck(target, args)) {
                outwardSite = outward(IrreversibleDetector.classify(target, checked, aim.aimed), aim.aimed)
            }
            return Decision.Clear
        }
        val policy = origin.policy
        val target = tools.gateTarget(tool)
        tools.approvalRisk(tool, checked)?.let { declared ->
            return approve(tool, checked, origin, null, declared, null, null, null, everyWrite) ?: Decision.Clear
        }
        val typing = if (verb == Verb.TypeText) (args["text"] as? JsonPrimitive)?.contentOrNull else null
        // 每步确认名单里的 App(微信):每一步写操作都要用户同意,和看起来可不可逆无关;
        // 问不到人时按 Approval.Deny 停下(见 [ApprovalPolicy]),不擅自放行
        if (everyWrite) {
            return approve(tool, checked, origin, null, Risk(ActionRisk.Change), aim.aimed, typing, null, everyWrite) ?: Decision.Clear
        }
        if (confirmations == null && !policy.judgeWithoutGate) return Decision.Clear
        // 没有可判的不可逆字样的动作(一般改动、界面间移动)不花钱,不用问
        if (!IrreversibleDetector.needsCheck(target, args)) return Decision.Clear
        val snap = aim.aimed
        val fresh = aim.now()
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
        if (hit != null || !IrreversibleDetector.unresolved(target, checked, snap, fresh)) return Decision.Clear
        return if (policy.refuseUnresolved) Decision.Unresolved else Decision.Traced(unresolvedDetail(args, aim.readAgeMs, fresh != null))
    }

    /** 命中的是发送、提交时这一屏的网站或 App。 */
    private fun outward(hit: Irreversible?, screen: ScreenSnapshot?): String? =
        if (hit?.action == IrrevAction.send || hit?.action == IrrevAction.submit) OutwardPacing.siteOf(screen?.packageName, screen?.web != null) else null

    /** 按来源判;要花钱时核对用户的一次性同意。放行为 null。 */
    private fun approve(
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
        val required = Decision.Required(asked, risk.ask, everyWrite)
        val gate = confirmations?.takeIf { policy.usesGrant } ?: return required.also { audit("required") }
        val permit = risk.ask?.let { gate.permitsExactly(it, origin.step) } ?: gate.permits(asked, screen, origin.step, typing, listOfNotNull(seen))
        return when (val p = permit) {
            Permit.Allowed -> null.also { audit("allowed") }
            Permit.NoGrant -> required.also { audit("required") }
            is Permit.Mismatch -> Decision.Mismatch(asked, p.reason).also { audit("mismatch") }
        }
    }

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
