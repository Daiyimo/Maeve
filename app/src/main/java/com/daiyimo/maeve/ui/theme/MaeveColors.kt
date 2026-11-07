package com.daiyimo.maeve.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 界面用到的全部颜色。页面一律从 [MaeveTheme.colors] 取,不写死色值。
 *
 * ## 这套配色照 Linear 的规矩来
 *
 * 参考 linear.app 的公开设计规格,照搬它的四条硬规矩:
 *
 * 1. **一个彩色。** 全站只有 [accent] 一种有彩度的颜色,只用在品牌标、焦点环、
 *    每屏一个主按钮。状态(成功/警告/失败)**不靠色相**,靠图形、字重和表面层级。
 * 2. **表面阶梯代替阴影。** [surfaceLowest] → [bg] → [surface] → [surfaceHigh] →
 *    [surfaceHighest] 五级,相邻两级只差 4–6 个明度点,几乎看不出来;
 *    层级由**发丝线**(0.5dp,[hair]/[rule]/[edge])承担。全站零投影。
 * 3. **画布是中性灰,不是暖白。** 上一版纸面偏黄,大面积铺开显脏、显旧。
 *    现在回到中性偏一点冷(色相 250°,彩度千分之二),和屏幕、系统 UI 都不打架。
 * 4. **文字用四级灰,不用彩色说话。** ink / ink2 / ink3 / ink4 全是从画布底色
 *    按明度阶梯推出来的同一个灰。
 *
 * ## 和上一版的差别
 *
 * 上一版有**三条语义轨**(暖梅子 / 冷青 / 琥珀)外加暖纸底,四个色系同时争夺注意力,
 * 结果就是"花"。现在冷青那一族**保留字段但全部指向中性灰**——[machine] 等于 [ink2],
 * [machineSoft] 等于 [surface],所有旧调用点不用改一行,视觉上自动变干净。
 * 真要区分"机器在说话",用等宽字体和图标,不用颜色。
 */
@Immutable
class MaeveColors(
    /** 页面背景(画布)。 */
    val bg: Color,
    /** 页面渐变的顶端/底端;现在两者几乎相同,只留一丝方向感。 */
    val bgTop: Color,
    val bgBottom: Color,
    /** 纸面卡:比画布**高一级**的表面。 */
    val surface: Color,
    // ---------- 五级表面阶梯(低 → 高) ----------
    val surfaceLowest: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    /** 浅填充:次按钮、分段控件底、代码块底。 */
    val fill: Color,
    /** 文字四级:正文 / 次要 / 辅助 / 极弱。 */
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    /** 行分隔线。 */
    val hair: Color,
    /** 更重的一道:台词轨、诊断框、选中态。 */
    val rule: Color,
    /** 卡片描边。 */
    val edge: Color,
    // ---------- 唯一的彩色 ----------
    val accent: Color,
    val accentPressed: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val accentBubble: Color,
    val onAccentBubble: Color,
    val accentGlow: Color,
    val accentSoft: Color,
    val onAccent: Color,
    // ---------- 状态:靠图形和字重,不靠色相 ----------
    /** 成功:极低彩度的绿,只用在勾、进度满格。 */
    val ok: Color,
    val okSoft: Color,
    /** 需要你:极省地用,只在待确认那一处。 */
    val warn: Color,
    val warnSolid: Color,
    val warnSoft: Color,
    val bad: Color,
    val badSoft: Color,
    /** 图标块的底。 */
    val neutralSoft: Color,
    /** 兼容位:纸面时代不再给回答套底。 */
    val answer: Color,
    /** 灰色胶囊、关着的开关。 */
    val greySoft: Color,
    // ---------- 毛玻璃四档填充 ----------
    /** Chrome 档(顶栏 / 输入栏)的白度。 */
    val glass: Color,
    /** Sheet 档(弹层 / 侧栏)的填充;深色下要和 card 分开,否则弹层与卡片糊在一起。 */
    val glassSheet: Color,
    val glassSheetDark: Color,
    /** Card 档(平放的大件)。 */
    val glassCard: Color,
    /** Pill 档(小件)。 */
    val glassPill: Color,
    val glassEdge: Color,
    val glassRim: Color,
    val cardShadow: Color,
    val floatShadow: Color,
    val thumb: Color,
    val scrim: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    /** 第三团极光:屏幕下半部的一道冷色,和左上暖色对冲,玻璃透出来的颜色才有冷暖层次。 */
    val glowTertiary: Color,
    val series: List<Color>,
    val seriesOther: Color,
    val grid: Color,
    val isDark: Boolean,
    /** 兼容位。上一版的冷青诊断轨;现在全部指向中性灰,见类注释。 */
    val machine: Color,
    val machineSoft: Color,
    val machineEdge: Color,
    val machineDim: Color,
    /** 兼容位。上一版的琥珀信号轨;现在指向 [warn]。 */
    val signal: Color,
    val signalSoft: Color,
)

