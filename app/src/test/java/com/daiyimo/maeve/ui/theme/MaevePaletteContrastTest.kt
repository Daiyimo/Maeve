package com.daiyimo.maeve.ui.theme

import androidx.compose.ui.graphics.Color
import com.daiyimo.maeve.ui.theme.MaevePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2 设计契约的验收(visual-tokens.md §5):六套预设 × 浅深两色,
 * 全部用**计算对比度**断言,不靠眼睛看。
 *
 * - 正文 ink 对页面/卡片 ≥ 4.5:1(WCAG AA)
 * - 次级 ink2 对页面/卡片 ≥ 4.5:1
 * - 三级 ink3 对页面/卡片 ≥ 3:1(非文字)
 * - 强调色对页面 ≥ 3:1;onAccent 对强调色 ≥ 4.5:1
 * - 气泡色对 bubble 上的文字 ≥ 4.5:1(onAccentBubble/accentBubble)
 * - 状态色(ok/warn/bad)的文字档对页面 ≥ 4.5:1
 */
class MaevePaletteContrastTest {


    /**
     * 把半透明色 [fg] 按 alpha 叠到不透明底 [bg] 上,得到实际看到的颜色。
     *
     * 毛玻璃时代的表面色本身带 alpha(比如 surface = 白 58%),它们画的不是
     * 一个纯色块,而是"画布 + 一层白"。直接拿 Color 的 RGB 去算对比度,
     * 算出来的是"纯白对墨色",和屏幕上的观感完全不是一回事——深色模式下
     * surface 是白 6% 叠在黑底上,直接算会得出 1.12 这种荒谬数字。
     * 所以所有断言都先把背景合成到画布上。
     */
    private fun over(fg: Color, bg: Color): Color {
        val a = fg.alpha
        if (a >= 0.999f) return fg
        return Color(
            red = fg.red * a + bg.red * (1 - a),
            green = fg.green * a + bg.green * (1 - a),
            blue = fg.blue * a + bg.blue * (1 - a),
        )
    }

    /** 对比度:[fg] 叠在 [surface] 上(而 surface 已叠在 [canvas] 上)之后的实际比值。 */
    private fun onSurface(fg: Color, surface: Color, canvas: Color): Float =
        contrastRatio(over(fg, over(surface, canvas)), over(surface, canvas))

    private val presets = AccentPreset.entries

