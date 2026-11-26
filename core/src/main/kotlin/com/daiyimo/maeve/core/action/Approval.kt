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
 * [payment] 为 true 时这一步要花钱(付款、下单,或屏上有应付金额时的对外动作):必须交由用户处理。
 * [ask] 是工具声明的系统级授权(如 shell/root 写命令),仍需单次确认。
 */
data class Risk(val level: ActionRisk, val payment: Boolean = false, val ask: ConfirmationRequest? = null)

/** 用户在确认卡片上的选择:同意这一次,或不同意。 */
enum class ConfirmChoice { Once, Deny;

    val approved: Boolean get() = this != Deny
}

/** 询问 / 自主:此刻是不是自主模式(中途换成自主时,正在跑的任务从下一步起照它)。由设置提供,测试里换成固定值。 */
fun interface ApprovalSettings {
    /** 兼容现有设置；非支付动作不再因自主模式改变审批策略。 */
    fun autonomous(): Boolean
    fun revision(): Long = 0

    companion object {
        /** 询问(默认):只有付款需要用户接手或确认。 */
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
 * 审批判定:纯函数。付款与工具声明的系统级授权需要用户介入;发送、提交、删除、下单前提交与普通界面写入直接执行。
 * 密码 / 验证码、系统授权和默认不碰名单由 [ConfirmDecision] 的硬规则单独保护。
 */
object ApprovalPolicy {
    fun decide(risk: Risk, run: RunProvenance = RunProvenance(), everyWrite: Boolean = false): Approval = when {
        !risk.payment && risk.ask == null -> Approval.Allow
        run.attendance == Attendance.Unattended -> Approval.Deny
        run.origin == RunOrigin.Ui -> Approval.Ask
        else -> Approval.Deny
    }
}
