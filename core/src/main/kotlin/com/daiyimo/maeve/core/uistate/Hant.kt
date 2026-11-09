package com.daiyimo.maeve.core.uistate

/**
 * 词表都是简体写的,所以繁体界面要先把繁体字换成简体再比:按钮、金额、提示语
 * (删除→刪除、结账→結帳)不换过就判不上。
 * 只收词表里用得到的字;词表加了新字,这里也要跟着加。
 * 两串字同样长,一个对一个,按下标结成对照表。
 */
object Hant {
    internal const val TRADITIONAL ="個買優餘內減刪動單歷發臺後唄塊復寶實對崗應錄總報機溝點現碼確簡紅約紋細結繳羣職聯獲計訂認記論設證評該說請賬貨購費賞車轉輸運進遞錢銀銷鍵預額馬驗帳説傳尋儲註覆佈繫"
    internal const val SIMPLIFIED = "个买优余内减删动单历发台后呗块复宝实对岗应录总报机沟点现码确简红约纹细结缴群职联获计订认记论设证评该说请账货购费赏车转输运进递钱银销键预额马验账说传寻储注复布系"
    private val MAP = TRADITIONAL.zip(SIMPLIFIED).toMap()

    /** 逐个字符换成简体;一个繁体字也没有时原样返回。 */
    fun fold(text: String): String {
        if (text.none(::isTraditional)) return text
        val folded = StringBuilder(text.length)
        for (c in text) folded.append(MAP[c] ?: c)
        return folded.toString()
    }

    /** 这个字在对照表里(是繁体字)。 */
    private fun isTraditional(c: Char) = c in MAP
}
