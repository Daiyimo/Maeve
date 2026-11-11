package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.i18n.trPlural
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.core.usage.ModelUsage
import com.daiyimo.maeve.core.usage.ProviderUsage
import com.daiyimo.maeve.core.usage.UsageBucket
import com.daiyimo.maeve.core.usage.UsageQuery
import com.daiyimo.maeve.core.usage.UsageRange
import com.daiyimo.maeve.core.usage.UsageReport
import com.daiyimo.maeve.core.usage.UsageStep
import com.daiyimo.maeve.ui.theme.MaeveColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import com.daiyimo.maeve.i18n.uiLocale
import kotlin.math.roundToInt

enum class UsagePreset(private val labelRes: Int) {
    Day(R.string.usage_24h), Week(R.string.usage_7d), Month(R.string.usage_30d), Quarter(R.string.usage_90d), Custom(R.string.usage_custom);

    val label: String get() = tr(labelRes)

    /** 这一段对应的范围;[Custom] 要给出 [from]..[to](本地日期)。 */
    fun range(from: LocalDate?, to: LocalDate?): UsageRange = when (this) {
        Day -> UsageRange.Last24Hours
        Week -> UsageRange.LastDays(7)
        Month -> UsageRange.LastDays(30)
        Quarter -> UsageRange.LastDays(90)
        Custom -> if (from != null && to != null) UsageRange.Custom(from, to) else UsageRange.LastDays(7)
    }
}


data class ModelRank(val provider: ProviderUsage, val model: ModelUsage, val share: Double)


object UsageCopy {
    val TITLE: String get() = tr(R.string.usage_title)
    val NONE: String get() = tr(R.string.usage_none)
    val EMPTY_TITLE: String get() = tr(R.string.usage_empty_title)
    val EMPTY_BODY: String get() = tr(R.string.usage_empty_body)
    val NOTE: String get() = tr(R.string.usage_note)
    val TOTAL: String get() = tr(R.string.usage_total)
    val TREND: String get() = tr(R.string.usage_trend)
    val BY_PROVIDER: String get() = tr(R.string.usage_by_provider)
    val BY_PROVIDER_HINT: String get() = tr(R.string.usage_by_provider_hint)
    val BY_MODEL: String get() = tr(R.string.usage_by_model)
    val ALL: String get() = tr(R.string.usage_all)
    val ALL_PROVIDERS: String get() = tr(R.string.usage_all_providers)
    val OTHERS: String get() = tr(R.string.usage_others)
    val DELETED: String get() = tr(R.string.usage_deleted)
    val CHANGE_DATES: String get() = tr(R.string.usage_change_dates)
    const val UNIT = "Token"

    /** 图表里有自己颜色的服务商最多几家(主题里 [MaeveColors.series] 的个数),其余在趋势图里归为「其他」。 */
    const val COLORED = 3

    /**
     * token 数:一万以下照写;往上按万、亿,保留三位有效数字(四舍五入,去掉末尾的 0),
     * 例如 8765、1.23 万、12.3 万、123 万、1.5 亿;繁体写成萬、億。
     */
    fun tokens(n: Long): String {
        if (n < 10_000) return n.toString()
        val wan = round3(BigDecimal(n).divide(BigDecimal(10_000)))
        val locale = uiLocale()
        if (locale.language == "zh") {
            val hant = locale.script == "Hant"
            if (wan < BigDecimal(10_000)) return "${wan.stripTrailingZeros().toPlainString()} ${if (hant) "萬" else "万"}"
            val yi = round3(BigDecimal(n).divide(BigDecimal(100_000_000)))
            return "${yi.stripTrailingZeros().toPlainString()} ${if (hant) "億" else "亿"}"
        }
        // 英文按千、百万、十亿写:12.3K、1.5M
        for ((unit, size) in listOf("K" to 1_000L, "M" to 1_000_000L, "B" to 1_000_000_000L)) {
            val v = round3(BigDecimal(n).divide(BigDecimal(size)))
            if (v < BigDecimal(1_000) || unit == "B") return "${v.stripTrailingZeros().toPlainString()}$unit"
        }
        return n.toString()
    }

    private fun round3(v: BigDecimal): BigDecimal = v.setScale(
        when {
            v < BigDecimal.TEN -> 2
            v < BigDecimal(100) -> 1
            else -> 0
        },
        RoundingMode.HALF_UP,
    )

    /** 设置首页那一行的值:近 7 天的合计,没有时写「还没有」。 */
    fun settingsValue(weekTokens: Long): String = if (weekTokens <= 0) NONE else tr(R.string.usage_last_7d, tokens(weekTokens))

    fun calls(n: Long) = trPlural(R.plurals.times, n.toInt())

    /** 占比:不到 1% 但不是 0 时写「<1%」。 */
    fun percent(share: Double): String {
        val p = (share * 100).roundToInt()
        return if (p == 0 && share > 0) "<1%" else "$p%"
    }