/**
 * 由 [AccentSpec] 出 [MaeveColors]。
 *
 * 中性色相固定在中性偏冷的一侧([NEUTRAL_HUE]),**不再随强调色转动**——
 * 这是上一版最伤的一点:配色一换,整张纸的色相跟着跑,界面失去稳定性。
 * 现在强调色只影响 [accent] 一族,画布永远是同一个灰。
 */
object MaevePalette {

    /** 中性画布的色相:偏冷一点点(蓝侧),彩度极低。 */
    private const val NEUTRAL_HUE = 250f

    fun light(spec: AccentSpec): MaeveColors {
        fun n(L: Float, C: Float) = Oklch(L, C, NEUTRAL_HUE).toColor()
        // Linear 的暗色阶梯是 #010102 → #0f1011 → #141516 → #18191a → #191a1b,
        // 相邻差约 4 个明度点。浅色照同样的密度反推。
        val canvas = n(0.973f, 0.0016f)
        val ink = n(0.208f, 0.006f)
        val ink2c = guardText(n(0.452f, 0.005f), canvas, 4.5f)
        val ink3c = guardText(n(0.585f, 0.004f), canvas, 3.0f)
        return MaeveColors(
            bg = canvas,
            bgTop = n(0.978f, 0.0014f),
            bgBottom = n(0.963f, 0.0018f),
            // ★ 这些"表面"色本身是**半透明**的:全站几十处直接拿它当底画矩形,
            // 如果给不透明值,那些地方就永远成不了玻璃。改成半透明白之后,
            // 它们在极光底上自动就是一层薄玻璃,不需要逐个改调用点。
            // 白度取 0.58:太低(0.46)玻璃和背景只差 1~2 个色阶,
            // 会看不见层——"太透等于没有层"。0.58 在浅色极光上刚好立得住,
            // 又明显比纯白纸"薄"
            surface = Color.White.copy(alpha = 0.58f),
            surfaceLowest = Color.White.copy(alpha = 0.30f),
            surfaceHigh = Color.White.copy(alpha = 0.68f),
            surfaceHighest = Color.White.copy(alpha = 0.74f),
            fill = ink.copy(alpha = 0.045f),
            ink = ink,
            ink2 = ink2c,
            ink3 = ink3c,
            ink4 = guardText(n(0.700f, 0.003f), canvas, 2.2f),
            hair = ink.copy(alpha = 0.070f),
            rule = ink.copy(alpha = 0.135f),
            edge = ink.copy(alpha = 0.080f),
            accent = spec.lightSeed,
            accentPressed = spec.lightPressed,
            accentContainer = spec.lightContainer.copy(alpha = 0.60f),
            onAccentContainer = guardText(spec.lightOnContainer, spec.lightContainer, 4.5f),
            accentBubble = spec.lightSeed,
            onAccentBubble = Color.White,
            accentGlow = spec.lightGlow,
            accentSoft = spec.lightContainer.copy(alpha = 0.55f),
            onAccent = Color.White,
            ok = Oklch(0.478f, 0.088f, 152f).toColor(),
            okSoft = Oklch(0.945f, 0.022f, 152f).toColor().copy(alpha = 0.55f),
            warn = Oklch(0.500f, 0.105f, 60f).toColor(),
            warnSolid = Oklch(0.580f, 0.115f, 60f).toColor(),
            warnSoft = Oklch(0.950f, 0.028f, 60f).toColor().copy(alpha = 0.55f),
            bad = Oklch(0.492f, 0.140f, 26f).toColor(),
            badSoft = Oklch(0.948f, 0.026f, 26f).toColor().copy(alpha = 0.55f),
            neutralSoft = ink.copy(alpha = 0.048f),
            answer = Color.Transparent,
            greySoft = ink.copy(alpha = 0.075f),
            // Chrome 0.62:要压住滚动内容,又不能白成一块塑料
            glass = Color.White.copy(alpha = 0.62f),
            // Sheet 0.74:弹层盖大面积内容,背后不能太抢
            glassSheet = Color.White.copy(alpha = 0.74f),
            glassSheetDark = Color.White.copy(alpha = 0.74f),
            // Card 0.44:平放的卡要真的"透"——这是整站出现最多的一档,
            // 白度一高就退回白纸,极光一点也透不上来
            glassCard = Color.White.copy(alpha = 0.58f),
            glassPill = Color.White.copy(alpha = 0.62f),
            // 边是玻璃唯一"硬"的轮廓。淡到 0.085 时整块玻璃没有边界,
            // 看起来就是一片发白的水渍;0.16 才能读出"这是一片薄片"
            glassEdge = ink.copy(alpha = 0.16f),
            // 内高光用纯白:光打在玻璃边上就是白的
            glassRim = Color.White.copy(alpha = 0.95f),
            cardShadow = Color.Black,
            floatShadow = Color.Black,
            thumb = Color.White,
            scrim = Color.Black.copy(alpha = 0.30f),
            // 极光是给玻璃折射用的原料:够浓才透得出颜色,但又不能把底染成花纸
            glowPrimary = spec.lightGlow.copy(alpha = 0.26f),
            glowSecondary = spec.hazeLight.copy(alpha = 0.18f),
            glowTertiary = Color(0xFF3E6C9E).copy(alpha = 0.11f),
            series = listOf(
                Oklch(0.50f, 0.10f, 250f).toColor(),
                Oklch(0.54f, 0.09f, 55f).toColor(),
                Oklch(0.48f, 0.07f, 158f).toColor(),
                Oklch(0.50f, 0.09f, 320f).toColor(),
            ),
            seriesOther = n(0.660f, 0.004f),
            grid = ink.copy(alpha = 0.055f),
            isDark = false,
            // —— 兼容位:全部归到中性 ——
            machine = ink2c,
            machineSoft = Color.White.copy(alpha = 0.40f),
            machineEdge = ink.copy(alpha = 0.080f),
            machineDim = ink3c,
            signal = Oklch(0.500f, 0.105f, 60f).toColor(),
            signalSoft = Oklch(0.950f, 0.028f, 60f).toColor().copy(alpha = 0.55f),
        )
    }

