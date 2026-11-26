package com.daiyimo.maeve.core.cap.phone

import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.run.SKIPPED_FOR_NEW_MESSAGE
import com.daiyimo.maeve.core.run.waitOrSteer
import com.daiyimo.maeve.core.tool.LinkPolicy
import com.daiyimo.maeve.core.tool.SubActions
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.failCode
import com.daiyimo.maeve.core.uistate.DescribedTarget
import com.daiyimo.maeve.core.uistate.Located
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.screen
import com.daiyimo.maeve.core.uistate.session
import com.daiyimo.maeve.core.vision.LocateResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** 固定的定时任务走现有应用访问、动作审批和证据记录。只切换定位方式，不重放签到。 */
internal class AmooCheckIn(private val ctx: ToolContext, private val sub: SubActions, private val callId: String) {
    private var sequence = 0

    suspend fun execute(): ToolExecution {
        if (ctx.phone.amooCheckInAttempted) return fail("check_in_already_attempted", "签到已尝试，请读回状态，勿重复点击。")
        openGrowthPlan()?.let { return it }
        val sight = locate(CHECK_IN, "The enabled daily check-in button labelled exactly 签到. Exclude 已签到 and reward descriptions.")
        if (sight.interrupted()) return failCode(SKIPPED_FOR_NEW_MESSAGE)
        val before = readGrowthPlan()
        if (before.signed()) return result("already_signed", "ui_tree")
        val targets = before?.elements.orEmpty().filter { it.name.trim() == CHECK_IN }
        val treeTarget = targets.singleOrNull()
        val visual = sight.exact(CHECK_IN, allowUnverified = treeTarget == null)
        if (visual == null && treeTarget == null) {
            val signedSight = locate(SIGNED, "The completed daily check-in status labelled exactly 已签到.")
            if (signedSight.interrupted()) return failCode(SKIPPED_FOR_NEW_MESSAGE)
            if (signedSight.exact(SIGNED, allowUnverified = before == null || targets.isEmpty()) != null) {
                return result("already_signed", "vision", readback = "vision")
            }
            return unreadable()
        }
        val byVision = visual != null
        if (ctx.screen.currentPackage() != GROWTH_PACKAGE) return unreadable()

        val target = if (visual != null) {
            ctx.session.locatedInfo[visual.ref] = Located(CHECK_IN, CHECK_IN, visual.seen, visual.unverified)
            visual.ref to visual.snapshotId
        } else treeTarget!!.ref to before!!.snapshotId
        ctx.phone.amooCheckInAttempted = true
        val tapped = call("phone_tap", buildJsonObject { put("ref", target.first); put("snapshotId", target.second) })
        if (!tapped.ok()) return tapped
        val method = if (byVision) "vision" else "ui_tree"
        if (readGrowthPlan().signed()) return result("signed", method)
        val appeared = waitOrSteer { ctx.screen.waitFor("appear", SIGNED, 4_000) }
            ?: return failCode(SKIPPED_FOR_NEW_MESSAGE)
        if (appeared && readGrowthPlan().signed()) return result("signed", method)
        if (byVision) {
            val after = locate(SIGNED, "The completed daily check-in status labelled exactly 已签到.")
            if (after.interrupted()) return failCode(SKIPPED_FOR_NEW_MESSAGE)
            if (after.exact(SIGNED, allowUnverified = true) != null) return result("signed", "vision", readback = "vision")
        }
        return fail("check_in_unverified", "已尝试签到，但未读到「已签到」。请核对当前状态，勿重复点击。")
    }

    private suspend fun openGrowthPlan(): ToolExecution? {
        if (ctx.screen.currentPackage() == GROWTH_PACKAGE) return null
        if (ctx.screen.currentPackage() != LinkPolicy.STEPX_ACCOUNT_PACKAGE) {
            val link = call("phone_open_link", buildJsonObject {
                put("action", LinkPolicy.STEPX_ACCOUNT)
                put("package", LinkPolicy.STEPX_ACCOUNT_PACKAGE)
            })
            if (!link.ok()) {
                if ((link.content["error"] as? JsonPrimitive)?.contentOrNull !in
                    setOf("link_unresolved", "launch_failed", "unknown_tool", "unsupported")) return link
                val settings = call("phone_launch_app", buildJsonObject { put("package", "com.android.settings") })
                if (!settings.ok()) return settings
                val account = tapLabel("账号管理和我的设备")
                if (!account.ok()) return account
            }
        }
        if (ctx.screen.currentPackage() != LinkPolicy.STEPX_ACCOUNT_PACKAGE) return unreadable()
        val growth = tapLabel("Amoo 成长计划")
        if (!growth.ok()) return growth
        return if (ctx.screen.currentPackage() == GROWTH_PACKAGE) null else unreadable()
    }

    private suspend fun locate(label: String, description: String, timeoutMs: Long = 8_000): LocateResult? {
        val locator = ctx.session.locator ?: return null
        return waitOrSteer {
            withTimeoutOrNull(timeoutMs) { locator.locate(description, label, preferVision = true) }
                ?: LocateResult.NotFound("vision_timeout", "视觉定位超时")
        } ?: LocateResult.NotFound(SKIPPED_FOR_NEW_MESSAGE, "任务收到新消息")
    }

    private fun LocateResult?.interrupted() = (this as? LocateResult.NotFound)?.reason == SKIPPED_FOR_NEW_MESSAGE

    private fun LocateResult?.exact(label: String, allowUnverified: Boolean = false) = (this as? LocateResult.Found)
        ?.takeIf { it.seen?.trim() == label && (!it.unverified || allowUnverified) }

    private suspend fun readGrowthPlan(): ScreenSnapshot? {
        if (ctx.screen.currentPackage() != GROWTH_PACKAGE || !call("phone_list_elements").ok()) return null
        return ctx.session.lastSnapshot?.takeIf { it.packageName == GROWTH_PACKAGE && !it.protected }
    }

    private suspend fun tapLabel(label: String): ToolExecution {
        val sight = locate(label, "The clickable entry labelled exactly $label.", timeoutMs = 6_000)
        if (sight.interrupted()) return failCode(SKIPPED_FOR_NEW_MESSAGE)
        val point = sight.exact(label, allowUnverified = true)
        if (point != null) {
            return call("phone_tap", buildJsonObject { put("x", point.x); put("y", point.y) })
        }
        return call("phone_tap", buildJsonObject { put("element", label) })
    }

    private suspend fun call(tool: String, args: JsonObject = JsonObject(emptyMap())) =
        sub.run(Part.ToolCall("$callId.${++sequence}.$tool", tool, args.toString()))

    private fun ScreenSnapshot?.signed() = this?.elements?.any { it.name.trim() == SIGNED } == true

    private fun ToolExecution.ok() = content["ok"] == JsonPrimitive(true)

    private fun unreadable() = fail("check_in_unavailable", "无法确认当前是 Amoo 成长计划签到区，或签到按钮不唯一。")

    private fun result(status: String, method: String, readback: String = "ui_tree") = ToolExecution(buildJsonObject {
        put("ok", true)
        put("status", status)
        put("method", method)
        put("readback", readback)
        put("evidence", SIGNED)
    })

    private companion object {
        const val GROWTH_PACKAGE = LinkPolicy.AMOO_GROWTH_PACKAGE
        const val CHECK_IN = "签到"
        const val SIGNED = "已签到"
    }
}
