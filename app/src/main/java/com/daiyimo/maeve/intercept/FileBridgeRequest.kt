package com.daiyimo.maeve.intercept

import kotlinx.serialization.json.*

internal data class AmooRequest(val goal: String, val requestId: String? = null)

/** 平铺协议：JSON 库负责转义/语法，额外拒绝重复字段、嵌套值和超限内容。 */
internal fun parseAmooRequest(body: String, now: Long = System.currentTimeMillis(), allowExpired: Boolean = false): AmooRequest? = runCatching {
    require(body.toByteArray(Charsets.UTF_8).size <= 16 * 1024)
    val root = Json.parseToJsonElement(body).jsonObject
    require(root.keys.all { it in setOf("id", "goal", "schemaVersion", "requestId", "createdAt", "expiresAt") })
    require(root.values.all { it is JsonPrimitive })
    // 提取全部字符串 token。每个后接冒号的 token 都是键；解码后检查重复（含 Unicode 转义）。
    val names = HashSet<String>()
    var i = 0
    while (i < body.length) {
        if (body[i] != '"') { i++; continue }
        val start = i++
        while (i < body.length) {
            if (body[i] == '\\') { i += 2; continue }
            if (body[i++] == '"') break
        }
        val end = i
        while (i < body.length && body[i].isWhitespace()) i++
        if (i < body.length && body[i] == ':') require(names.add(Json.decodeFromString<String>(body.substring(start, end))))
    }
    val goal = root.getValue("goal").jsonPrimitive.let { require(it.isString); it.content.trim() }
    require(goal.isNotEmpty() && goal.length <= 4000)
    val version = root["schemaVersion"]?.jsonPrimitive?.let { require(!it.isString); requireNotNull(it.intOrNull) } ?: 0
    require(version in 0..1)
    if (version == 1) {
        require(!root.getValue("createdAt").jsonPrimitive.isString && !root.getValue("expiresAt").jsonPrimitive.isString)
        val created = root.getValue("createdAt").jsonPrimitive.long
        val expires = root.getValue("expiresAt").jsonPrimitive.long
        require(expires > created && expires - created <= 86_400_000)
        if (!allowExpired) require(created <= now + 120_000 && created > now - 86_400_000 && expires > now)
    }
    val requestId = root["requestId"]?.jsonPrimitive?.let { require(it.isString); it.content }
    AmooRequest(goal, requestId)
}.getOrNull()
