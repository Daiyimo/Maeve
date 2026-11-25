package com.daiyimo.maeve.core.cap.phone

import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.run.SKIPPED_FOR_NEW_MESSAGE
import com.daiyimo.maeve.core.run.waitOrSteer
import com.daiyimo.maeve.core.tool.failCode
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Entry
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.SubActions
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import com.daiyimo.maeve.core.uistate.screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.daiyimo.maeve.core.uistate.session

/** 根据每一步的当前画面重新定位目标,然后把动作交回统一执行入口。 */
internal object PhoneRun : PhoneTool {
    override val verb = Verb.PhoneRun
    override val risk = Risk.Write
    override fun entry(ctx: ToolContext) = Entry.Opens.takeIf { ctx.phone.deepLinks }
    override val composite = true
    override val label = "连续操作手机"
    private val operations = setOf("launch", "tap", "type", "clear", "scroll", "swipe", "key", "back", "wait", "assert", "observe")
    override val def = definition(deepLinks = false)
    private val withLinks = definition(deepLinks = true)
    override fun def(ctx: ToolContext) = if (ctx.phone.deepLinks) withLinks else def

    private fun definition(deepLinks: Boolean): ToolDef {
        val ops = if (deepLinks) operations + "open_link" else operations
        val fields = listOf("targetText", "resourceId", "role", "text", "packageName", "direction", "whenText", "key")
        val tap = "tap(targetText/resourceId/role or x+y)"
        val description = Prompts.tool("phone_run", "navigation_commands" to
            if (deepLinks) "open_link(action or uri, packageName), $tap" else tap)
        return ToolDef("phone_run", description, mapOf(
            "commands" to buildJsonObject {
                put("type", "array"); put("minItems", 1); put("maxItems", 30)
                put("description", "Ordered commands. Each target is found again on the current screen before acting.")
                put("items", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("op", buildJsonObject { put("type", "string"); put("enum", JsonArray(ops.map(::JsonPrimitive))) })
                        for (key in if (deepLinks) fields + listOf("action", "uri") else fields)
                            put(key, buildJsonObject { put("type", "string") })
                        for (key in listOf("x", "y", "amount", "timeoutMs", "startX", "startY", "endX", "endY", "durationMs"))
                            put(key, buildJsonObject { put("type", "integer") })
                        put("present", buildJsonObject { put("type", "boolean") })
                        put("pressEnter", buildJsonObject { put("type", "boolean") })
                        put("cold", buildJsonObject { put("type", "boolean") })
                    })
                    put("required", JsonArray(listOf(JsonPrimitive("op"))))
                    put("additionalProperties", false)
                })
            },
        ), listOf("commands"))
    }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = ctx.session.textOnly {
        runCommands(a, ctx)
    }

    private suspend fun runCommands(a: Args, ctx: ToolContext): ToolExecution {
        val commands = a.o["commands"] as? JsonArray ?: throw ArgError("commands", "must be an array")
        if (commands.size !in 1..30) throw ArgError("commands", "must have 1-30 items")
        val sub = ctx.sub ?: return failed("actions_unavailable")
        val steps = mutableListOf<JsonObject>()
        val callId = ctx.callId
        var expectedPackage = ctx.screen.currentPackage()
        var stop: String? = null
        var finalScreen: JsonObject? = null
        for ((index, raw) in commands.withIndex()) {
            val command = raw as? JsonObject ?: throw ArgError("commands[$index]", "must be an object")
            val op = command.string("op") ?: throw ArgError("commands[$index].op", "required")
            if (op !in operations && !(op == "open_link" && ctx.phone.deepLinks))
                throw ArgError("commands[$index].op", "unknown operation")
            val currentPackage = ctx.screen.currentPackage()
            val visual = ctx.session.visualFirst()
            if (expectedPackage != null && currentPackage != expectedPackage && (currentPackage != null || visual)) {
                stop = "app_switched"
                steps += row(index, op, false, stop, executed = false)
                break
            }
            val whenText = command.string("whenText")
            if (whenText != null && hasText(ctx, whenText) != true) {
                stop = "condition_unmet"
                steps += row(index, op, false, stop, executed = false)
                break
            }
            val result = when (op) {
                "launch" -> {
                    val pkg = command.string("packageName") ?: throw ArgError("commands[$index].packageName", "required")
                    run(sub, callId, index, "phone_launch_app", buildJsonObject { put("package", pkg); if (command.boolean("cold") == true) put("cold", true) })
                }
                "open_link" -> run(sub, callId, index, "phone_open_link", buildJsonObject {
                    command.string("action")?.let { put("action", it) }
                    command.string("uri")?.let { put("uri", it) }
                    command.string("packageName")?.let { put("package", it) }
                })
                "tap", "type", "clear" -> {
                    val description = command.string("targetText")
                    val described = description != null && op != "clear" && command.string("resourceId") == null
                    val target = when {
                        described -> Target()
                        visual && (description != null || command.string("resourceId") != null || command.string("role") != null) ->
                            Target(error = "target_not_supported_on_visual_screen")
                        else -> select(sub, callId, index, command, op != "tap")
                    }
                    if (target.error != null) {
                        stop = target.error
                        steps += row(index, op, false, stop, executed = false)
                        break
                    }
                    val args = if (op == "tap") buildJsonObject {
                        target.ref?.let { put("ref", it); put("snapshotId", target.snapshotId!!) }
                        if (described) {
                            put("element", description)
                            command.string("role")?.let { put("role", it) }
                        } else if (target.ref == null) {
                            put("x", command.integer("x")!!); put("y", command.integer("y")!!)
                        }
                    } else buildJsonObject {
                        if (op == "type") {
                            put("text", command.string("text") ?: throw ArgError("commands[$index].text", "required"))
                            if (command.boolean("pressEnter") == true) put("pressEnter", true)
                        }
                        if (described) {
                            put("element", description)
                            command.string("role")?.let { put("role", it) }
                        }
                        target.ref?.let { put("ref", it); put("snapshotId", target.snapshotId!!) }
                    }
                    run(sub, callId, index, when (op) { "tap" -> "phone_tap"; "type" -> "phone_type_text"; else -> "phone_clear_text" }, args)
                }
                "scroll" -> {
                    val dir = command.string("direction") ?: throw ArgError("commands[$index].direction", "required")
                    if (dir !in setOf("up", "down", "left", "right")) throw ArgError("commands[$index].direction", "invalid")
                    val amount = command.integer("amount") ?: 3
                    if (amount !in 1..20) throw ArgError("commands[$index].amount", "must be 1-20")
                    run(sub, callId, index, "phone_scroll", buildJsonObject { put("direction", dir); put("amount", amount) })
                }
                "back" -> run(sub, callId, index, "phone_back", JsonObject(emptyMap()))
                "key" -> {
                    val key = command.string("key") ?: throw ArgError("commands[$index].key", "required")
                    run(sub, callId, index, "phone_press_key", buildJsonObject { put("key", key) })
                }
                "swipe" -> {
                    val coordinates = listOf("startX", "startY", "endX", "endY")
                    if (coordinates.any { command.integer(it) !in 0..999 }) throw ArgError("commands[$index]", "swipe coordinates must be 0-999")
                    run(sub, callId, index, "phone_swipe", buildJsonObject {
                        coordinates.forEach { put(it, command.integer(it)!!) }
                        command.integer("durationMs")?.let { put("durationMs", it) }
                    })
                }
                "wait", "assert" -> {
                    val text = command.string("targetText") ?: throw ArgError("commands[$index].targetText", "required")
                    val present = command.boolean("present") ?: true
                    val timeout = if (op == "assert") 0 else command.integer("timeoutMs") ?: 5_000
                    if (timeout !in 0..10_000) throw ArgError("commands[$index].timeoutMs", "must be 0-10000")
                    // 等的时候用户插了话:不再等,这一条和后面的都不做,先读那句话
                    val found = awaitText(ctx, text, present, timeout)
                    if (found == null) failCode(SKIPPED_FOR_NEW_MESSAGE)
                    else ToolExecution(buildJsonObject { put("ok", found); if (!found) put("error", "condition_unmet") })
                }
                else -> run(sub, callId, index, if (visual) "phone_observe" else "phone_list_elements", JsonObject(emptyMap()))
            }
            val content = result.content
            val accepted = content["ok"] == JsonPrimitive(true)
            val inputUnverified = accepted && visual && op == "type" && content["verification"] != JsonPrimitive("match")
            val ok = accepted && !inputUnverified
            val reportedError = (content["error"] as? JsonPrimitive)?.contentOrNull
            val error = when {
                inputUnverified -> "input_unverified"
                reportedError == "element_ambiguous" -> "ambiguous_target"
                else -> reportedError
            }
            val executed = content["executed"] != JsonPrimitive(false) && reportedError !in
                setOf("element_ambiguous", "element_not_found", "target_unresolved")
            steps += row(index, op, ok, error, executed, (content["message"] as? JsonPrimitive)?.contentOrNull)
            (content["screen"] as? JsonObject)?.let { finalScreen = it }
            if (op == "observe") finalScreen = content
            if (!ok) { stop = error ?: "failed"; break }
            if (op == "launch") expectedPackage = command.string("packageName")
            else if (op == "open_link") expectedPackage = (content["currentApp"] as? JsonPrimitive)?.contentOrNull
            else if (expectedPackage == null) expectedPackage = currentPackage
            if (content["app_switched"] != null) { stop = "app_switched"; break }
            if (content["opened"] != null) { stop = "opened_elsewhere"; break }
            if ((content["screen"] as? JsonObject)?.get("imageReason") != null) { stop = "screen_needs_review"; break }
        }
        if (stop !in setOf(SKIPPED_FOR_NEW_MESSAGE, "app_switched", "opened_elsewhere")) {
            val readTool = if (ctx.session.visualFirst()) "phone_observe" else "phone_list_elements"
            val last = run(sub, callId, commands.size, readTool, JsonObject(emptyMap()))
            if (last.content["ok"] == JsonPrimitive(true)) finalScreen = last.content
            else if (stop == null) stop = "final_read_failed"
        }
        return ToolExecution(buildJsonObject {
            put("ok", stop == null)
            stop?.let { put("error", "phone_run_stopped"); put("reason", it); put("recoverable", true) }
            put("completed", steps.count { it["ok"] == JsonPrimitive(true) })
            put("total", commands.size)
            put("steps", JsonArray(steps))
            finalScreen?.let { put("screen", it) }
        })
    }

    private data class Target(val ref: String? = null, val snapshotId: String? = null, val error: String? = null)

    private suspend fun select(sub: SubActions, callId: String, index: Int, command: JsonObject, editable: Boolean): Target {
        val text = command.string("targetText")
        val id = command.string("resourceId")
        val role = command.string("role")
        if (text == null && id == null && role == null) {
            if (editable) return Target()
            val x = command.integer("x"); val y = command.integer("y")
            return if (x in 0..999 && y in 0..999) Target() else Target(error = "target_required")
        }
        val query = run(sub, callId, index, "phone_query", buildJsonObject {
            text?.let { put("text", it) }; id?.let { put("resourceId", it) }; role?.let { put("role", it) }; put("limit", 100)
        })
        if (query.content["ok"] != JsonPrimitive(true)) return Target(error = "query_failed")
        if (query.content["truncated"] == JsonPrimitive(true)) return Target(error = "ambiguous_target")
        val sid = (query.content["snapshotId"] as? JsonPrimitive)?.contentOrNull ?: return Target(error = "no_snapshot")
        val refKey = if (editable) "editRef" else "actionRef"
        val all = (query.content["matches"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { (it[refKey] ?: it["ref"]) is JsonPrimitive && it["enabled"] != JsonPrimitive(false) }
        val exact = text?.let { t -> all.filter { (it["name"] as? JsonPrimitive)?.contentOrNull?.equals(t, true) == true } }.orEmpty()
        val candidates = (exact.takeIf { it.isNotEmpty() } ?: all).let { items ->
            val preferred = items.filter { it[if (editable) "editable" else "clickable"] == JsonPrimitive(true) }
            preferred.takeIf { it.isNotEmpty() } ?: items
        }
        if (candidates.size != 1) return Target(error = if (candidates.isEmpty()) "target_not_found" else "ambiguous_target")
        return Target(((candidates.single()[refKey] ?: candidates.single()["ref"]) as JsonPrimitive).content, sid)
    }

    private suspend fun run(sub: SubActions, callId: String, index: Int, name: String, args: JsonObject): ToolExecution =
        sub.run(Part.ToolCall("$callId.${index + 1}.$name", name, args.toString()))

    /** 等到文字出现(或消失);用户插话撤掉了这次等待时为 null。 */
    private suspend fun awaitText(ctx: ToolContext, text: String, present: Boolean, timeoutMs: Int): Boolean? {
        return waitOrSteer {
            if (ctx.session.visualFirst()) {
                // OCR can establish presence. An unreadable or missing label does not establish absence.
                present && if (timeoutMs == 0) hasText(ctx, text) == true else withTimeoutOrNull(timeoutMs.toLong()) {
                    while (hasText(ctx, text) != true) delay(250)
                    true
                } == true
            } else if (hasText(ctx, text) == present) true
            else timeoutMs > 0 && ctx.screen.waitFor(if (present) "appear" else "disappear", text, timeoutMs.toLong())
        }
    }

    private suspend fun hasText(ctx: ToolContext, text: String): Boolean? {
        if (ctx.session.visualFirst()) {
            return ctx.session.currentVisualText()?.any { it.text.contains(text, ignoreCase = true) }?.takeIf { it }
        }
        val snapshot = ctx.session.peek() ?: return null
        return PhoneAutomationEngine.find(snapshot, PhoneAutomationEngine.Query(text = text), 1)["total"] != JsonPrimitive(0)
    }

    private fun row(index: Int, op: String, ok: Boolean, error: String?, executed: Boolean, message: String? = null) = buildJsonObject {
        put("n", index + 1); put("op", op); put("ok", ok)
        if (!executed) put("executed", false)
        error?.let { put("error", it) }
        if (!ok) message?.take(320)?.let { put("message", it) }
    }

    private fun failed(error: String) = ToolExecution(buildJsonObject { put("ok", false); put("error", error) })

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.integer(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.boolean(key: String): Boolean? = when (this[key]) { JsonPrimitive(true) -> true; JsonPrimitive(false) -> false; else -> null }
}
