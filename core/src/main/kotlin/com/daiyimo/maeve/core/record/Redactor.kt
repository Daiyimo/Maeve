package com.daiyimo.maeve.core.record

import com.daiyimo.maeve.core.tool.Verb
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 运行记录的脱敏入口。凡是密码框里的输入、形似 API Key 的串、名字像凭据的字段,
 * 都只留下 [MASK]。判断不了目标性质时按敏感处理——记录少一点比多记一段密码好。
 */
object Redactor {
    const val MASK = "***"

    // 各种服务商的密钥形态。长度下限用来避开普通单词,不做语义判断。
    private val SK_KEY = Regex("""\bsk-[A-Za-z0-9_\-]{16,}""")
    private val BEARER = Regex("""\bBearer\s+[A-Za-z0-9._~+/=\-]{8,}""")
    private val GOOGLE_KEY = Regex("""\bAIza[0-9A-Za-z_\-]{30,}""")

    /** Google 新式认证 Key:`AQ.` 前缀,后面是一段 base64url 字符。 */
    private val GOOGLE_AUTH_KEY = Regex("""\bAQ\.[0-9A-Za-z_\-]{20,}""")
    private val GITHUB_TOKEN = Regex("""\bgh[pousr]_[A-Za-z0-9]{20,}""")

    /** [secretField]:端口报告这次输入的目标是密码框。 */
    /** [verb]:调用的工具做的动作(不认识的工具为 null)。 */
    fun args(verb: Verb?, argsJson: String, secretField: Boolean): String = when {
        verb == Verb.MemorySave -> maskFields(argsJson, MEMORY_ARGS)
        verb == Verb.Script -> maskScript(argsJson)
        verb == Verb.PhoneCommand -> maskFields(argsJson, setOf("command"))
        verb == Verb.PhoneRun -> maskPhoneRun(argsJson)
        verb != Verb.TypeText || !secretField -> text(argsJson)
        else -> maskTypedText(argsJson)
    }

    /** 是否密码框由端口判断;确定是时把 `text` 字段整段换掉。 */
    private fun maskTypedText(argsJson: String): String {
        val root = parseObject(argsJson) ?: return MASK
        val masked = if ("text" in root) {
            JsonObject(root + ("text" to JsonPrimitive(MASK)))
        } else {
            root
        }
        return text(masked.toString())
    }

    /**
     * 条件式手机命令里可能夹着输入动作,而输入目标未必是普通输入框。
     * 只要 `op` 是 type 且带 `text`,就把输入内容遮住。
     */
    private fun maskPhoneRun(argsJson: String): String {
        val root = parseObject(argsJson) ?: return MASK
        val commands = root["commands"] as? JsonArray ?: return text(argsJson)
        val rewritten = JsonArray(commands.map { element ->
            val command = element as? JsonObject ?: return@map element
            val typesText = command["op"] == JsonPrimitive("type") && "text" in command
            if (typesText) JsonObject(command + ("text" to JsonPrimitive(MASK))) else command
        })
        return text(JsonObject(root + ("commands" to rewritten)).toString())
    }

