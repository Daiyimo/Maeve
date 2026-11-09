package com.daiyimo.maeve.core.mcp

import com.daiyimo.maeve.core.remote.HttpRequest
import com.daiyimo.maeve.core.remote.HttpResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.InetAddress
import java.util.Base64

/**
 * MCP 的 JSON-RPC 层,跑在 Streamable HTTP 上:只回单个 JSON,不建立 SSE 流。
 *
 * 两代协议都要处理。新一代把版本和客户端能力放在正文 `_meta` 里,同时在 HTTP 头上再抄一份,
 * 两处必须一致,而且没有握手、没有会话。老一代先调 `initialize` 协商版本。
 * 服务端不会反向发请求或通知,也不发放会话编号,所以老一代同样是逐请求独立的。
 *
 * 到达这里之前,令牌、来源、限流和路径都已经由 remote 入口筛过。
 */
class McpRpc(
    private val tools: McpTaskTools,
    private val serverVersion: String,
    /** 每个真正被读懂的请求都会回调;对方自报的客户端名为空时不传。 */
    private val onRequest: (remote: InetAddress, clientName: String?) -> Unit = { _, _ -> },
) {
    fun handle(req: HttpRequest): HttpResponse {
        if (req.method != "POST") {
            return transportError(405, "Only POST is supported on this endpoint", mapOf("Allow" to "POST"))
        }
        val contentType = req.header("content-type")?.substringBefore(';')?.trim()?.lowercase()
        if (contentType != "application/json") {
            return transportError(415, "Content-Type must be application/json")
        }
        val accept = req.header("accept")?.lowercase()
        if (accept != null && ACCEPTED_MEDIA.none { it in accept }) {
            return transportError(406, "Accept must include application/json")
        }

        val parsed = runCatching { json.parseToJsonElement(req.body.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return rpcError(400, null, PARSE_ERROR, "Parse error")
        if (parsed is JsonArray) return rpcError(400, null, INVALID_REQUEST, "Batches are not supported")
        val envelope = parsed as? JsonObject
            ?: return rpcError(400, null, INVALID_REQUEST, "Not a JSON-RPC 2.0 message")
        if (textOf(envelope["jsonrpc"]) != "2.0") {
            return rpcError(400, null, INVALID_REQUEST, "Not a JSON-RPC 2.0 message")
        }

        val method = methodName(envelope["method"])
        val id = envelope["id"]

        // 客户端回给我们的响应(本服务从不发请求,所以不会有)和通知:照单收下,不产生正文。
        if (method == null) {
            return if ("result" in envelope || "error" in envelope) HttpResponse.empty(202)
            else rpcError(400, null, INVALID_REQUEST, "Missing method")
        }
        if (id == null) {
            onRequest(req.remote, null)
            return HttpResponse.empty(202)
        }
        if (!isRpcId(id)) {
            return rpcError(400, null, INVALID_REQUEST, "id must be a string or a number")
        }
        val params = when (val raw = envelope["params"]) {
            null, JsonNull -> JsonObject(emptyMap())
            is JsonObject -> raw
            else -> return rpcError(400, id, INVALID_PARAMS, "params must be an object")
        }

        val meta = params["_meta"] as? JsonObject
        val bodyVersion = textOf(meta?.get(META_VERSION))
        if (bodyVersion != null && meta != null && method != "initialize") {
            return serveModern(req, id, method, params, meta, bodyVersion)
        }
        return serveLegacy(req, id, method, params)
    }

    /** 新一代请求:版本、方法、工具名在头上各镜像一份,和正文不一致就拒;版本不认得就列出认得的。 */
    private fun serveModern(
        req: HttpRequest,
        id: JsonElement,
        method: String,
        params: JsonObject,
        meta: JsonObject,
        version: String,
    ): HttpResponse {
        val headerVersion = req.header("mcp-protocol-version")
            ?: return rpcError(400, id, HEADER_MISMATCH, "Missing MCP-Protocol-Version header")
        if (headerVersion != version) {
            return rpcError(
                400, id, HEADER_MISMATCH,
                "Header mismatch: MCP-Protocol-Version '$headerVersion' does not match body '$version'",
            )
        }
        if (version !in MODERN_VERSIONS) return versionRejected(id, version)

        val headerMethod = req.header("mcp-method")
            ?: return rpcError(400, id, HEADER_MISMATCH, "Missing Mcp-Method header")
        if (headerMethod != method) {
            return rpcError(
                400, id, HEADER_MISMATCH,
                "Header mismatch: Mcp-Method '$headerMethod' does not match body '$method'",
            )
        }

        if (method == "tools/call") {
            val bodyName = textOf(params["name"])
            val headerName = req.header("mcp-name")?.let(::decodeHeader)
                ?: return rpcError(400, id, HEADER_MISMATCH, "Missing Mcp-Name header")
            if (headerName != bodyName) {
                return rpcError(400, id, HEADER_MISMATCH, "Header mismatch: Mcp-Name does not match body")
            }
        }

        if (meta[META_CAPABILITIES] !is JsonObject) {
            return rpcError(400, id, INVALID_PARAMS, "Missing $META_CAPABILITIES in _meta")
        }
        onRequest(req.remote, reportedClientName(meta[META_CLIENT]))

        val payload = when (method) {
            "server/discover" -> buildJsonObject {
                putJsonArray("supportedVersions") { (MODERN_VERSIONS + LEGACY_VERSIONS).forEach { add(it) } }
                put("capabilities", toolCapabilities())
                put("instructions", INSTRUCTIONS)
            }
            "ping" -> JsonObject(emptyMap())
            "tools/list" -> buildJsonObject { put("tools", tools.definitions()) }
            "tools/call" -> when (val outcome = dispatchToolCall(params)) {
                is ToolCallOutcome.Executed -> outcome.result
                is ToolCallOutcome.Rejected -> return rpcError(400, id, INVALID_PARAMS, outcome.message)
            }
            else -> return rpcError(404, id, METHOD_NOT_FOUND, "Method not found: $method")
        }

        val complete = buildJsonObject {
            put("resultType", "complete")
            payload.forEach { (key, value) -> put(key, value) }
            if (method in CACHEABLE) {
                put("ttlMs", LIST_TTL_MS)
                put("cacheScope", "private")
            }
            putJsonObject("_meta") { put(META_SERVER, serverDescription()) }
        }
        return success(id, complete)
    }

    /** 老一代请求:先握手挑一个双方都认的版本,后续调用照常;全程不发会话编号。 */
    private fun serveLegacy(req: HttpRequest, id: JsonElement, method: String, params: JsonObject): HttpResponse {
        val headerVersion = req.header("mcp-protocol-version")
        if (headerVersion != null && method != "initialize" && headerVersion !in LEGACY_VERSIONS) {
            // 头里声明的是新一代版本,正文却没带新一代必填的 _meta。
            return if (headerVersion in MODERN_VERSIONS) {
                rpcError(400, id, INVALID_PARAMS, "Missing _meta.$META_VERSION")
            } else {
                versionRejected(id, headerVersion)
            }
        }

        val payload = when (method) {
            "initialize" -> {
                onRequest(req.remote, reportedClientName(params["clientInfo"]))
                val asked = textOf(params["protocolVersion"])
                buildJsonObject {
                    put("protocolVersion", if (asked != null && asked in LEGACY_VERSIONS) asked else LEGACY_VERSIONS.first())
                    put("capabilities", toolCapabilities())
                    put("serverInfo", serverDescription())
                    put("instructions", INSTRUCTIONS)
                }
            }
            "ping" -> {
                onRequest(req.remote, null)
                JsonObject(emptyMap())
            }
            "tools/list" -> {
                onRequest(req.remote, null)
                buildJsonObject { put("tools", tools.definitions()) }
            }
            "tools/call" -> {
                onRequest(req.remote, null)
                when (val outcome = dispatchToolCall(params)) {
                    is ToolCallOutcome.Executed -> outcome.result
                    is ToolCallOutcome.Rejected -> return rpcError(200, id, INVALID_PARAMS, outcome.message)
                }
            }
            else -> return rpcError(200, id, METHOD_NOT_FOUND, "Method not found: $method")
        }
        return success(id, payload)
    }

    private sealed interface ToolCallOutcome {
        data class Executed(val result: JsonObject) : ToolCallOutcome

        data class Rejected(val message: String) : ToolCallOutcome
    }

    /**
     * 工具名不认识、arguments 不是对象:这是协议层错误。参数内容不合法、任务不存在:
     * 作为工具结果返回(isError),让对面的模型自己看着改。
     */
    private fun dispatchToolCall(params: JsonObject): ToolCallOutcome {
        val name = methodName(params["name"]) ?: return ToolCallOutcome.Rejected("params.name must be a string")
        if (!tools.has(name)) return ToolCallOutcome.Rejected("Unknown tool: $name")
        val arguments = when (val raw = params["arguments"]) {
            null, JsonNull -> JsonObject(emptyMap())
            is JsonObject -> raw
            else -> return ToolCallOutcome.Rejected("params.arguments must be an object")
        }
        val output = try {
            runBlocking { tools.call(name, arguments) }
        } catch (e: McpTaskTools.ToolError) {
            return ToolCallOutcome.Executed(toolResult(e.message ?: "error", null, isError = true))
        } catch (e: Exception) {
            return ToolCallOutcome.Executed(
                toolResult("Maeve could not do that: ${e.message ?: e::class.simpleName}", null, isError = true),
            )
        }
        return ToolCallOutcome.Executed(toolResult(output.toString(), output, isError = false))
    }

    private fun toolResult(text: String, structured: JsonObject?, isError: Boolean): JsonObject = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", text) }) }
        structured?.let { put("structuredContent", it) }
        put("isError", isError)
    }

    private fun toolCapabilities(): JsonObject =
        buildJsonObject { putJsonObject("tools") { put("listChanged", false) } }

    private fun serverDescription(): JsonObject = buildJsonObject {
        put("name", "maeve")
        put("title", "Maeve")
        put("version", serverVersion)
    }

    private fun reportedClientName(info: JsonElement?): String? = textOf((info as? JsonObject)?.get("name"))?.take(64)

    private fun versionRejected(id: JsonElement?, requested: String): HttpResponse = HttpResponse.json(
        400,
        buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("error") {
                put("code", UNSUPPORTED_VERSION)
                put("message", "Unsupported protocol version")
                putJsonObject("data") {
                    putJsonArray("supported") { (MODERN_VERSIONS + LEGACY_VERSIONS).forEach { add(it) } }
                    put("requested", requested)
                }
            }
        }.toString(),
    )

    private fun success(id: JsonElement, result: JsonObject): HttpResponse = HttpResponse.json(
        200,
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }.toString(),
    )

    private fun rpcError(status: Int, id: JsonElement?, code: Int, message: String): HttpResponse = HttpResponse.json(
        status,
        buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }.toString(),
    )

    companion object {
        const val PATH = "/mcp"
        const val MODERN = "2026-07-28"
        val MODERN_VERSIONS = listOf(MODERN)

        /** 新的排前面;握手时如果对方要的不在列表里,就把第一个给它。 */
        val LEGACY_VERSIONS = listOf("2025-11-25", "2025-06-18", "2025-03-26")

        /**
         * 新一代要求这些方法的完整结果带缓存提示(`ttlMs`、`cacheScope`),
         * 少了客户端会拒收整个结果。工具列表取决于这台手机有没有接设备能力,
         * 不同调用方看到的可能不同,所以只允许同一令牌复用(private),新鲜期 5 分钟。
         */
        val CACHEABLE = setOf("server/discover", "tools/list")
        const val LIST_TTL_MS = 300_000L

        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val HEADER_MISMATCH = -32020
        const val UNSUPPORTED_VERSION = -32022

        const val META_VERSION = "io.modelcontextprotocol/protocolVersion"
        const val META_CLIENT = "io.modelcontextprotocol/clientInfo"
        const val META_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities"
        const val META_SERVER = "io.modelcontextprotocol/serverInfo"

        const val INSTRUCTIONS = "Maeve is an AI agent running on the user's Android phone. Hand it whole tasks with maeve_run_task " +
            "(it operates the phone's apps and browser itself), then follow them with maeve_wait_task. Answer its questions with maeve_answer. " +
            "Payments, confirmations and steps handed to the user can only be done by the user on the phone."

        private val ACCEPTED_MEDIA = listOf("application/json", "*/*", "application/*", "text/event-stream")
        private val json = Json { ignoreUnknownKeys = true }

        /** 请求头里放不下的值会写成 `=?base64?…?=`,比较前先还原。 */
        fun decodeHeader(v: String): String? =
            if (v.startsWith("=?base64?") && v.endsWith("?=") && v.length >= 11) {
                runCatching { String(Base64.getDecoder().decode(v.substring(9, v.length - 2)), Charsets.UTF_8) }.getOrNull()
            } else {
                v
            }

        /** 在入口层就被挡下的请求:HTTP 状态码说明原因,正文是不带 id 的 JSON-RPC 错误。 */
        fun transportError(status: Int, message: String, headers: Map<String, String> = emptyMap()): HttpResponse =
            HttpResponse.json(
                status,
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    putJsonObject("error") {
                        put("code", INVALID_REQUEST)
                        put("message", message)
                    }
                }.toString(),
                headers,
            )

        private fun textOf(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

        private fun methodName(element: JsonElement?): String? =
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun isRpcId(id: JsonElement): Boolean {
            if (id !is JsonPrimitive) return false
            if (id is JsonNull) return false
            if (id.isString) return true
            return id.content.toLongOrNull() != null || id.content.toDoubleOrNull() != null
        }
    }
}
