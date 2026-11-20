package com.daiyimo.maeve.core.vision

import com.daiyimo.maeve.core.model.ModelJson
import com.daiyimo.maeve.core.prompt.Prompts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 落点上视觉核查的结论。 */
sealed interface PointAudit {
    /** 落点处有文字且和期望相符,或是个无文字的图形控件(图标/按钮/输入框);没有期望文字时只要不是空白处就算过。 */
    data object Ok : PointAudit

    /** 落点在空白处:那一点什么都没有,点不下去。 */
    data object Blank : PointAudit

    /** 落点处的文字和期望的不是一个东西([at] 是实际在那里的文字)。 */
    data class OffTarget(val at: String) : PointAudit

    /** 看不了(截图拿不到、受保护、视觉模型出错或超时):不拦路,照旧点。 */
    data class Unavailable(val reason: String) : PointAudit
}

/** 点击之后界面变了没有的视觉结论。 */
enum class ScreenChange {
    /** 换了页面/视图/弹窗/列表:屏幕级变化。 */
    Page,

    /** 还是同一页,只有小的局部变化。 */
    Minor,

    /** 什么都看不见变。 */
    None,

    /** 看不了(截图拿不到、受保护、视觉模型出错、超时或回复读不出):按树的原判,不改。 */
    Unavailable,
}

/**
 * 点击前后的视觉核查,杜绝「工具说成功了其实没点上」:
 * - [auditPoint]:点之前,在一张截图上的落点处画小红标记,问视觉模型那一点上是什么文字或什么图标。
 *   真空白处、或文字和期望不是一回事 → 不点(调用方照失败处理);无文字的图形控件是有效落点,放行。
 * - [screenChange]:点之后,对比动作前后两张截图,问视觉模型界面是换了页面、只有小变化、还是没变。
 *   说没变而树说变化够多时,调用方把效果改判成没生效。
 *
 * 受保护的页面不能外传,截图拿不到、模型出错或超时都算 [PointAudit.Unavailable]/[ScreenChange.Unavailable]:
 * 视觉核查是兜底,看不了的时候不拦路(和 [Locator] 同一取舍)。
 */
class TapVisionCheck(private val vision: VisionService) {

    /**
     * 核查落点 ([x]/[y] 为 0–999,和 tap 同一坐标系) 处是不是 [want] 指的东西;[want] 为空时只查是不是空白处。
     * [png] 是动作前的截图(带着落点标记发给视觉模型)。
     */
    suspend fun auditPoint(png: ByteArray, x: Int, y: Int, want: String?): PointAudit {
        val marked = try {
            vision.ops.drawFrame(png, intArrayOf((x - MARK).coerceAtLeast(0), (y - MARK).coerceAtLeast(0), (x + MARK).coerceAtMost(999), (y + MARK).coerceAtMost(999)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PointAudit.Unavailable("mark_failed")
        }
        val reply = try {
            withTimeoutOrNull(POINT_AUDIT_MS) { vision.ask(PROMPTS.first, "What is at the marked point?", listOf(marked)) }
                ?: return PointAudit.Unavailable("audit_timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PointAudit.Unavailable("vision_unavailable")
        }
        val reading = parseAt(reply) ?: return PointAudit.Unavailable("audit_unparsed")
        if (reading.at.isNotBlank()) {
            if (want.isNullOrBlank()) return PointAudit.Ok
            return if (related(reading.at, want)) PointAudit.Ok else PointAudit.OffTarget(reading.at)
        }
        // 无文字但是个图形控件(图标/图片按钮/输入框):是有效落点,放行;点得对不对由点后核对兜底。
        // 旧版把图标也当空白拒点,微信首页的放大镜搜索图标这类无文字目标因此点不下去。
        if (reading.icon.isNotBlank()) return PointAudit.Ok
        return PointAudit.Blank
    }

    /** 对比动作前后 ([before]/[after]) 两张截图,判界面变化。 */
    suspend fun screenChange(before: ByteArray, after: ByteArray): ScreenChange {
        val reply = try {
            withTimeoutOrNull(SCREEN_CHANGE_MS) { vision.ask(PROMPTS.second, "Before the tap, then after the tap. How did the screen change?", listOf(before, after)) }
                ?: return ScreenChange.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ScreenChange.Unavailable
        }
        return when (reply.trim().trim('"', '.').lowercase().substringBefore(' ').substringBefore('\n')) {
            "page" -> ScreenChange.Page
            "minor" -> ScreenChange.Minor
            "none" -> ScreenChange.None
            else -> ScreenChange.Unavailable
        }
    }

    /** 落点读数:[at] 是那一点的可见文字,[icon] 是无文字图形控件的简述;两者都空才是真空白。 */
    private class Reading(val at: String, val icon: String)

    /** 回复里 {"at": "文字", "icon": "图标简述"};null/空串算查到了(那一点没有该类内容);at 与 icon 都缺失、整个回复读不出时为 null。 */
    private fun parseAt(reply: String): Reading? {
        val obj = ModelJson.firstObject(reply) ?: return null
        fun str(vararg keys: String): String? {
            for (k in keys) when (val v = obj[k]) {
                is JsonNull -> return ""
                is JsonPrimitive -> return v.contentOrNull
                null -> {}
                else -> return null
            }
            return null
        }
        val at = str("at", "text")
        val icon = str("icon")
        if (at == null && icon == null) return null
        return Reading(at ?: "", icon ?: "")
    }

    /** 两段文字是不是指同一个东西:去空白、小写后互相包含(和 [Locator] 核对名字同一口径)。 */
    private fun related(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x in y || y in x)
    }

    private fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()

    private companion object {
        /** 落点标记的半宽(0–999)。 */
        const val MARK = 16

        /** 点前核查不能把循环拖住:视觉模型慢时尽快超时回退到树判,别把每步点击拖满。3 秒内拿不到结论就按树判放行,点后核对仍兜底。 */
        const val POINT_AUDIT_MS = 3_000L

        /** 点后核查等两张图对比;同样设短超时,慢模型超时即按树的原判,不阻塞快速执行。 */
        const val SCREEN_CHANGE_MS = 8_000L

        val PROMPTS: Pair<String, String> by lazy {
            Prompts.role("point_audit") to Prompts.role("screen_change")
        }
    }
}
