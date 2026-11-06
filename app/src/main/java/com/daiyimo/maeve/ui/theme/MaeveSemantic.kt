package com.daiyimo.maeve.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 运行步骤的视觉类别。名字 → 类别的映射在各 UI 的图标选择处。
 * 色相本身就是语义:一张「读文件」的行在任何配色下都必须长得一样。
 */
enum class ToolTone {
    /** 跑命令、shell。 */
    Terminal,
    /** 读屏幕、读文件。 */
    Read,
    /** 点、写、改。 */
    Edit,
    /** 找、搜。 */
    Search,
    /** 联网、开网页。 */
    Web,
    /** 子任务、另一个自己在干活。 */
    Agent,
    /** 计划、等你确认。 */
    Plan,
    /** 其他、未知。 */
    Neutral,
}

/**
 * 语义色:与强调色**无关**的固定色。状态色一律低饱和——
 * 鲜绿鲜红一出现,界面立刻变成"运维面板"。
 *
 * 全部由 OKLCH 现推(固定色相 + 明度带),不抄任何第三方色值:
 * 文字对其底色 ≥ 4.5,容器上的 on-色 ≥ 4.5,图形 ≥ 3。
 */
@Immutable
class MaeveSemantic private constructor(
    val ok: Color,
    val okContainer: Color,
    val warn: Color,
    val warnSolid: Color,
    val warnContainer: Color,
    val bad: Color,
    val badContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val toolContainers: List<Color>,
    val onToolContainers: List<Color>,
    val identity: List<Color>,
    val onIdentity: List<Color>,
) {
    /** 工具类别的 (容器, on-容器)。 */
    fun tool(tone: ToolTone): Pair<Color, Color> =
        toolContainers[tone.ordinal] to onToolContainers[tone.ordinal]

    /**
     * 由稳定字符串(对话 id)映射到身份色下标。
     * 不用 hashCode:各平台都不保证跨进程稳定,重启后颜色会整体重排。
     * 固定 FNV-1a。
     */
    fun identityIndex(key: String, slots: Int = identity.size): Int {
        var hash = 0x811c9dc5.toInt()
        for (i in key.indices) {
            hash = hash xor key[i].code
            hash *= 0x01000193
        }
        return (hash.toLong() and 0xFFFFFFFFL).mod(slots.toLong()).toInt()
    }

    companion object {
        // 状态色相:成功 150、警告 70、危险 25、信息 245
        private const val H_OK = 150f
        private const val H_WARN = 70f
        private const val H_BAD = 25f
        private const val H_INFO = 245f

        /** 工具色调的色相(顺序 = [ToolTone])。 */
        private val TOOL_HUES = listOf(260f, 220f, 60f, 290f, 160f, 330f, 190f, 0f)

        /** 身份色的色相:八家"宿主",绕色相环均匀取。 */
        private val IDENTITY_HUES = listOf(220f, 270f, 320f, 20f, 60f, 140f, 180f, 250f)

        val light = build(
            textL = 0.45f, textC = 0.11f,
            containerL = 0.92f, containerC = 0.05f,
            onContainerL = 0.32f, onContainerC = 0.09f,
            solidL = 0.62f, solidC = 0.13f,
        )

        val dark = build(
            textL = 0.78f, textC = 0.10f,
            containerL = 0.30f, containerC = 0.055f,
            onContainerL = 0.88f, onContainerC = 0.07f,
            solidL = 0.70f, solidC = 0.12f,
        )

        private fun build(
            textL: Float, textC: Float,
            containerL: Float, containerC: Float,
            onContainerL: Float, onContainerC: Float,
            solidL: Float, solidC: Float,
        ) = MaeveSemantic(
            ok = Oklch(textL, textC, H_OK).toColor(),
            okContainer = Oklch(containerL, containerC, H_OK).toColor(),
            warn = Oklch(textL, textC, H_WARN).toColor(),
            warnSolid = Oklch(solidL, solidC, H_WARN).toColor(),
            warnContainer = Oklch(containerL, containerC, H_WARN).toColor(),
            bad = Oklch(textL, textC, H_BAD).toColor(),
            badContainer = Oklch(containerL, containerC, H_BAD).toColor(),
            info = Oklch(textL, textC, H_INFO).toColor(),
            infoContainer = Oklch(containerL, containerC, H_INFO).toColor(),
            toolContainers = TOOL_HUES.map { h ->
                if (h == 0f) Oklch(containerL + 0.01f, 0.012f, 260f).toColor()
                else Oklch(containerL, containerC + 0.01f, h).toColor()
            },
            onToolContainers = TOOL_HUES.map { h ->
                if (h == 0f) Oklch(onContainerL, 0.014f, 260f).toColor()
                else Oklch(onContainerL, onContainerC + 0.02f, h).toColor()
            },
            identity = IDENTITY_HUES.map { h -> Oklch(containerL, containerC + 0.008f, h).toColor() },
            onIdentity = IDENTITY_HUES.map { h -> Oklch(onContainerL, onContainerC + 0.02f, h).toColor() },
        )
    }
}

/** 语义色的 CompositionLocal;由 [MaeveTheme] 提供。 */
val LocalMaeveSemantic = staticCompositionLocalOf { MaeveSemantic.light }