    fun missing(n: Long) = trPlural(R.plurals.usage_missing, n.toInt())

    fun providerName(p: ProviderUsage) = if (p.deleted) p.name + DELETED else p.name

    fun stepLabel(step: UsageStep) = when (step) {
        UsageStep.Hour -> tr(R.string.usage_hourly)
        UsageStep.Day -> tr(R.string.usage_daily)
        UsageStep.Week -> tr(R.string.usage_weekly)
    }

    /** 本地日期:今年的不写年份。 */
    fun date(d: LocalDate, today: LocalDate): String =
        if (d.year == today.year) tr(R.string.date_month_day, d.monthValue, d.month.getDisplayName(java.time.format.TextStyle.SHORT, uiLocale()), d.dayOfMonth)
        else tr(R.string.date_year_month_day, d.year, d.monthValue, d.month.getDisplayName(java.time.format.TextStyle.SHORT, uiLocale()), d.dayOfMonth)

    /** 范围选择下面那一行,写出实际是哪几天。 */
    fun rangeText(range: UsageRange, today: LocalDate): String = when (range) {
        UsageRange.Last24Hours -> tr(R.string.usage_past_24h)
        is UsageRange.LastDays -> tr(R.string.usage_until_today, date(UsageQuery.days(range, today).first, today))
        is UsageRange.Custom -> UsageQuery.days(range, today).let { (from, to) ->
            if (from == to) date(from, today) else "${date(from, today)} – ${date(to, today)}"
        }
    }

    /** 趋势图下面的刻度:按小时写「18:00」(此刻那一格写「现在」),按天、按周写「10/27」(今天写「今天」)。 */
    fun axis(b: UsageBucket, step: UsageStep, today: LocalDate, last: Boolean = false): String = when {
        step == UsageStep.Hour && last -> tr(R.string.usage_now)
        step == UsageStep.Hour -> "%d:%02d".format(b.start.hour, b.start.minute)
        b.start.toLocalDate() == today -> tr(R.string.usage_today)
        else -> "${b.start.monthValue}/${b.start.dayOfMonth}"
    }

    /** 点中一根柱子时说的是哪段时间。 */
    fun period(b: UsageBucket, step: UsageStep, today: LocalDate): String = when (step) {
        UsageStep.Hour -> "%d:%02d".format(b.start.hour, b.start.minute)
        UsageStep.Day -> date(b.start.toLocalDate(), today)
        UsageStep.Week -> {
            val first = b.start.toLocalDate()
            val last = b.lastDay ?: first
            if (first == last) date(first, today) else "${date(first, today)} – ${date(last, today)}"
        }
    }

    /** 这家服务商在图表里用第几种颜色;排在 [COLORED] 家之后的为 null(灰色、趋势图里归「其他」)。 */
    fun colorSlot(p: ProviderUsage): Int? = p.order.takeIf { it < COLORED }

    /** 网格线的顶:不小于 [max] 的 1、2、4、6、8 乘 10 的幂,一半处的刻度也是整数。 */
    fun niceMax(max: Long): Long {
        if (max <= 0) return 1
        var scale = 1L
        while (scale * 10 < max) scale *= 10
        return listOf(1L, 2L, 4L, 6L, 8L, 10L).map { it * scale }.first { it >= max }
    }

    /** 按模型排行:选了服务商只列这家的,没选列全部服务商的;按 token 从多到少,占比按这一排的合计。 */
    fun modelRanking(report: UsageReport, providerId: String?): List<ModelRank> {
        val from = providerId?.let { id -> listOfNotNull(report.provider(id)) } ?: report.providers
        val rows = from.flatMap { p -> p.models.map { p to it } }
            .sortedWith(compareByDescending<Pair<ProviderUsage, ModelUsage>> { it.second.totals.tokens }.thenByDescending { it.second.totals.calls })
        val total = rows.sumOf { it.second.totals.tokens }
        return rows.map { (p, m) -> ModelRank(p, m, if (total <= 0) 0.0 else m.totals.tokens.toDouble() / total) }
    }
}


private fun seriesColor(c: MaeveColors, p: ProviderUsage): Color = UsageCopy.colorSlot(p)?.let { c.series.getOrNull(it) } ?: c.seriesOther


private val TotalStyle = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)


private val CardTitle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)


private val RowShape = RoundedCornerShape(8.dp)


private val RankText = TextStyle(fontSize = 13.5.sp, lineHeight = 18.sp)


private val RankMono = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)


private val RankValue = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)


private val RankShare = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)


private val ChipText = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)


private data class StackPart(val slot: Int?, val tokens: Long)


private val LegendShape = RoundedCornerShape(3.dp)


private val GridText = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp)


private val TipShape = RoundedCornerShape(8.dp)


private val TipText = TextStyle(fontSize = 11.5.sp, lineHeight = 18.sp)
