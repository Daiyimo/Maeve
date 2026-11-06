package com.daiyimo.maeve.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Maeve 的六套配色。取名来自《西部世界》的意象:
 *
 * | id | 名字 | 意象 |
 * |---|---|---|
 * | [Reverie] | 雾紫 | 默认。梦游、觉醒前的迷雾 |
 * | [Dolores] | 晨金 | 首发代号。牧场晨光 |
 * | [Maze] | 沙褐 | 迷宫。 Maeve 找路的那座 |
 * | [Pearl] | 珠白 | 珍珠。冷静的旁观者 |
 * | [Sublime] | 深靛 | 升华。甜水镇夜里的蓝 |
 * | [Graphite] | 石墨 | 近无色。Cradle 的金属 |
 *
 * 每套只定**种子**(浅/深各一枚)和中性色的色调,其余色位按 [AccentSpec] 的
 * 阶梯现推,推完过对比度守卫。数值是设计决策,见 docs/spec/visual-tokens.md §1–§2。
 */
enum class AccentPreset(val id: String, val lightSeed: Long, val darkSeed: Long, val tintChroma: Float) {
    Reverie("reverie", 0xFF6E6296, 0xFFB3A8D6, 1.0f),
    Dolores("dolores", 0xFF8A6A3E, 0xFFD8BC93, 0.8f),
    Maze("maze", 0xFF7A6248, 0xFFC9AE93, 0.9f),
    Pearl("pearl", 0xFF4F6472, 0xFFAFC3CE, 0.7f),
    Sublime("sublime", 0xFF3A4A7A, 0xFF9BAEDD, 0.9f),
    Graphite("graphite", 0xFF2C2E32, 0xFFE5E8ED, 0.15f),
    ;

    companion object {
        fun byId(id: String?): AccentPreset = entries.firstOrNull { it.id == id } ?: Reverie
    }
}

/** 自定义色(色轮)或跟随壁纸时的来源标记。 */
const val ACCENT_CUSTOM = "custom"
const val ACCENT_WALLPAPER = "wallpaper"

/**
 * 一套配色推导完的全部色位。[MaevePalette] 拿它出深浅两色的 [MaeveColors]。
 *
 * 阶梯(与 dsh-Go 的 accent.dart 同一组设计决策,OKLCH 明度/彩度倍数):
 *
 * - 浅色:seed/pressed L 0.53→0.47;container L 0.93 彩度 ×0.26;bubble L 0.918 ×0.29;glow L 0.819 ×0.68
 * - 深色:seed L 0.764 ×0.76;pressed L 0.704;onSeed L 0.231 ×0.39;container L 0.361 ×0.51;bubble L 0.314 ×0.29
 *
 * 中性色(页面、玻璃、文字)的色相转到 [tintHue],彩度乘 [tintChroma] —— 换配色时
 * 各级文字对比度不变,只有色调在走。
 */
@Immutable
data class AccentSpec(
    val id: String,
    val hue: Float,
    val chroma: Float,
    val tintHue: Float,
    val tintChroma: Float,
    val lightSeed: Color,
    val lightPressed: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val lightBubble: Color,
    val lightGlow: Color,
    val darkSeed: Color,
    val darkPressed: Color,
    val darkOnSeed: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val darkBubble: Color,
    val darkGlow: Color,
    val hazeLight: Color,
    val hazeDark: Color,
) {
    val label: String get() = when {
        id == ACCENT_CUSTOM -> "自定义"
        id == ACCENT_WALLPAPER -> "壁纸"
        else -> AccentPreset.byId(id).name
    }

    /** 按亮度取种子。 */
    fun seedFor(dark: Boolean): Color = if (dark) darkSeed else lightSeed
}

/**
 * 从预设/自定义/壁纸推出 [AccentSpec]。三条来源走同一个 derive,没有单独代码路径。
 */
