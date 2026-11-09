package com.daiyimo.maeve.core.access

import com.daiyimo.maeve.core.model.Author
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.Role
import com.daiyimo.maeve.core.prompt.Prompts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 用模型按主要用途给应用打分类标签。
 *
 * 分类结果只用于整理与推荐，不参与权限判定；
 * [language] 决定理由文案的语言（界面语言），由调用方提供，枚举名本身固定为英文。
 *
 * @param model 首选模型
 * @param fallback 首选模型拿不到结果时换用的模型
 */
class AppClassifier(
    private val model: ChatModel,
    private val fallback: ChatModel? = null,
    private val language: String = "English",
) {
    /**
     * 给 [apps]（包名到界面标签）打标签。模型不可用或输出不可解析时返回空表，
     * 调用方按「未分类」渲染即可。
     */
    suspend fun classify(apps: List<Pair<String, String>>): Map<String, AppClassification> {
        if (apps.isEmpty()) return emptyMap()
        val request = buildJsonObject {
            put("language", language)
            putJsonArray("apps") {
                apps.forEach { (pkg, label) ->
                    add(buildJsonObject {
                        put("package", pkg)
                        put("label", label)
                    })
                }
            }
        }
        val candidates = listOfNotNull(model, fallback?.takeUnless { it === model })
        for (candidate in candidates) {
            val outcome = try {
                withTimeoutOrNull(TIMEOUT_MS) {
                    val reply = candidate.complete(
                        Prompts.role("app_classifier"),
                        listOf(Message(Role.User, listOf(Part.Text(request.toString())), Author.User)),
                        emptyList(),
                    )
                    val text = reply.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
                    parse(text, apps.map { it.first }.toSet())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (!outcome.isNullOrEmpty()) return outcome
        }
        return emptyMap()
    }

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val REASON_LIMIT = 240

        /**
         * 解析模型输出。只接受 `[{package, category, reason}, …]` 形式的数组，
         * 未知包名直接丢弃，单项解析失败不影响其余条目。
         */
        fun parse(raw: String, packages: Set<String>): Map<String, AppClassification> =
            runCatching {
                val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                Json.parseToJsonElement(cleaned).jsonArray.mapNotNull { item ->
                    val obj = item.jsonObject
                    val pkg = obj["package"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    if (pkg !in packages) return@mapNotNull null
                    val category = AppCategory.from(obj["category"]?.jsonPrimitive?.content)
                    pkg to AppClassification(category, obj["reason"]?.jsonPrimitive?.content.orEmpty().take(REASON_LIMIT))
                }.toMap()
            }.getOrDefault(emptyMap())
    }
}
