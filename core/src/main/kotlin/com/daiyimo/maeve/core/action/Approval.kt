package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.gate.ConfirmationRequest
import com.daiyimo.maeve.core.run.Attendance
import com.daiyimo.maeve.core.run.RunOrigin
import com.daiyimo.maeve.core.run.RunProvenance

/**
 * 一个动作的风险等级(记进审批记录,核查据此知道任务做过对外的事)。[Read] 只看或只在界面间移动;[Change] 在界面上改东西(点、打字);
 * [External] 把东西交给别人(发送、提交);[Irreversible] 做了收不回(付款、下单、删除);[Unknown] 看不清点的是什么。
 */
enum class ActionRisk { Read, Change, External, Irreversible, Unknown;

    /** 会影响屏幕之外的世界(或拿不准会不会)。 */
    val outward: Boolean get() = this == External || this == Irreversible || this == Unknown
}

/**
 * [payment] 为 true 时这一步要花钱(付款、下单,或屏上有应付金额时的对外动作):询问时先问用户。
 * [ask] 不为空时是不在屏上、改了收不回的一步(工具按这次的参数声明,如会改动东西的 root 命令),询问时也先问用户,
 * 给用户看的就是它;同意只放行一字不差的这一步。
 */
data class Risk(val level: ActionRisk, val payment: Boolean = false, val ask: ConfirmationRequest? = null)

/** 用户在确认卡片上的选择:同意这一次,或不同意。 */
enum class ConfirmChoice { Once, Deny;

    val approved: Boolean get() = this != Deny
}

/** 询问 / 自主:此刻是不是自主模式(中途换成自主时,正在跑的任务从下一步起照它)。由设置提供,测试里换成固定值。 */
fun interface ApprovalSettings {
    /** 自主只影响可恢复的普通写入，对外与不可逆动作仍需单次授权。 */
    fun autonomous(): Boolean
    fun revision(): Long = 0

    companion object {
        /** 询问(默认):付款和工具声明要问的那几步(见 [Risk.ask])先问。 */
        val ASK = ApprovalSettings { false }
    }
}

sealed interface Approval {
    /** 不用问。 */
    data object Allow : Approval
    /** 要用户这一次的同意(有一次性同意就核对并用掉它)。 */
    data object Ask : Approval
    /** 本次运行不能向用户要同意，动作必须停下。 */
    data object Deny : Approval
}

/**
 * 审批判定:纯函数。付款和工具声明要问的(见 [Risk.ask])要问;[everyWrite] 为 true 时(每步确认名单里的 App,
 * 如微信)每一步写操作都要问。Amoo 文件桥的普通界面写入可由手机主人逐次确认。
 */
object ApprovalPolicy {
    fun decide(risk: Risk, run: RunProvenance = RunProvenance(), everyWrite: Boolean = false): Approval = when {
        !risk.payment && risk.ask == null && !risk.level.outward && !everyWrite -> Approval.Allow
        run.attendance == Attendance.Unattended -> Approval.Deny
        run.origin == RunOrigin.Ui -> Approval.Ask
        run.origin == RunOrigin.Amoo && everyWrite && !risk.payment && risk.ask == null && !risk.level.outward -> Approval.Ask
        else -> Approval.Deny
    }
}
