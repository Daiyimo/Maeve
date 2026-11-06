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
                check("ink/surface", c.ink, c.surface, 4.5f)
                check("ink2/bg", c.ink2, c.bg, 4.5f)
                check("ink2/surface", c.ink2, c.surface, 4.5f)
                check("ink3/bg", c.ink3, c.bg, 3.0f)
                check("ink3/surface", c.ink3, c.surface, 3.0f)
                check("accent/bg", c.accent, c.bg, 3.0f)
                check("onAccent/accent", c.onAccent, c.accent, 4.5f)
                check("onAccentBubble/accentBubble", c.onAccentBubble, c.accentBubble, 4.5f)
                check("ok/bg", c.ok, c.bg, 4.5f)
                check("warn/bg", c.warn, c.bg, 4.5f)
                check("bad/bg", c.bad, c.bg, 4.5f)
                check("onAccentContainer/accentContainer", c.onAccentContainer, c.accentContainer, 4.5f)
            }
        }
        assertTrue("contrast failures:\n${failures.joinToString("\n")}", failures.isEmpty())
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
