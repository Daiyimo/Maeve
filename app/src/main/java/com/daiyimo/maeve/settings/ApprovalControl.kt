package com.daiyimo.maeve.settings

import com.daiyimo.maeve.core.action.ApprovalSettings
import com.daiyimo.maeve.core.gate.ConfirmationGate

/** 每次运行持有自己的可撤销审批状态；自主模式只放宽普通可恢复操作。 */
class RunApproval(startAutonomous: Boolean) : ApprovalSettings {
    /** 开工时的显示状态；运行中的策略始终现读 autonomous()。 */
    val startedAutonomous: Boolean = startAutonomous
    @Volatile private var auto = startAutonomous
    @Volatile private var version = 0L
    private val gates = mutableListOf<ConfirmationGate>()

    override fun autonomous(): Boolean = auto
    override fun revision(): Long = version

    @Synchronized fun set(autonomous: Boolean) {
        if (auto == autonomous) return
        auto = autonomous
        version++
        gates.forEach { it.reset() }
    }

    /** 每个父/子循环都有独立单次授权；切换模式时统一撤销未消费授权。 */
    @Synchronized fun gate(): ConfirmationGate = ConfirmationGate().also { gates += it }
}

/**
 * 询问 / 自主是一个全局开关,存在 [ApprovalStore.autonomous],默认询问。每个任务开工时拿一份自己的([start]);
 * 换开关([setAutonomous]):存下来管之后的任务,正在跑的任务也跟着换(下一动作边界使用新设置，对外动作仍需单次同意)。
 */
class ApprovalControl(private val store: ApprovalStore) {
    private val runs = mutableSetOf<RunApproval>()

    /**
     * [remote] 为 true 是电脑经 MCP 交来的任务:不管开关,一律按询问开工、也不跟着开关变,
     * 付款这一步在这类任务里永远不做(审批判定对非手机发起的一律拒绝),远端没有办法让它放行。
     */
    @Synchronized fun start(remote: Boolean = false): RunApproval =
        if (remote) RunApproval(false) else RunApproval(store.autonomous.value).also { runs += it }

    @Synchronized fun end(run: RunApproval) {
        runs -= run
    }

    @Synchronized fun setAutonomous(on: Boolean) {
        store.setAutonomous(on)
        runs.forEach { it.set(on) }
    }
}
