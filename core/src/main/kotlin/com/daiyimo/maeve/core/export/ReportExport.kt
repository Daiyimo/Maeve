package com.daiyimo.maeve.core.export

import com.daiyimo.maeve.core.record.Redactor

/** 导出脱敏级别;默认只遮凭据,用户可选连常见个人联系信息一起遮。 */
enum class ReportExportRedaction { Secrets, SecretsAndPii }

/** 报告渲染前的原始材料。它不能直接交给 Markdown/DOCX 后端。 */
data class ReportExportInput(
    val title: String,
    val summary: List<String> = emptyList(),
    val preface: List<String> = emptyList(),
    val criteria: List<String> = emptyList(),
    val body: String = "",
    val headings: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val attachmentNames: List<String> = emptyList(),
    val toolTexts: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** 已脱敏材料;导出后端只允许消费这一类型。 */
data class ReportExportDocument(
    val title: String,
    val summary: List<String>,
    val preface: List<String>,
    val criteria: List<String>,
    val body: String,
    val headings: List<String>,
    val urls: List<String>,
    val attachmentNames: List<String>,
    val toolTexts: List<String>,
    val notes: List<String>,
)

/** 所有可导出的字符串都在这里脱敏;截图像素不经过本对象,也不声称已匿名化。 */
object ReportExportSanitizer {
    fun sanitize(input: ReportExportInput, level: ReportExportRedaction = ReportExportRedaction.Secrets): ReportExportDocument =
        ReportExportDocument(
            title = clean(input.title, level),
            summary = input.summary.map { clean(it, level) },
            preface = input.preface.map { clean(it, level) },
            criteria = input.criteria.map { clean(it, level) },
            body = clean(input.body, level),
            headings = input.headings.map { clean(it, level) },
            urls = input.urls.map { cleanUrl(it, level) },
            attachmentNames = input.attachmentNames.map { clean(it, level) },
            toolTexts = input.toolTexts.map { clean(it, level) },
            notes = input.notes.map { clean(it, level) },
        )

    private fun clean(raw: String, level: ReportExportRedaction): String {
        val withSafeUrls = URL.replace(raw) { match ->
            val trailing = match.value.takeLastWhile { it in ".,;!?，。；！？、" }
            cleanUrl(match.value.dropLast(trailing.length), level) + trailing
        }
        val networkSafe = Redactor.network(body = withSafeUrls).body ?: withSafeUrls
        val secrets = KEY_VALUE.replace(networkSafe) { match ->
            val name = match.groupValues[1]
            if (Redactor.secretName(name)) name + match.groupValues[2] + Redactor.MASK else match.value
        }
        return if (level == ReportExportRedaction.SecretsAndPii) ExportPii.mask(secrets) else secrets
    }

    private fun cleanUrl(raw: String, level: ReportExportRedaction): String {
        val redacted = Redactor.network(url = raw).url ?: raw
        val withoutUserInfo = redacted.replace(Regex("""^(https?://)[^/@]+@""", RegexOption.IGNORE_CASE), "$1***@")
        return if (level == ReportExportRedaction.SecretsAndPii) ExportPii.mask(withoutUserInfo) else withoutUserInfo
    }
    private val URL = Regex("""https?://[^\s<>()\[\]{}"']+""", RegexOption.IGNORE_CASE)
    private val KEY_VALUE = Regex(
        """(?<![A-Za-z0-9_])([A-Za-z][A-Za-z0-9_.-]*)(\s*[:=]\s*)("(?:\\.|[^"\\\r\n])*"|'(?:\\.|[^'\\\r\n])*'|(?i:Basic|Bearer)\s+[^\s&;]+|[^\s&;]+)""",
    )
}