fun deriveAccent(preset: AccentPreset): AccentSpec {
    val light = hex(preset.lightSeed).toOklch()
    val dark = hex(preset.darkSeed).toOklch()
    val hue = light.h
    // 守卫:浅色种子配白字 ≥ 4.5,深色种子配深墨字 ≥ 4.5(种子太亮/太暗时调明度,色相不动)
    val lightSeed = guardSeed(light, onLight = true)
    val darkSeed = guardSeed(dark, onLight = false)
    val c = lightSeed.toOklch().C
    val cd = darkSeed.toOklch().C
    return AccentSpec(
        id = preset.id,
        hue = hue,
        chroma = c,
        tintHue = hue,
        tintChroma = preset.tintChroma,
        lightSeed = lightSeed,
        lightPressed = Oklch(lightSeed.toOklch().L - 0.06f, c * 0.95f, hue).toColor(),
        lightContainer = Oklch(0.93f, c * 0.26f, hue).toColor(),
        lightOnContainer = Oklch(0.33f, c * 0.70f, hue).toColor(),
        lightBubble = Oklch(0.918f, c * 0.29f, hue).toColor(),
        lightGlow = Oklch(0.819f, c * 0.68f, hue).toColor(),
        darkSeed = darkSeed,
        darkPressed = Oklch(darkSeed.toOklch().L - 0.06f, cd * 0.95f, hue).toColor(),
        darkOnSeed = Oklch(0.231f, cd * 0.39f, hue).toColor(),
        darkContainer = Oklch(0.361f, cd * 0.51f, hue).toColor(),
        darkOnContainer = Oklch(0.909f, cd * 0.24f, hue).toColor(),
        darkBubble = Oklch(0.314f, cd * 0.29f, hue).toColor(),
        darkGlow = Oklch(0.52f, cd * 0.72f, hue).toColor(),
        hazeLight = Oklch(0.80f, c * 0.45f, normDeg(hue + 24f)).toColor(),
        hazeDark = Oklch(0.45f, cd * 0.55f, normDeg(hue + 24f)).toColor(),
    )
}

/** 自定义色:色轮给色相和彩度,明度按浅/深各自的带现推。 */
fun deriveCustom(hue: Float, chroma: Float): AccentSpec {
    val c = chroma.coerceIn(0f, 0.13f)
    val lightSeed = guardSeed(Oklch(0.53f, c, normDeg(hue)), onLight = true)
    val darkSeed = guardSeed(Oklch(0.764f, c * 0.76f, normDeg(hue)), onLight = false)
    val ld = lightSeed.toOklch()
    val dd = darkSeed.toOklch()
    val h = ld.h
    return AccentSpec(
        id = ACCENT_CUSTOM,
        hue = h,
        chroma = ld.C,
        tintHue = h,
        tintChroma = 1.0f,
        lightSeed = lightSeed,
        lightPressed = Oklch(ld.L - 0.06f, ld.C * 0.95f, h).toColor(),
        lightContainer = Oklch(0.93f, ld.C * 0.26f, h).toColor(),
        lightOnContainer = Oklch(0.33f, ld.C * 0.70f, h).toColor(),
        lightBubble = Oklch(0.918f, ld.C * 0.29f, h).toColor(),
        lightGlow = Oklch(0.819f, ld.C * 0.68f, h).toColor(),
        darkSeed = darkSeed,
        darkPressed = Oklch(dd.L - 0.06f, dd.C * 0.95f, h).toColor(),
        darkOnSeed = Oklch(0.231f, dd.C * 0.39f, h).toColor(),
        darkContainer = Oklch(0.361f, dd.C * 0.51f, h).toColor(),
        darkOnContainer = Oklch(0.909f, dd.C * 0.24f, h).toColor(),
        darkBubble = Oklch(0.314f, dd.C * 0.29f, h).toColor(),
        darkGlow = Oklch(0.52f, dd.C * 0.72f, h).toColor(),
        hazeLight = Oklch(0.80f, ld.C * 0.45f, normDeg(h + 24f)).toColor(),
        hazeDark = Oklch(0.45f, dd.C * 0.55f, normDeg(h + 24f)).toColor(),
    )
}

/**
 * 壁纸取色(API 31+):只读系统强调色的**色相和彩度**,明度按 Maeve 的带现推。
 * 不是 Material You——动态取色只借色相,色位、对比度仍是自己的。
 */
