package com.daiyimo.maeve.ui.components

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AGSL 透镜几何的纯 Kotlin 镜像,避免把像素验收压在 Robolectric 上。 */
class GlassLensGeometryTest {
    @Test
    fun centerHasNoDisplacement() {
        val half = Vec2(100f, 40f)
        val radii = Radii(20f, 20f, 20f, 20f)
        val d = sdRoundRect(Vec2(0f, 0f), half, radii)
        val band = edgeWeight(d, 16f)
        val shift = displacement(Vec2(0f, 0f), half, radii, 16f, 14f)
        assertEquals(0f, band, EPS)
        assertEquals(0f, shift.length, EPS)
    }

    @Test
    fun edgeReachesTargetAndSamplesInward() {
        val half = Vec2(100f, 40f)
        val radii = Radii(20f, 20f, 20f, 20f)
        val left = displacement(Vec2(-100f, 0f), half, radii, 16f, 14f)
        val right = displacement(Vec2(100f, 0f), half, radii, 16f, 14f)
        val top = displacement(Vec2(0f, -40f), half, radii, 16f, 14f)
        val bottom = displacement(Vec2(0f, 40f), half, radii, 16f, 14f)

        assertEquals(14f, left.length, EPS)
        assertTrue("left edge must sample toward +x", left.x > 0f)
        assertTrue("right edge must sample toward -x", right.x < 0f)
        assertTrue("top edge must sample toward +y", top.y > 0f)
        assertTrue("bottom edge must sample toward -y", bottom.y < 0f)
    }

    @Test
    fun edgeWeightIsMonotonicAndBounded() {
        assertEquals(0f, edgeWeight(-16f, 16f), EPS)
        assertEquals(1f, edgeWeight(0f, 16f), EPS)
        val near = edgeWeight(-4f, 16f)
        val far = edgeWeight(-12f, 16f)
        assertTrue(near > far)
        assertTrue(near in 0f..1f)
        assertTrue(far in 0f..1f)
    }

    @Test
    fun chromaticChannelsSampleOppositeDirections() {
        val half = Vec2(100f, 40f)
        val radii = Radii(20f, 20f, 20f, 20f)
        val p = Vec2(-100f, 0f)
        val n = normal(p, half, radii)
        val dispersion = n * 0.65f
        val redOffset = dispersion
        val blueOffset = dispersion * -1f
        assertTrue(redOffset.dot(blueOffset) < 0f)
        assertEquals(0.65f, redOffset.length, EPS)
    }

    @Test
    fun bleedCoversRefractionAndBothBlurPaths() {
        val density = 1f
        val refractiveSigma = 3f * density
        val fallbackSigma = 6f * density
        val refraction = 14f * density
        val bleed = requiredBleed(refractiveSigma, fallbackSigma, refraction, density)
        assertTrue(bleed >= 3f * refractiveSigma)
        assertTrue(bleed >= 3f * fallbackSigma)
        assertTrue(bleed >= refraction + 2f * density)
    }

    @Test
    fun fourCornerRadiiAreAccepted() {
        val half = Vec2(100f, 40f)
        val radii = Radii(4f, 20f, 8f, 16f)
        assertEquals(0f, sdRoundRect(Vec2(-100f, 0f), half, radii), EPS)
        assertEquals(0f, sdRoundRect(Vec2(100f, 0f), half, radii), EPS)
        assertTrue(sdRoundRect(Vec2(0f, 0f), half, radii) < 0f)
        assertTrue(sdRoundRect(Vec2(120f, 60f), half, radii) > 0f)
    }

    private fun displacement(
        p: Vec2,
        half: Vec2,
        radii: Radii,
        width: Float,
        strength: Float,
    ): Vec2 = normal(p, half, radii) * (-strength * edgeWeight(sdRoundRect(p, half, radii), width))

    private fun normal(p: Vec2, half: Vec2, radii: Radii): Vec2 {
        val e = 0.75f
        val grad = Vec2(
            sdRoundRect(p + Vec2(e, 0f), half, radii) - sdRoundRect(p - Vec2(e, 0f), half, radii),
            sdRoundRect(p + Vec2(0f, e), half, radii) - sdRoundRect(p - Vec2(0f, e), half, radii),
        )
        return grad / max(grad.length, 0.0001f)
    }

    private fun sdRoundRect(p: Vec2, half: Vec2, radii: Radii): Float {
        val radius = if (p.y < 0f) {
            if (p.x < 0f) radii.topLeft else radii.topRight
        } else {
            if (p.x > 0f) radii.bottomRight else radii.bottomLeft
        }
        val q = Vec2(abs(p.x) - half.x + radius, abs(p.y) - half.y + radius)
        return sqrt(max(q.x, 0f) * max(q.x, 0f) + max(q.y, 0f) * max(q.y, 0f)) +
            minOf(max(q.x, q.y), 0f) - radius
    }

    private fun edgeWeight(d: Float, width: Float): Float =
        smoothstep(-max(width, 0.0001f), 0f, d)

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun requiredBleed(
        refractiveSigma: Float,
        fallbackSigma: Float,
        refraction: Float,
        density: Float,
    ): Float = max(max(refractiveSigma, fallbackSigma) * 3f, refraction + 2f * density)

    private data class Vec2(val x: Float, val y: Float) {
        operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
        operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
        operator fun times(scale: Float) = Vec2(x * scale, y * scale)
        operator fun div(scale: Float) = Vec2(x / scale, y / scale)
        val length: Float get() = sqrt(x * x + y * y)
        fun dot(other: Vec2): Float = x * other.x + y * other.y
    }

    private data class Radii(
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float,
    )

    private companion object {
        const val EPS = 0.001f
    }
}
