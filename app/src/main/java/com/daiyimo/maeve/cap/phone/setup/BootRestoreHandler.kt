package com.daiyimo.maeve.cap.phone.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daiyimo.maeve.cap.phone.PhoneServices
import kotlinx.coroutines.launch

/**
 * 开机广播。只有用户打开了「重启后自动恢复」、并且 Maeve 还拿着修改系统设置的授权时才动手,
 * 其余情况什么都不做(判断在 [BuiltinShellRecovery.restoreAfterBoot])。
 *
 * 广播只有 10 秒预算,开机时系统正忙,所以后台动作都放协程里跑完再 finish。
 * 注意:goAsync 取到的 PendingResult 整个 receiver 只有一份,取一次就没了,
 * 子类不能再自己 goAsync(会拿到 null),额外动作挂 [restoreInBackground] 钩子上,
 * 由这里一起跑完、一起 finish。
 */
open class BootRestoreHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!accepts(intent)) return
        val pending = goAsync()
        val phone = PhoneServices.of(context)
        phone.pairing.scope.launch {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    runCatching { phone.recovery.restoreAfterBoot() }
                }
                restoreInBackground(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    /** 默认只接开机广播;子类把自己要接的(闹钟重排、时区变化那几种)也放进来。 */
    protected open fun accepts(intent: Intent): Boolean = intent.action == Intent.ACTION_BOOT_COMPLETED

    /** 手机能力恢复之外的后台动作,和恢复在同一个协程里串行跑完。默认什么都不做。 */
    protected open suspend fun restoreInBackground(context: Context, intent: Intent) {}
}
