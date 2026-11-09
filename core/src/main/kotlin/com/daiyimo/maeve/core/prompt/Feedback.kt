package com.daiyimo.maeve.core.prompt

/**
 * 工具结果与主循环里给模型的反馈文字(提示、错误说明、提醒、参考资料的标题),都在 prompts/feedback/ 下的 .md 文件里:
 * 每条以一行「## 键」开头,下面到下一条之前是原文(可以多行,首尾空行不算);{{name}} 由调用方填。
 */
object Feedback {
    /** 反馈文件;键在所有文件里唯一。 */
    val FILES = listOf("tools", "loop")

    private val head = Regex("^## ([a-z0-9_.]+)$")
    private val entries: Map<String, String> by lazy { loadAll() }

    /** 一个反馈文件里的各条(键 → 原文),按出现顺序;第一条之前的说明不算。 */
    fun parse(text: String): List<Pair<String, String>> {
        val found = ArrayList<Pair<String, String>>()
        var current: String? = null
        val lines = ArrayList<String>()
        for (line in text.lines()) {
            val match = head.matchEntire(line)
            if (match == null) {
                if (current != null) lines += line
                continue
            }
            current?.let { found += it to lines.joinToString("\n").trim('\n') }
            lines.clear()
            current = match.groupValues[1]
        }
        current?.let { found += it to lines.joinToString("\n").trim('\n') }
        return found
    }

    operator fun get(key: String): String = Prompts.fill(raw(key), emptyMap())

    fun format(key: String, vararg vars: Pair<String, Any?>): String = Prompts.fill(raw(key), vars.toMap())

    fun has(key: String): Boolean = entries.containsKey(key)

    /** 键以 [prefix] 开头的各条,键去掉前缀(例如按错误码给的说明)。 */
    fun section(prefix: String): Map<String, String> {
        val all = entries
        return buildMap {
            for ((key, value) in all) {
                if (key.startsWith(prefix)) put(key.removePrefix(prefix), value)
            }
        }
    }

    val keys: Set<String> get() = entries.keys

    private fun raw(key: String): String = checkNotNull(entries[key]) { "missing feedback $key" }

    private fun loadAll(): Map<String, String> = buildMap {
        for (file in FILES) {
            for ((key, text) in parse(Prompts.read("feedback/$file.md"))) {
                check(put(key, text) == null) { "duplicate feedback key $key" }
            }
        }
    }
}