    /**
     * `web_script` 的源码。输入内容可能直接交给 type、先赋给变量、拼接后再用,
     * 没法判断哪一段会进密码框,所以把每个字符串字面量都遮住,只保留引号与
     * 模板字符串里的 `${…}` 占位。
     */
    private fun maskScript(argsJson: String): String {
        val root = parseObject(argsJson) ?: return MASK
        val code = (root["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return text(argsJson)
        val masked = maskJsLiterals(code)
        return text(JsonObject(root + ("code" to JsonPrimitive(masked))).toString())
    }

    private fun maskJsLiterals(code: String): String = STRING_LITERAL.replace(code) { match ->
        val literal = match.value
        val quote = literal.first()
        val body = literal.substring(1, literal.length - 1)
        when {
            body.isEmpty() -> literal
            quote == '`' -> "`" + TEMPLATE_HOLE.findAll(body).joinToString("") { hole ->
                MASK + STRING_LITERAL.replace(hole.value) { inner -> "'$MASK'" }
            } + MASK + "`"
            else -> "$quote$MASK$quote"
        }
    }

    /** 工具结果进记录前:记忆正文、交回用户时的备注、命令输出都不留原文。 */
    fun result(verb: Verb?, content: JsonObject): JsonObject = when (verb) {
        Verb.MemorySearch -> maskMemories(content)
        Verb.HandToUser -> maskField(content, "userNote")
        Verb.PhoneCommand -> maskField(content, "output")
        else -> content
    }

    private fun maskMemories(content: JsonObject): JsonObject {
        val memories = content["memories"] as? JsonArray ?: return content
        val rewritten = JsonArray(memories.map { item ->
            when (item) {
                is JsonObject ->
                    if ("content" in item) JsonObject(item + ("content" to JsonPrimitive(MASK))) else item
                else -> JsonPrimitive(MASK)
            }
        })
        return JsonObject(content + ("memories" to rewritten))
    }

    private fun maskField(content: JsonObject, name: String): JsonObject =
        if (name in content) JsonObject(content + (name to JsonPrimitive(MASK))) else content

    private fun maskFields(argsJson: String, fields: Set<String>): String {
        val root = parseObject(argsJson) ?: return MASK
        val masked = root.mapValues { (name, value) ->
            if (name in fields) JsonPrimitive(MASK) else value
        }
        return text(JsonObject(masked).toString())
    }

    private fun parseObject(argsJson: String): JsonObject? = try {
        Json.parseToJsonElement(argsJson) as? JsonObject
    } catch (e: SerializationException) {
        null
    }

    private val STRING_LITERAL = Regex("""'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"|`(?:\\.|[^`\\])*`""")

    /** 模板字符串里的 `${…}`:表达式不是字面量,保留它的位置。 */
    private val TEMPLATE_HOLE = Regex("""\$\{[^}]*\}""")
    private val MEMORY_ARGS = setOf("content", "quote", "keywords")

    /** 一次性验证码一类的提示词。 */
    private val CODE_WORD = Regex("""验证|校验码|动态码|动态密码|确认码|安全码|短信|口令|(?i:\b(?:code|otp|passcode|pin|2fa)\b)""")

    /** 4–8 位数字,数字之间可以夹一个空格或连字符,如「4 8 2 9 1 3」「482-913」。 */
    private val CODE_DIGITS = Regex(
        """(?<![\d.¥$€£])(?<!\d,)(?<![¥$€£] )\d(?:[ \-]?\d){3,7}(?!\d)(?!\.\d)(?!,\d{3}(?!\d))""" +
            // 紧跟单位、年份或金额的是数量,不当验证码
            """(?!\s*(?:年|元|块|角|分钟|小时|秒|天|月|日|号|次|个|人|件|条|米|公里|千米|克|公斤|千克|斤|吨|度|℃|°|%|‰|[¥$€£]|(?i:km|kg|mb|gb|kb|ms|cm|mm|mah|rmb|usd|cny)\b))""",
    )

    /** 验证码提示词与数字之间允许隔开的最大字数。 */
    private const val CODE_WINDOW = 24

    /**
     * 证据账本的材料(读屏、网页、命令输出)入账前走这一层:先按 [text] 遮掉
     * 已知的 Key,再把附近出现「验证」「短信」「code」等提示的 4–8 位数字遮住。
     * 密码框里的字本来就不会进入读屏文字,这里不重复处理。
     */
    fun evidence(raw: String): String {
        val redacted = text(raw)
        val hints = CODE_WORD.findAll(redacted).map { it.range }.toList()
        if (hints.isEmpty()) return redacted
        return CODE_DIGITS.replace(redacted) { candidate ->
            val close = hints.any { hint ->
                candidate.range.first - hint.last in 1..CODE_WINDOW + 1 ||
                    hint.first - candidate.range.last in 1..CODE_WINDOW + 1
            }
            if (close) MASK else candidate.value
        }
    }

    fun text(s: String): String {
        var redacted = s
        for (pattern in listOf(SK_KEY, GOOGLE_KEY, GOOGLE_AUTH_KEY, GITHUB_TOKEN)) {
            redacted = pattern.replace(redacted, MASK)
        }
        return BEARER.replace(redacted, "Bearer $MASK")
    }

    // ---------- 网页网络数据 ----------

    data class Network(val url: String?, val body: String?, val headers: Map<String, String>?)

    /**
     * 网页网络数据(地址、正文、头)离开网络模块前统一经过这里,保证交给模型、
     * 核查证据和运行记录的是同一份。名字像凭据的查询参数、JSON/表单字段和头的值
     * 记为 [MASK](按词判断,author、keyword 不算);OAuth 回跳里的 code、state
     * 一并遮住;JWT、长且字符种类混合的串以及 [text] 认得的 Key 也不留。
     */
    fun network(
        url: String? = null,
        body: String? = null,
        headers: Map<String, String>? = null,
    ): Network {
        val safeUrl = url?.let { values(maskUrl(it), slash = false) }
        val safeBody = body?.let { values(maskBody(it), slash = true) }
        val safeHeaders = headers?.mapValues { (name, value) ->
            if (secretName(name)) MASK else values(value, slash = true)
        }
        return Network(safeUrl, safeBody, safeHeaders)
    }

    /**
     * 脚本与站点命令从页面读出的数据(JSON)。规则同网络正文,只是长串不跨斜杠
     * 识别,避免把网址的路径段整个当成凭据。
     */
    fun pageData(s: String): String = values(maskBody(s), slash = false)

    private val SECRET_WORDS = setOf(
        "token", "tokens", "secret", "password", "passwd", "pwd", "passcode", "session", "sessionid", "sessid", "sid",
        "auth", "authorization", "csrf", "xsrf", "signature", "sig", "otp", "cookie", "credential", "credentials",
        "apikey", "jwt", "bearer", "pin", "privatekey",
    )
    private val SECRET_COMPOUNDS = setOf("apikey", "accesskey", "secretkey", "privatekey", "clientsecret", "sessionkey", "authkey")
    private val CASE_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
    private val WORD_SPLIT = Regex("[^A-Za-z0-9]+")

    /** 名字拆成小写词后是否含凭据语义(驼峰、下划线、连字符都算分隔)。 */
    internal fun secretName(name: String): Boolean {
        val words = name.split(WORD_SPLIT)
            .flatMap { it.split(CASE_BOUNDARY) }
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
        return words.any { it in SECRET_WORDS } || words.joinToString("") in SECRET_COMPOUNDS
    }

    private val OAUTH_MARKERS = setOf("state", "redirect_uri", "client_id", "code_verifier")
    private val OAUTH_RESULTS = setOf("code", "state")

    /**
     * 查询串或片段里的 `a=b&c=d`。凭据名的值遮住;如果看起来是 OAuth 回跳
     * (带 state 等标记,并且带 code 或凭据),code、state 也一起遮。
     * [bareKey] 为 true 时单独的 `key` 参数也算凭据。
     */
    private fun maskParams(query: String, bareKey: Boolean): String {
        val pairs = query.split('&')
        val names = pairs.map { it.substringBefore('=').lowercase() }
        val oauthReturn = names.any { it in OAUTH_MARKERS } &&
            (names.contains("code") || names.any { secretName(it) })
        return pairs.joinToString("&") { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@joinToString pair
            val name = pair.substring(0, separator)
            val lower = name.lowercase()
            val hide = secretName(name) ||
                (bareKey && lower == "key") ||
                (oauthReturn && lower in OAUTH_RESULTS)
            if (hide) "$name=$MASK" else pair
        }
    }

    private fun maskUrl(raw: String): String {
        val hashAt = raw.indexOf('#')
        val beforeHash = if (hashAt >= 0) raw.substring(0, hashAt) else raw
        val fragment = if (hashAt >= 0) raw.substring(hashAt + 1) else null
        val queryAt = beforeHash.indexOf('?')
        val maskedBase = if (queryAt >= 0) {
            beforeHash.substring(0, queryAt + 1) +
                maskParams(beforeHash.substring(queryAt + 1), bareKey = true)
        } else {
            beforeHash
        }
        if (fragment == null) return maskedBase
        val maskedFragment = if ('=' in fragment) maskParams(fragment, bareKey = true) else fragment
        return "$maskedBase#$maskedFragment"
    }

    private val JSON_PAIR = Regex(""""((?:[^"\\]|\\.){1,100})"(\s*:\s*)("(?:[^"\\]|\\.)*"|-?\d[\d.eE+\-]*|true|false|null)""")
    private val FORM = Regex("""^[^\s{}\[\]"<>]+=[^\s]*$""")

    private fun maskBody(body: String): String {
        if (FORM.matches(body)) return maskParams(body, bareKey = false)
        return JSON_PAIR.replace(body) { match ->
            val name = match.groupValues[1]
            if (secretName(name)) {
                "\"$name\"${match.groupValues[2]}\"$MASK\""
            } else {
                match.value
            }
        }
    }

    private val JWT = Regex("""\beyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}""")
    private val LONG_WITH_SLASH = Regex("""[A-Za-z0-9_\-+/=]{32,}""")
    private val LONG = Regex("""[A-Za-z0-9_\-+=]{32,}""")

    /**
     * 值里的 JWT、长且同时含大小写字母和数字的串、已知格式的 Key。
     * [slash] 决定长串是否把 `/` 当作串的一部分。
     */
    private fun values(s: String, slash: Boolean): String {
        var out = JWT.replace(s, MASK)
        val longRuns = if (slash) LONG_WITH_SLASH else LONG
        out = longRuns.replace(out) { match ->
            val value = match.value
            val mixed = value.any(Char::isUpperCase) &&
                value.any(Char::isLowerCase) &&
                value.any(Char::isDigit)
            if (mixed) MASK else value
        }
        return text(out)
    }
}
