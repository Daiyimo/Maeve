package com.daiyimo.maeve.quit

/** 完全退出的两个持久状态操作;安卓实现用 [com.daiyimo.maeve.settings.FeatureFlags]。 */
internal interface QuitState {
    fun quitRequested(): Boolean
    fun setQuitRequested(on: Boolean)
}

/** 完全退出要做的安卓侧动作;拆成组以便单测核对顺序,组内仍由安卓实现分别兜底。 */
internal interface QuitEffects {
    fun stopCapsule()
    fun stopBackgroundWork()
    fun stopServices()
    fun finishActivity()
    fun clearNotifications()
    fun resumeCapsule()
    fun resumeServices()
}

/** 延迟结束进程;重新打开 Maeve 时先撤掉还没执行的那一次。 */
internal interface QuitExit {
    fun cancel()
    fun schedule()
}

/**
 * 完全退出的纯编排:先记退出标记再停后台,最后才安排杀进程;重新打开时先撤销退出,再恢复胶囊和后台能力。
 * 安卓对象 [MaeveQuit] 只负责提供实现,不在这里直接碰 Context、Service 或进程。
 */
internal class QuitCoordinator(
    private val state: QuitState,
    private val effects: QuitEffects,
    private val exit: QuitExit,
) {
    fun quit() {
        state.setQuitRequested(true)
        effects.stopCapsule()
        effects.stopBackgroundWork()
        effects.stopServices()
        effects.finishActivity()
        effects.clearNotifications()
        exit.schedule()
    }

    /** 返回这次是否真的从「完全退出」状态恢复;没有退出标记时只撤销待执行的退出。 */
    fun resume(): Boolean {
        exit.cancel()
        if (!state.quitRequested()) return false
        state.setQuitRequested(false)
        effects.resumeCapsule()
        effects.resumeServices()
        return true
    }
}
