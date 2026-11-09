package com.daiyimo.maeve.core.shell

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 供测试与尚未接入真实实现的界面使用的假控制器：只把每次调用按顺序记进 [calls]，
 * 状态则完全由构造参数和两个结果字段摆布——[pairingResult] 决定 [startPairing] 最终停在哪，
 * [retryResult] 决定 [retry] 最终停在哪。
 */
class FakeBuiltinShellControl(
    initial: BuiltinShellStatus = BuiltinShellStatus.NotPaired,
    var pairingResult: BuiltinShellStatus = BuiltinShellStatus.Running,
    var retryResult: BuiltinShellStatus = BuiltinShellStatus.Running,
) : BuiltinShellControl {
    override val status = MutableStateFlow<BuiltinShellStatus>(initial)
    override val pairingDiscovery = MutableStateFlow<PairingDiscovery>(PairingDiscovery.Idle)

    /** 按发生顺序记下的调用，供断言。 */
    val calls = mutableListOf<String>()

    /** 记下一次调用：这是本假实现唯一真正做的事。 */
    private fun record(action: String) {
        calls += action
    }

    /** 先落到「正在连接」再落到 [final]：和真实实现一样让界面看到中间那一闪。 */
    private fun connectThen(final: BuiltinShellStatus) {
        status.value = BuiltinShellStatus.Connecting
        status.value = final
    }

    override fun startDiscovery() {
        record("discover")
        if (pairingDiscovery.value == PairingDiscovery.Idle) {
            pairingDiscovery.value = PairingDiscovery.Searching
        }
    }

    override fun stopDiscovery() {
        record("stopDiscovery")
        pairingDiscovery.value = PairingDiscovery.Idle
    }

    override suspend fun startPairing(code: String, port: Int?) {
        record("pair $code" + (port?.let { " $it" } ?: ""))
        connectThen(pairingResult)
    }

    override suspend fun retry() {
        record("retry")
        connectThen(retryResult)
    }

    override suspend fun unpair() {
        record("unpair")
        status.value = BuiltinShellStatus.NotPaired
    }
}