    @Test
    fun `all presets light and dark pass contrast guards`() {
        val failures = mutableListOf<String>()
        for (preset in presets) {
            val spec = deriveAccent(preset)
            for (dark in listOf(false, true)) {
                val c = if (dark) MaevePalette.dark(spec) else MaevePalette.light(spec)
                val tag = "${preset.id}/${if (dark) "dark" else "light"}"
                fun check(name: String, fg: Color, bg: Color, min: Float) {
                    val r = contrastRatio(fg, bg)
                    if (r < min) failures += "$tag $name ${"%.2f".format(r)} < $min"
                }
                check("ink/bg", c.ink, c.bg, 4.5f)
                check("ink/surface", over(c.ink, over(c.surface, c.bg)), over(c.surface, c.bg), 4.5f)
                check("ink2/bg", c.ink2, c.bg, 4.5f)
                check("ink2/surface", over(c.ink2, over(c.surface, c.bg)), over(c.surface, c.bg), 4.5f)
                check("ink3/bg", c.ink3, c.bg, 3.0f)
                check("ink3/surface", over(c.ink3, over(c.surface, c.bg)), over(c.surface, c.bg), 3.0f)
                check("accent/bg", c.accent, c.bg, 3.0f)
                check("onAccent/accent", c.onAccent, c.accent, 4.5f)
                check("onAccentBubble/accentBubble", c.onAccentBubble, c.accentBubble, 4.5f)
                check("ok/bg", c.ok, c.bg, 4.5f)
                check("warn/bg", c.warn, c.bg, 4.5f)
                check("bad/bg", c.bad, c.bg, 4.5f)
                check(
                    "onAccentContainer/accentContainer",
                    over(c.onAccentContainer, over(c.accentContainer, c.bg)), over(c.accentContainer, c.bg), 4.5f,
                )
            }
        }
        assertTrue("contrast failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    /**
     * 诊断轨是新加的一族:冷青的机器读数铺在极淡的冷色底上。它不像正文那样允许
     * 4.5 的宽松,机器读数通常更小更密,所以按 4.5 守;琥珀是「需要你」的信号,
     * 常常只用在一个方块或一根音符条上,按图形口径 3.0 守,但配在纸面上要够亮。
     */
    @Test
    fun `machine and signal tracks stay legible on their own surfaces`() {
        val failures = mutableListOf<String>()
        for (preset in presets) {
            val spec = deriveAccent(preset)
            for (dark in listOf(false, true)) {
                val c = if (dark) MaevePalette.dark(spec) else MaevePalette.light(spec)
                val tag = "${preset.id}/${if (dark) "dark" else "light"}"
                fun check(name: String, fg: Color, bg: Color, min: Float) {
                    val r = contrastRatio(fg, bg)
                    if (r < min) failures += "$tag $name ${"%.2f".format(r)} < $min"
                }
                check("machine/machineSoft", over(c.machine, over(c.machineSoft, c.bg)), over(c.machineSoft, c.bg), 4.5f)
                check("machine/bg", c.machine, c.bg, 4.5f)
                check("machineDim/machineSoft", over(c.machineDim, over(c.machineSoft, c.bg)), over(c.machineSoft, c.bg), 3.0f)
                check("signal/bg", c.signal, c.bg, 4.5f)
                check("signal/machineSoft", over(c.signal, over(c.machineSoft, c.bg)), over(c.machineSoft, c.bg), 3.0f)
                check("onAccent/accent", c.onAccent, c.accent, 4.5f)
            }
        }
        assertTrue("diagnostic track failures://n${failures.joinToString("//n")}", failures.isEmpty())
    }

    @Test
    fun `reverie is the default and matches the documented seeds`() {
        assertEquals("reverie", AccentPreset.Reverie.id)
        val spec = deriveAccent(AccentPreset.Reverie)
        // 规范 §2 的雾紫种子(守卫可能微调明度,色相必须保住)
        val light = hex(AccentPreset.Reverie.lightSeed).toOklch()
        assertEquals(light.h, spec.hue, 2f)
    }

    @Test
    fun `neutral surfaces keep constant lightness across presets`() {
        // 换配色只换色相,页面明度不动——各级文字对比度不漂移
        val ls = presets.map { MaevePalette.light(deriveAccent(it)) }
        val first = ls.first().bg.toOklch().L
        ls.forEach { assertEquals(first, it.bg.toOklch().L, 0.01f) }
        val inks = presets.map { MaevePalette.light(deriveAccent(it)) }
        val firstInk = inks.first().ink.toOklch().L
        inks.forEach { assertEquals(firstInk, it.ink.toOklch().L, 0.01f) }
    }

    @Test
    fun `oklch round trips within tolerance`() {
        val colors = listOf(
            Color(0xFF6E6296), Color(0xFF8A6A3E), Color(0xFF2C2E32),
            Color(0xFF4D6BFE), Color(0xFF1F9D55), Color(0xFFE5483D),
        )
        for (c in colors) {
            val back = c.toOklch().toColor()
            assertTrue("$c -> $back drift", contrastRatio(c, back) > 0.98f)
        }
    }

    @Test
    fun `out of gamut chroma reduces chroma not lightness`() {
        // 一个 sRGB 装不下的高彩度:金色相 90°、彩度 0.4
        val wild = Oklch(0.75f, 0.40f, 90f)
        val mapped = wild.toColor()
        val back = mapped.toOklch()
        assertTrue("lightness must not drift: ${back.L}", Math.abs(back.L - wild.L) < 0.02f)
        assertTrue("chroma must shrink: ${back.C}", back.C < wild.C)
    }

    @Test
    fun `identity index is stable for the same key`() {
        val s = MaeveSemantic.light
        val a = s.identityIndex("conv-123")
        val b = s.identityIndex("conv-123")
        assertEquals(a, b)
        assertTrue(a in s.identity.indices)
    }
}
