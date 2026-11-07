package com.daiyimo.maeve.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字号与字重。
 *
 * ## 两套字,三种声部
 *
 * 界面只有两种字体:**系统无衬线**说人话,**等宽**说机器话。这不是装饰,是分层:
 *
 * 1. **等宽大写微标签**([Eyebrow])——`LOOP 03`、`CRADLE · RUNNING`、`12 STEPS`。
 *    字距拉到 0.16em,字号压到 10.5。它是整套界面最省力的记忆点:任何一行字加上
 *    这层壳,立刻从「正文」变成「读数」,而且不花一分钱授权费、不增加一个字节的包体。
 * 2. **等宽读数**([Mono]/[Num])——耗时、步数、token、版本号、时间。数字一律
 *    tabular(等宽数字),跳动时不左右抖。
 * 3. **无衬线正文**——其余一切。
 *
 * ## 字重只用 400 / 500 / 600
 *
 * AOSP 只带 Noto Sans CJK Regular,w500 会被映射回 Regular、w600+ 走合成加粗。
 * 所以层级主要靠**字号 + 颜色 + 字距**,字重只在真正需要压住的地方用 600。
 * 大号展示字用 500(不用 Bold 合成,中文加粗合成后笔画会糊)。
 */
object MaeveType {

    // ---------- 无衬线 ----------

    /**
     * 展示字:欢迎页、空状态问候。
     *
     * **负字距是这套界面「精密感」的全部来源**。Linear 在 80px 上用 −0.04em,
     * 一路到手 16px 收到 0;照同样的比例,30sp 用 −0.022em(约 −0.66sp)。
     * 正字距的大字会散、会松、会像营销页;收紧之后字块才成为**一个形状**。
     */
    val Display = TextStyle(fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.66).sp)
    /** 大标题。 */
    val LargeTitle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.48).sp)
    /** 页面标题:顶栏。 */
    val Title = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.17).sp)
    /** 正文、行标题。 */
    val Body = TextStyle(fontSize = 15.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.05).sp)
    /** 卡片标题等需要强调的正文。 */
    val BodyStrong = TextStyle(fontSize = 15.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
    /** 说明文字。 */
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal)
    /** 分组小标题。 */
    val Label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    /** 提示、页脚。 */
    val Hint = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
    /** 行尾的值。 */
    val Value = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)

    /**
     * 她的回答正文。比 [Body] 松一点、大一点点:
     * 这是全 App 唯一需要被「读」而不是被「扫」的文本,行高给到 1.58。
     */
    val Script = TextStyle(fontSize = 15.5.sp, lineHeight = 24.5.sp, fontWeight = FontWeight.Normal)

    // ---------- 等宽 ----------

    private val MonoFamily = FontFamily.Monospace
    /** 等宽数字:同一行里的耗时/计数不会左右跳。 */
    private const val TABULAR = "tnum"

    /**
     * 微标签。
     *
     * 上一版是「等宽 + 大写 + 1.7sp 宽字距」,铺满整站之后成了噪音——
     * 到处都是宽字距大写,等于到处都是重点,也就没有重点。
     * 现在退回 **小号、中灰、正常字距**:它只是一行比正文更轻的说明,
     * 不再试图当"仪表面板"。
     */
    val Eyebrow = TextStyle(
        fontFamily = MonoFamily, fontSize = 10.5.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp,
    )

    /** 更小一档的微标签:卡片角标、行内分类。 */
    val EyebrowSmall = TextStyle(
        fontFamily = MonoFamily, fontSize = 9.5.sp, lineHeight = 13.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp,
    )

    /** 等宽读数:耗时、步数、token、版本号。 */
    val Mono = TextStyle(
        fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp,
        fontFeatureSettings = TABULAR,
    )

    /** 小一档读数:行内紧跟说明的次要数字。 */
    val MonoSmall = TextStyle(
        fontFamily = MonoFamily, fontSize = 10.5.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.Normal, fontFeatureSettings = TABULAR,
    )

    /** 大一号读数:用量页、关于页的主数字。 */
    val Numeral = TextStyle(
        fontFamily = MonoFamily, fontSize = 22.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp,
        fontFeatureSettings = TABULAR,
    )

    /**
     * 微标签的中文档。
     *
     * 拉丁文在 10.5sp + 宽字距下是漂亮的「仪器印刷字」;同样的字号套到汉字上就太小了,
     * 汉字的辨识靠笔画密度,10.5sp 时笔锋会糊成一团。所以凡是含中文的微标签自动升到
     * 11.5sp(字距略收,否则一行放不下),拉丁标签维持原样。
     */
    fun eyebrowFor(text: String): TextStyle =
        if (text.any { it.code > 0x2E80 }) Eyebrow.copy(fontSize = 11.5.sp) else Eyebrow

    /** 台词轨上的轮次号。 */
    val Turn = TextStyle(
        fontFamily = MonoFamily, fontSize = 10.sp, lineHeight = 12.sp,
        fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR,
    )
}

/** 间距。页面左右留 [Page]。阶梯以 4 的倍数为主。 */
object MaeveSpace {
    val S2 = 2.dp
    val S4 = 4.dp
    val S6 = 6.dp
    val S8 = 8.dp
    val S10 = 10.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val S20 = 20.dp
    val S24 = 24.dp
    val S32 = 32.dp
    val S40 = 40.dp
    val Page = 18.dp
}

/** 各类元素的尺寸。 */
object MaeveSize {
    val TopBar = 56.dp
    val IconButton = 40.dp
    val RowMin = 52.dp
    val Button = 52.dp
    val Composer = 52.dp
    val Icon = 22.dp
    /** 列表行左边的图标块,里面的图形是 [TileIcon]。 */
    val Tile = 30.dp
    val TileIcon = 18.dp
    /** 行尾箭头等小图标。 */
    val SmallIcon = 16.dp
    const val IconStroke = 1.7f
    /** 台词轨的宽度(竖线 + 轮次号那一栏)。 */
    val Rail = 22.dp
    /** 钢琴卷一根音符条的宽度与间距。 */
    val Note = 3.dp
    val NoteGap = 2.5.dp
}
