package com.daiyimo.maeve.core.mcp

import com.daiyimo.maeve.core.device.DeviceAccess
import com.daiyimo.maeve.core.device.toJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 手机主屏的三个工具:读锁屏状态、唤醒、解锁。
 * 它们故意不接受任何参数——凭据只保存在手机设置里,电脑端既传不进来,也不会从结果里读回去。
 * 每个调用仍然要过 MCP 入口那层认证。
 */
internal class McpDeviceTools(private val device: DeviceAccess) {

    /** 三件工具都没有参数,共用同一张空对象 schema。 */
    private val noArgumentsSchema = emptyObjectSchema()

    val tools = listOf(
        McpTaskTools.Tool(
            STATE,
            "Read phone screen/lock state and whether local remote-unlock permission is enabled. No credentials are returned.",
            noArgumentsSchema,
            true,
        ),
        McpTaskTools.Tool(
            WAKE,
            "Wake the main phone screen. Requires the phone's remote wake/unlock setting. Does not unlock it.",
            noArgumentsSchema,
            false,
        ),
        McpTaskTools.Tool(
            UNLOCK,
            "Wake and unlock the phone using only permission and credentials configured locally on the phone. " +
                "Call before maeve_run_task, or when a task is paused because the phone is locked. A failed credential attempt blocks further attempts until reconfigured on the phone. " +
                "Cannot approve payments or bypass authentication.",
            noArgumentsSchema,
            false,
        ),
    )

    suspend fun call(name: String, args: JsonObject): JsonObject {
        rejectArguments(args)
        val result = when (name) {
            STATE -> device.state()
            WAKE -> device.wake()
            UNLOCK -> device.unlock()
            else -> throw McpTaskTools.ToolError("Unknown tool: $name")
        }
        return result.toJson()
    }

    /** 凭据只能在手机上配;带任何参数都直接拒绝,并且先于任何设备操作。 */
    private fun rejectArguments(args: JsonObject) {
        if (args.isNotEmpty()) {
            throw McpTaskTools.ToolError("Device tools accept no arguments; configure credentials on the phone.")
        }
    }

    companion object {
        const val STATE = "maeve_device_state"
        const val WAKE = "maeve_wake_device"
        const val UNLOCK = "maeve_unlock_device"

        private fun emptyObjectSchema() = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(emptyMap()))
            put("required", buildJsonArray {})
            put("additionalProperties", false)
        }
    }
}
