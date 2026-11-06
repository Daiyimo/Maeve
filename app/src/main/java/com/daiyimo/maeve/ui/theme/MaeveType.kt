package com.daiyimo.maeve.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字号与字重。只定大小、字重、行高,颜色由用的地方给。
 *
 * 系统无衬线负责一切文字,等宽只给代码。字重只用 400/600 两档:
 * AOSP 只带 Noto Sans CJK Regular,w500 会被映射回 Regular、w600+ 走合成加粗,
 * 层级只靠字号、颜色和 400/600。大号展示字用 600(不靠 Bold 合成)。
 */
object MaeveType {
    /** 大标题:欢迎页、空状态的问候。 */
    val LargeTitle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
    /** 页面标题:顶栏。 */
    val Title = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    /** 正文、行标题。 */
    val Body = TextStyle(fontSize = 15.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal)
    /** 卡片标题等需要强调的正文。 */
    val BodyStrong = TextStyle(fontSize = 15.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
    /** 说明文字。 */
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    /** 分组小标题。 */
    val Label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    /** 提示、页脚。 */
    val Hint = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
    /** 行尾的值。 */
    val Value = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
}

/** 间距。页面左右留 [Page]。阶梯以 4 的倍数为主。 */
object MaeveSpace {
    val S2 = 2.dp
    val S4 = 4.dp
    val S6 = 6.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val S20 = 20.dp
    val S24 = 24.dp
    val S32 = 32.dp
    val Page = 16.dp
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
}