fun deriveWallpaper(hue: Float, chroma: Float): AccentSpec = deriveCustom(hue, chroma.coerceIn(0f, 0.13f))

/**
 * 种子守卫:浅色种子上的白字、深色种子上的深墨字都要 ≥ 4.5。
 * 不够就挪明度(色相、彩度不动),最多挪 12 步。
 */
private fun guardSeed(seed: Oklch, onLight: Boolean): Color {
    val on = if (onLight) Color.White else Oklch(0.231f, seed.C * 0.39f, seed.h).toColor()
    if (contrastRatio(seed.toColor(), on) >= 4.5f) return seed.toColor()
    var c = seed
    repeat(12) {
        c = if (onLight) c.withLightness(c.L - 0.02f) else c.withLightness(c.L + 0.02f)
        if (contrastRatio(c.toColor(), on) >= 4.5f) return c.toColor()
    }
    return c.toColor()
}

/**
 * 配色偏好。存在 SharedPreferences,改动以 [StateFlow] 广播,界面当场换、不重建。
 *
 * 序列化:`preset:<id>` / `custom:<hue>:<chroma>` / `wallpaper`。
 */
class MaeveAccentStore private constructor(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("maeve_accent", Context.MODE_PRIVATE)
    private val _accent = MutableStateFlow(load())

    /** 当前配色。 */
    val accent: StateFlow<AccentSpec> = _accent

    private fun load(): AccentSpec {
        val raw = prefs.getString(KEY, null) ?: return deriveAccent(AccentPreset.Reverie)
        return parse(raw)
    }

    private fun parse(raw: String): AccentSpec = when {
        raw == ACCENT_WALLPAPER -> {
            val (h, c) = wallpaperSeed()
            if (h != null) deriveWallpaper(h, c) else deriveAccent(AccentPreset.Reverie)
        }
        raw.startsWith("$ACCENT_CUSTOM:") -> {
            val parts = raw.split(":")
            val h = parts.getOrNull(1)?.toFloatOrNull()
            val c = parts.getOrNull(2)?.toFloatOrNull()
            if (h != null && c != null) deriveCustom(h, c) else deriveAccent(AccentPreset.Reverie)
        }
        else -> deriveAccent(AccentPreset.byId(raw.removePrefix("preset:")))
    }

    /** 系统强调色的色相与彩度;取不到返回 null。 */
    private fun wallpaperSeed(): Pair<Float?, Float> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null to 0.1f
        val res = context.resources
        val id = res.getIdentifier("system_accent1_500", "color", "android")
        if (id == 0) return null to 0.1f
        val color = try {
            @Suppress("DEPRECATION") res.getColor(id, null)
        } catch (e: Exception) {
            null
        } ?: return null to 0.1f
        val oklch = androidx.compose.ui.graphics.Color(color).toOklch()
        return oklch.h to oklch.C.coerceIn(0.02f, 0.13f)
    }

    fun setPreset(preset: AccentPreset) = save("preset:${preset.id}")

    fun setCustom(hue: Float, chroma: Float) = save("$ACCENT_CUSTOM:${normDeg(hue)}:${chroma.coerceIn(0f, 0.13f)}")

    fun setWallpaper() = save(ACCENT_WALLPAPER)

    /** 当前是不是某一套预设(设置页高亮用)。 */
    fun currentPreset(): AccentPreset? {
        val raw = prefs.getString(KEY, null) ?: return AccentPreset.Reverie
        return if (raw.startsWith("preset:")) AccentPreset.byId(raw.removePrefix("preset:")) else null
    }

    private fun save(raw: String) {
        prefs.edit().putString(KEY, raw).apply()
        _accent.value = parse(raw)
    }

    companion object {
        private const val KEY = "accent"
        private var instance: MaeveAccentStore? = null

        fun get(context: Context): MaeveAccentStore =
            instance ?: synchronized(this) {
                instance ?: MaeveAccentStore(context).also { instance = it }
            }
    }
}
