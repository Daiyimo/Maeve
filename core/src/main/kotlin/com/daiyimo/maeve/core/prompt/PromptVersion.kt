package com.daiyimo.maeve.core.prompt

import com.daiyimo.maeve.core.model.ToolSpec
import java.security.MessageDigest

/** 本任务提示词的版本:系统提示词加工具定义的摘要,写进运行记录;对比评测时据此区分提示词版本。 */
object PromptVersion {
    private const val DIGEST_BYTES = 6

    fun of(system: String, tools: List<ToolSpec>): String {
        val material = buildString {
            append(system)
            for (tool in tools) {
                append("\n\n").append(tool.name).append('\n')
                append(tool.description).append('\n')
                append(tool.parametersJsonSchema)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return buildString(DIGEST_BYTES * 2) {
            for (index in 0 until DIGEST_BYTES) append("%02x".format(digest[index]))
        }
    }
}
