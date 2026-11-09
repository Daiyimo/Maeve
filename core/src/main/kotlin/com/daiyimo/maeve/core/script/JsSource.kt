package com.daiyimo.maeve.core.script

/**
 * 词法级的 `import` 检查：判断一段 JS 源码里有没有会被当作 `import` 使用的词。
 *
 * 只看词法，不建语法树。字符串、注释、正则字面量内部出现的 `import` 不算数。
 * 之所以连动态 `import()` 一起拦，是因为它会在全局作用域里执行模块，绕开只读包装。
 *
 * `/` 的身份并不总由前一个字符决定：它出现在 `)`、`}`、`++`、`--` 之后，
 * 或出现在 `of`/`yield`/`await` 这类既可能是关键字、也可能是变量名的词之后时，
 * 既可能开启一个正则字面量，也可能是除号。此时两种读法都保留，后续各自推进；
 * 只要其中任一种读法在代码区读到 `import` 就返回 true。
 *
 * 为避免指数膨胀，读法按 (位置, `/` 的身份, 嵌套栈) 去重；同一快照只扫一遍。
 * 读到行尾仍没有闭合的字符串或正则属于不成立的读法，直接丢弃。
 * 整体取舍是宁可误拦，不可漏放。
 */
object JsSource {

    /** 当前位置的 `/` 可以被理解成什么。 */
    private enum class SlashRole {
        /** 正则字面量的开头。 */
        Pattern,

        /** 除法运算符。 */
        Division,

        /** 由上文无法判定，两种读法都得试。 */
        Unresolved,
    }

    /** 一条读法在某一时刻的快照：读到哪、`/` 的身份、模板插值与花括号共用的嵌套栈。 */
    private data class Reading(val at: Int, val slash: SlashRole, val nesting: List<Char>)

    /** [Scanner] 推进一次的结果。 */
    private sealed interface Outcome {
        /** 读到了 import。 */
        data object Found : Outcome

        /** 这条读法走不通，或已经读完。 */
        data object Dropped : Outcome

        /** 在身份未定的 `/` 处分叉。 */
        class Branching(val readings: List<Reading>) : Outcome
    }

    /** 这些词之后出现的 `/` 必是正则开头。 */
    private val PATTERN_AFTER = setOf(
        "return",
        "typeof",
        "instanceof",
        "in",
        "new",
        "delete",
        "void",
        "throw",
        "case",
        "do",
        "else",
    )

    /** 这些词既可能是关键字也可能是标识符，其后的 `/` 保留两种读法。 */
    private val UNRESOLVED_AFTER = setOf("of", "yield", "await", "let", "async", "get", "set", "static")

    /**
     * [code] 的代码区里是否出现了 `import`。
     *
     * 字符串、注释、正则字面量里的不计；两可的 `/` 会展开成多条读法分别检查。
     */
    fun usesImport(code: String): Boolean {
        val finished = HashSet<Reading>()
        val queue = ArrayDeque<Reading>()
        queue.addLast(Reading(at = 0, slash = SlashRole.Pattern, nesting = emptyList()))
        while (queue.isNotEmpty()) {
            val reading = queue.removeLast()
            if (!finished.add(reading)) continue
            when (val outcome = Scanner(code, reading).advance()) {
                Outcome.Found -> return true
                is Outcome.Branching -> queue.addAll(outcome.readings)
                Outcome.Dropped -> Unit
            }
        }
        return false
    }

    /** 从一条 [Reading] 出发，沿同一套词法规则向前走。 */
    private class Scanner(private val code: String, start: Reading) {
        private var at = start.at
        private var slash = start.slash
        private val nesting = start.nesting.toMutableList()
        private val length = code.length

        fun advance(): Outcome {
            while (at < length) {
                val ch = code[at]
                when {
                    ch.isWhitespace() -> at++

                    ch == '/' && next(1) == '/' -> skipLineComment()

                    ch == '/' && next(1) == '*' -> skipBlockComment()

                    ch == '/' -> when (slash) {
                        SlashRole.Division -> {
                            at++
                            slash = SlashRole.Pattern
                        }

                        SlashRole.Pattern -> if (!readPattern()) return Outcome.Dropped

                        SlashRole.Unresolved -> return Outcome.Branching(
                            listOf(
                                Reading(at, SlashRole.Pattern, nesting.toList()),
                                Reading(at, SlashRole.Division, nesting.toList()),
                            ),
                        )
                    }

                    ch == '\'' || ch == '"' -> {
                        if (!readQuoted(ch)) return Outcome.Dropped
                        slash = SlashRole.Division
                    }

                    ch == '`' -> {
                        at++
                        if (!readTemplateRest()) return Outcome.Dropped
                    }

                    ch == '{' -> {
                        nesting += '{'
                        at++
                        slash = SlashRole.Pattern
                    }

                    ch == '}' -> if (!closeCurly()) return Outcome.Dropped

                    ch == ')' -> {
                        at++
                        slash = SlashRole.Unresolved
                    }

                    ch == ']' -> {
                        at++
                        slash = SlashRole.Division
                    }

                    (ch == '+' || ch == '-') && next(1) == ch -> {
                        at += 2
                        slash = SlashRole.Unresolved
                    }

                    ch.isDigit() || (ch == '.' && next(1)?.isDigit() == true) -> {
                        skipNumber()
                        slash = SlashRole.Division
                    }

                    startsIdentifier(ch) -> if (readIdentifier()) return Outcome.Found

                    else -> {
                        at++
                        slash = SlashRole.Pattern
                    }
                }
            }
            return Outcome.Dropped
        }

