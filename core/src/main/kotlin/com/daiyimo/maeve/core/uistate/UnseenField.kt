package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.prompt.Feedback

/**
 * 往树里看不见的输入框里打字(App 自己画界面,树里没有输入框,它却可能拿着焦点):
 * 文字经剪贴板贴进去,读不回框里的内容,只能比对打字前后屏上读到的文字(OCR)。
 * 不要求输入法弹着:全选、粘贴键发给这块屏上正拿着焦点的窗口,拿着焦点的输入框不经输入法也会贴。
 */
object UnseenField {
    /** 焦点没在输入框上:先点框再打,别盲目重粘。 */
    private const val NO_FOCUS = "no_focus"
    /** 往一点上打字,贴完之后前后一点没变:那一点上不是能打字的框。 */
    const val POINT_NOT_TYPED = "point_not_typed"

    /**
     * 粘贴之后报什么。[keyboardUp]:粘贴前这块屏上弹着输入法(焦点确实在某个框上)。
     * 没弹输入法、前后屏上的字一点没变(MISMATCH):多半焦点不在框上,报 no_focus,请模型先点框,不再盲目重粘;
     * 弹着输入法时照旧交出核对结果(没打进去由输入工具报)。
     * [atPoint]:先点了模型给的一点再贴的;前后一点没变,就是那一点没接住字(不论弹没弹输入法),报 [POINT_NOT_TYPED],请模型重新找框,不自动重试。
     */
    fun typed(
        text: String, before: List<VisibleTextLine>?, after: List<VisibleTextLine>?, changed: Boolean, keyboardUp: Boolean, atPoint: Boolean = false,
    ): ActionResult {
        val outcome = verify(text, before, after, changed)
        if (outcome == Verification.MISMATCH && atPoint) return ActionResult.Failed(POINT_NOT_TYPED, Feedback["type.point_not_typed"])
        if (outcome == Verification.MISMATCH && !keyboardUp) return ActionResult.Failed(NO_FOCUS, Feedback["type.unseen_not_focused"])
        return ActionResult.Ok(changed = changed, verification = outcome, hint = Feedback["type.unseen_pasted"])
    }

    /** 全选再加删除键之后报什么:没弹输入法、界面一点没变时同 [typed],请模型先点框;其余照原样交出,并附上清空的做法。[atPoint] 同 [typed]。 */
    fun cleared(r: ActionResult, keyboardUp: Boolean, atPoint: Boolean = false): ActionResult = when {
        r !is ActionResult.Ok -> r
        r.changed == false && atPoint -> ActionResult.Failed(POINT_NOT_TYPED, Feedback["clear.point_not_cleared"])
        r.changed == false && !keyboardUp -> ActionResult.Failed(NO_FOCUS, Feedback["clear.unseen_not_focused"])
        else -> r.copy(hint = Feedback["clear.unseen_cleared"])
    }

    /** 点那一下就换到了 [pkg](点到链接、按钮):字没打,请模型回去重新找框。 */
    fun tapSwitched(pkg: String): ActionResult = ActionResult.Failed(POINT_NOT_TYPED, Feedback.format("type.point_switched", "pkg" to pkg))

    /** 要核对的字(去掉空白与标点后)超过这么长时,只看开头和结尾各 [PROBE_CHARS] 个字:长句子 OCR 常认错中间几个字。 */
    private const val NEEDLE_LONG = 16
    /** 长句掐头尾时,头尾各取这么多个字。 */
    private const val PROBE_CHARS = 6

    /**
     * 核对结果:打字后屏上这段字比打字前多出现一次,为 [Verification.MATCH];前后都读到了字、字一样、界面和画面也没动,为 MISMATCH(没打进去);
     * 其余(哪一次没读到字、变了却找不到这段字)为 UNKNOWN,不猜。[changed]:打字前后界面或画面有变化。
     */
    fun verify(typed: String, before: List<VisibleTextLine>?, after: List<VisibleTextLine>?, changed: Boolean): Verification {
        val needle = key(typed)
        if (after != null && before != null && needle.isNotEmpty()) {
            val afterText = joined(after); val beforeText = joined(before)
            if (moreOften(probes(needle), afterText, beforeText)) return Verification.MATCH
        }
        if (!changed && after != null && before != null && joined(after) == joined(before)) return Verification.MISMATCH
        return Verification.UNKNOWN
    }

    /** 短句整段拿去比;长句只掐头尾各一段,躲开 OCR 常认错的中间。 */
    private fun probes(needle: String): List<String> =
        if (needle.length <= NEEDLE_LONG) listOf(needle) else listOf(needle.take(PROBE_CHARS), needle.takeLast(PROBE_CHARS))

    /** 每段样本在 [afterText] 里出现的次数都严格多于 [beforeText]:这段字确实多出现了一次。 */
    private fun moreOften(samples: List<String>, afterText: String, beforeText: String): Boolean =
        samples.all { occurrences(afterText, it) > occurrences(beforeText, it) }

    /** 去掉中心落在 [box](0–999 的 [l,t,r,b],如输入法窗口)里的行:键盘上的字和候选词,不算框里的字。 */
    fun outside(lines: List<VisibleTextLine>, box: List<Int>?): List<VisibleTextLine> {
        if (box == null || box.size != 4) return lines
        return lines.filterNot { l ->
            l.bounds.size == 4 && (l.bounds[0] + l.bounds[2]) / 2 in box[0]..box[2] && (l.bounds[1] + l.bounds[3]) / 2 in box[1]..box[3]
        }
    }

    /** 从上到下、从左到右接起来;OCR 会把一句话拆成几行,也会认错标点和空格,故只比字母、数字和汉字。 */
    private fun joined(lines: List<VisibleTextLine>): String =
        lines.sortedWith(compareBy({ it.bounds.getOrElse(1) { 0 } }, { it.bounds.getOrElse(0) { 0 } })).joinToString("") { key(it.text) }

    /** 归一化:转小写,只留字母和数字(汉字算字母);标点空格滤掉,OCR 常认错它们。 */
    private fun key(s: String) = s.lowercase().filter(Char::isLetterOrDigit)

    /** [sample] 在 [text] 里出现了几次(不重叠地数)。 */
    private fun occurrences(text: String, sample: String): Int {
        var n = 0; var i = text.indexOf(sample)
        while (i >= 0) { n++; i = text.indexOf(sample, i + sample.length) }
        return n
    }
}