    fun dark(spec: AccentSpec): MaeveColors {
        fun n(L: Float, C: Float) = Oklch(L, C, NEUTRAL_HUE).toColor()
        // 直接对齐 Linear:#08090a / #0f1011 / #141516 / #18191a / #191a1b
        val canvas = n(0.145f, 0.0022f)
        val ink = n(0.962f, 0.0016f)
        val ink2c = guardText(n(0.740f, 0.003f), canvas, 4.5f)
        val ink3c = guardText(n(0.580f, 0.004f), canvas, 3.0f)
        return MaeveColors(
            bg = canvas,
            bgTop = n(0.157f, 0.0024f),
            bgBottom = n(0.132f, 0.0020f),
            // 深色同理:半透明的浅黑,底下透出极光
            surface = Color.White.copy(alpha = 0.062f),
            surfaceLowest = Color.White.copy(alpha = 0.028f),
            surfaceHigh = Color.White.copy(alpha = 0.088f),
            surfaceHighest = Color.White.copy(alpha = 0.115f),
            fill = ink.copy(alpha = 0.058f),
            ink = ink,
            ink2 = ink2c,
            ink3 = ink3c,
            ink4 = guardText(n(0.470f, 0.004f), canvas, 2.2f),
            hair = ink.copy(alpha = 0.085f),
            rule = ink.copy(alpha = 0.155f),
            edge = ink.copy(alpha = 0.095f),
            accent = spec.darkSeed,
            accentPressed = spec.darkPressed,
            accentContainer = spec.darkContainer,
            onAccentContainer = guardText(spec.darkOnContainer, spec.darkContainer, 4.5f),
            accentBubble = spec.darkBubble,
            onAccentBubble = spec.darkOnBubble,
            accentGlow = spec.darkGlow,
            accentSoft = spec.darkContainer.copy(alpha = 0.60f),
            onAccent = spec.darkOnSeed,
            ok = Oklch(0.760f, 0.100f, 152f).toColor(),
            okSoft = Oklch(0.268f, 0.038f, 152f).toColor().copy(alpha = 0.60f),
            warn = Oklch(0.780f, 0.110f, 62f).toColor(),
            warnSolid = Oklch(0.740f, 0.115f, 62f).toColor(),
            warnSoft = Oklch(0.300f, 0.045f, 62f).toColor().copy(alpha = 0.60f),
            bad = Oklch(0.700f, 0.150f, 26f).toColor(),
            badSoft = Oklch(0.300f, 0.055f, 26f).toColor().copy(alpha = 0.60f),
            neutralSoft = ink.copy(alpha = 0.055f),
            answer = Color.Transparent,
            greySoft = ink.copy(alpha = 0.125f),
            // 深色下玻璃靠「白度」立起来:iOS 深色玻璃是极低透光的黑玻璃,
            // 填充其实是「黑 + 一点点白」,不是「白 + 一点点黑」
            glass = n(0.215f, 0.0026f).copy(alpha = 0.68f),
            glassSheet = n(0.245f, 0.0028f).copy(alpha = 0.82f),
            glassSheetDark = n(0.245f, 0.0028f).copy(alpha = 0.82f),
            glassCard = n(0.195f, 0.0026f).copy(alpha = 0.50f),
            glassPill = n(0.215f, 0.0026f).copy(alpha = 0.60f),
            glassEdge = ink.copy(alpha = 0.11f),
            // 深色玻璃的边光要比浅色弱:强了会变成一圈白描边
            glassRim = Color.White.copy(alpha = 0.20f),
            cardShadow = Color.Transparent,
            floatShadow = Color.Black,
            thumb = Color.White,
            scrim = Color.Black.copy(alpha = 0.50f),
            glowPrimary = spec.darkGlow.copy(alpha = 0.30f),
            glowSecondary = spec.hazeDark.copy(alpha = 0.22f),
            glowTertiary = Color(0xFF2B5A94).copy(alpha = 0.20f),
            series = listOf(
                Oklch(0.720f, 0.090f, 250f).toColor(),
                Oklch(0.760f, 0.085f, 55f).toColor(),
                Oklch(0.700f, 0.070f, 158f).toColor(),
                Oklch(0.720f, 0.080f, 320f).toColor(),
            ),
            seriesOther = n(0.520f, 0.004f),
            grid = ink.copy(alpha = 0.065f),
            isDark = true,
            machine = ink2c,
            machineSoft = Color.White.copy(alpha = 0.05f),
            machineEdge = ink.copy(alpha = 0.095f),
            machineDim = ink3c,
            signal = Oklch(0.780f, 0.110f, 62f).toColor(),
            signalSoft = Oklch(0.300f, 0.045f, 62f).toColor().copy(alpha = 0.60f),
        )
    }
}

/** 没有包在 [MaeveTheme] 里时(比如界面测试)按默认配色(梅子)的浅色。 */
val LocalMaeveColors = staticCompositionLocalOf { MaevePalette.light(deriveAccent(AccentPreset.Reverie)) }

/** 默认配色(梅子)的浅色。界面测试等没有 Theme 包裹时用。 */
val LightColors: MaeveColors get() = MaevePalette.light(deriveAccent(AccentPreset.Reverie))

/** 默认配色(梅子)的深色。 */
val DarkColors: MaeveColors get() = MaevePalette.dark(deriveAccent(AccentPreset.Reverie))