        /** 标识符；命中 `import` 返回 true，否则按关键字表定下 `/` 的默认身份。 */
        private fun readIdentifier(): Boolean {
            val spelling = decodeIdentifier()
            if (spelling == "import") return true
            slash = when {
                spelling in PATTERN_AFTER -> SlashRole.Pattern
                spelling in UNRESOLVED_AFTER -> SlashRole.Unresolved
                else -> SlashRole.Division
            }
            return false
        }

        /** 标识符原文；`\uXXXX` 与 `\u{…}` 先解成对应字符再比较。 */
        private fun decodeIdentifier(): String {
            val spelling = StringBuilder()
            while (at < length && continuesIdentifier(code[at])) {
                if (code[at] == '\\' && next(1) == 'u') {
                    val braced = next(2) == '{'
                    val from = at + if (braced) 3 else 2
                    val to = if (braced) {
                        code.indexOf('}', from).takeIf { it >= 0 } ?: length
                    } else {
                        minOf(from + 4, length)
                    }
                    code.substring(from, to).toIntOrNull(16)?.let { spelling.appendCodePoint(it) }
                    at = if (braced) minOf(to + 1, length) else to
                } else {
                    spelling.append(code[at])
                    at++
                }
            }
            return spelling.toString()
        }

        /** 引号字符串；行内没闭合（又不是续行）就算失败。 */
        private fun readQuoted(quote: Char): Boolean {
            at++
            while (at < length) {
                val ch = code[at]
                when {
                    ch == '\\' -> at += 2
                    ch == quote -> {
                        at++
                        return true
                    }
                    endsLine(ch) -> return false
                    else -> at++
                }
            }
            return false
        }

        /** 模板字符串的文字部分；遇到 `${` 压栈并回到代码读法，遇到收尾反引号结束。 */
        private fun readTemplateRest(): Boolean {
            while (at < length) {
                val ch = code[at]
                when {
                    ch == '\\' -> at += 2
                    ch == '`' -> {
                        at++
                        slash = SlashRole.Division
                        return true
                    }
                    ch == '$' && next(1) == '{' -> {
                        nesting += '`'
                        at += 2
                        slash = SlashRole.Pattern
                        return true
                    }
                    else -> at++
                }
            }
            return false
        }

        /** 正则字面量本体与标志位；字符类里的 `/` 不算收尾，行内没闭合算失败。 */
        private fun readPattern(): Boolean {
            at++
            var insideClass = false
            while (at < length) {
                val ch = code[at]
                when {
                    endsLine(ch) -> return false
                    ch == '\\' -> at += 2
                    ch == '[' -> {
                        insideClass = true
                        at++
                    }
                    ch == ']' -> {
                        insideClass = false
                        at++
                    }
                    ch == '/' && !insideClass -> {
                        at++
                        while (at < length && continuesIdentifier(code[at])) at++
                        slash = SlashRole.Division
                        return true
                    }
                    else -> at++
                }
            }
            return false
        }

        /** `}`：在模板插值里就回去接着读模板文字，否则弹出普通花括号。 */
        private fun closeCurly(): Boolean {
            at++
            if (nesting.lastOrNull() == '`') {
                nesting.removeAt(nesting.lastIndex)
                return readTemplateRest()
            }
            if (nesting.isNotEmpty()) nesting.removeAt(nesting.lastIndex)
            slash = SlashRole.Unresolved
            return true
        }

        private fun skipLineComment() {
            while (at < length && !endsLine(code[at])) at++
        }

        private fun skipBlockComment() {
            val close = code.indexOf("*/", at + 2)
            at = if (close < 0) length else close + 2
        }

        private fun skipNumber() {
            while (at < length) {
                val ch = code[at]
                if (!(ch.isLetterOrDigit() || ch == '.' || ch == '_')) return
                at++
            }
        }

        private fun next(offset: Int): Char? = code.getOrNull(at + offset)

        private fun endsLine(ch: Char) = ch == '\n' || ch == '\r' || ch == '\u2028' || ch == '\u2029'

        private fun startsIdentifier(ch: Char) = ch == '$' || ch == '_' || ch == '\\' || ch.isLetter()

        private fun continuesIdentifier(ch: Char) =
            startsIdentifier(ch) || ch.isDigit() || ch == '\u200C' || ch == '\u200D'
    }
}
