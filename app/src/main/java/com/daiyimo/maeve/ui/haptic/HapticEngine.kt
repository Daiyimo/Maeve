package com.daiyimo.maeve.ui.haptic

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maeve 的交互触感语义。刻意不是 Flutter 的 selectionClick / lightImpact / mediumImpact /
 * heavyImpact —— 那些 API 在原生 Compose 里并不存在,Compose 自带的触感反馈只有
 * LongPress 和 TextHandleMove,表达不了「确认 / 停止 / 被护栏拒绝」。
 *
 * 数值与语义来自 docs/spec/haptic-matrix.md;实现为 Kotlin + VibrationEffect 重写,
 * 未复制 dsh-Go (AGPL-3.0) 的任何源码。
 */
enum class HapticLevel {
    /** 选中、轻点。 */
    Tick,
    /** 确认、发送、完成。 */
    Light,
    /** 停止、长按、接管、远程任务到达。 */
    Medium,
    /** 不可逆动作被批准。 */
    Heavy,
    /** Maeve 独有:被护栏拦下、授权失效。和 Heavy 的手感必须明显不同。 */
    Reject,
}

/** 把 Android 振动器隔离成可替换接口;单测用 FakeActuator 验证「关了就是零发射」。 */
interface HapticActuator {
    fun supports(effect: Int): Boolean
    fun predefined(effect: Int)
    fun waveform(timings: LongArray, amplitudes: IntArray)
}

interface HapticEngine {
    fun emit(level: HapticLevel)
    val enabled: StateFlow<Boolean>

    /** 便捷入口:页面只认 `MikaTheme.haptics`,不必再认识五级语义或新增 import。 */
    fun tick() = emit(HapticLevel.Tick)
    fun light() = emit(HapticLevel.Light)
    fun medium() = emit(HapticLevel.Medium)
    fun heavy() = emit(HapticLevel.Heavy)
    fun reject() = emit(HapticLevel.Reject)
}

/** 纯逻辑引擎:门控 + 五级映射 + 降级链。不依赖 Android 框架,可直接单测。 */
class DefaultHapticEngine(
    private val actuator: HapticActuator,
    private val gate: () -> Boolean,
) : HapticEngine {
    private val state = MutableStateFlow(gate())
    override val enabled: StateFlow<Boolean> = state.asStateFlow()

    /** 系统设置或 App 开关变化后重新求值。 */
    fun refresh() {
        state.value = gate()
    }

    override fun emit(level: HapticLevel) {
        if (!state.value) return
        emitLevel(actuator, level)
    }
}

/** 五级 -> Android 预定义效果,按「不支持就降一级」顺序尝试;全不支持时静默或极短波形。 */
internal fun emitLevel(a: HapticActuator, level: HapticLevel) {
    if (level == HapticLevel.Reject) {
        // 两下短促、下沉的脉冲:不是「你确认了」的正向点击。设备不支持振幅控制时
        // AndroidHapticActuator 会直接忽略 —— 宁可静默,也不要发出积极的 click。
        a.waveform(longArrayOf(0, 18, 42, 24), intArrayOf(0, 110, 0, 190))
        return
    }
    val chain = when (level) {
        HapticLevel.Tick -> intArrayOf(VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_CLICK)
        HapticLevel.Light -> intArrayOf(VibrationEffect.EFFECT_CLICK, VibrationEffect.EFFECT_TICK)
        HapticLevel.Medium -> intArrayOf(VibrationEffect.EFFECT_HEAVY_CLICK, VibrationEffect.EFFECT_CLICK)
        HapticLevel.Heavy -> intArrayOf(VibrationEffect.EFFECT_DOUBLE_CLICK, VibrationEffect.EFFECT_HEAVY_CLICK)
        HapticLevel.Reject -> return
    }
    for (effect in chain) {
        if (a.supports(effect)) {
            a.predefined(effect)
            return
        }
    }
    // Heavy 不能降级成一声轻响:那会让人误以为危险动作已获批准。
    if (level != HapticLevel.Heavy) a.waveform(longArrayOf(0, 12), intArrayOf(0, 90))
}

/** API 30 用 Vibrator,31+ 用 VibratorManager;所有调用都吞掉异常,触感永远不能让 App 崩。 */
class AndroidHapticActuator(context: Context) : HapticActuator {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val hasAmplitude = vibrator?.hasAmplitudeControl() == true

    override fun supports(effect: Int): Boolean {
        val v = vibrator ?: return false
        return runCatching {
            v.areEffectsSupported(effect)[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
        }.getOrDefault(false)
    }

    override fun predefined(effect: Int) {
        val v = vibrator ?: return
        runCatching { v.vibrate(VibrationEffect.createPredefined(effect)) }
    }

    override fun waveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!hasAmplitude) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1)) }
    }
}

/** 进程级单例。系统「触感反馈」总开关 + App 内开关,两者都为真才发射。 */
object MaeveHaptics {
    private const val PREF = "maeve_haptics"
    private const val KEY_APP = "app_enabled"

    @Volatile
    private var cached: DefaultHapticEngine? = null
    private val lock = Any()

    fun get(context: Context): HapticEngine = cached ?: synchronized(lock) {
        cached ?: create(context.applicationContext).also { cached = it }
    }

    fun appEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_APP, true)

    /** 设置页开关;当场生效,不需要重启界面。 */
    fun setAppEnabled(context: Context, value: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_APP, value).apply()
        cached?.refresh()
    }

    private fun create(app: Context): DefaultHapticEngine {
        val gate = {
            val systemOn = runCatching {
                Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
            }.getOrDefault(true)
            systemOn && appEnabled(app)
        }
        val engine = DefaultHapticEngine(AndroidHapticActuator(app), gate)
        val uri = Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED)
        app.contentResolver.registerContentObserver(
            uri, false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = engine.refresh()
            },
        )
        return engine
    }
}

/** 预览和测试里没有真实振动器时的默认实现。 */
object NoopHapticEngine : HapticEngine {
    private val off = MutableStateFlow(false)
    override val enabled: StateFlow<Boolean> = off.asStateFlow()
    override fun emit(level: HapticLevel) = Unit
}

val LocalHapticEngine = staticCompositionLocalOf<HapticEngine> { NoopHapticEngine }

@Composable
fun rememberHapticEngine(): HapticEngine {
    val context = LocalContext.current
    return remember(context) { MaeveHaptics.get(context) }
}
