package com.daiyimo.maeve.core.uistate

import kotlin.math.hypot

/**
 * 包含匹配这一层,对上的名字最多只能是描述长度的这么多倍;再长就说明描述只是这一大段里的一小截
 * (比如列表容器把整段文字拼成了自己的名字),点它的中心点不到描述指的地方,不能算对上。
 */
private const val CONTAINED_NAME_RATIO = 4

/**
 * 不靠编号、凭描述指一个元素:[text] 是它显示的字或无障碍名字(打字时也可以是输入框的提示语),[role] 可选,
 * [field] 为真时只找输入框(打字用)。
 */
data class ElementQuery(val text: String, val role: String? = null, val field: Boolean = false)

sealed interface DescribedMatch {
    /** [exact] 为真时名字(或提示语)和描述一字不差;否则是按开头或包含找到的,结果里要说清实际对上的是谁。 */
    data class Found(val element: UiElement, val exact: Boolean) : DescribedMatch
    /** 对得上的不止一个;[candidates] 里能操作的排在前,其余按读屏顺序。 */
    data class Ambiguous(val candidates: List<UiElement>) : DescribedMatch
    /** 一个都对不上。 */
    data object None : DescribedMatch
}

/**
 * 在一屏里按描述找出唯一的元素。比较前统一去掉空白、转成小写。按从严到宽分层找,哪一层先有结果就用哪一层:
 * 名字(或提示语)完全相同 → 名字以描述开头 → 名字包含描述(这一层只认能操作的元素,免得一段正文里碰巧含这几个字)。
 * 同一层里能操作的(打字看能不能输入,其余看能不能点;页面标了不可用的不算)优先:恰好一个就是它;
 * 能操作的有好几个、或一个能操作的都没有却有好几个对上,都算 [DescribedMatch.Ambiguous],不猜。
 * 给了 [ElementQuery.role] 时只看这个角色。包含匹配还对对上的名字有长度上限(见 [CONTAINED_NAME_RATIO]):
 * 名字只比描述略长一点才算对上。
 */
object DescribedTarget {
    fun match(elements: List<UiElement>, q: ElementQuery): DescribedMatch {
        val want = norm(q.text)
        if (want.isEmpty()) return DescribedMatch.None
        val pool = elements.filter { inScope(it, q) }
        val tiers = listOf({ s: String -> s == want }, { s: String -> s.startsWith(want) }, { s: String -> want in s })
        for ((i, test) in tiers.withIndex()) {
            val contained = i == 2
            var hits = pool.filter { e -> labels(e, q.field).any(test) && (!contained || usable(e, q.field)) }
            // 包含匹配里名字远长于描述的:描述只是这一大段里的一小截(如把整段列表文字拼成名字的容器),
            // 点它的中心点不着描述指的地方。这种不算对上,留给看图兜底或换个说法。
            if (contained) hits = hits.filter { e -> labels(e, q.field).filter(test).all { fits(it, want.length) } }
            if (hits.isEmpty()) continue
            val actionable = hits.filter { usable(it, q.field) }
            return when {
                actionable.size == 1 -> DescribedMatch.Found(actionable[0], exact = i == 0)
                actionable.isEmpty() && hits.size == 1 -> DescribedMatch.Found(hits[0], exact = i == 0)
                else -> DescribedMatch.Ambiguous(actionable + (hits - actionable.toSet()))
            }
        }
        return DescribedMatch.None
    }

    /** 候选范围:打字只要输入框;给了角色时还要角色对得上(不区分大小写)。 */
    private fun inScope(e: UiElement, q: ElementQuery): Boolean =
        (!q.field || e.editable) && (q.role == null || e.role.equals(q.role.trim(), ignoreCase = true))

    /** 包含匹配的长度上限:名字不能超过描述长度的 [CONTAINED_NAME_RATIO] 倍。 */
    private fun fits(name: String, want: Int): Boolean = name.length <= want * CONTAINED_NAME_RATIO

    /** 能操作:打字看能不能输入,其余看能不能点或输入;页面标了不可用的不算。 */
    fun usable(e: UiElement, field: Boolean): Boolean =
        UiStates.DISABLED !in e.states && (if (field) e.editable else e.clickable || e.editable)

    /** 离 [e] 最近的一段有名字的字(和它不同名):候选同名时靠它分辨是哪一个。 */
    fun neighbour(e: UiElement, elements: List<UiElement>): String? = elements
        .filter { apart(it, e) }
        .minByOrNull { apartBy(it, e) }?.name

    /** 另一段字:不是它自己、有名字、且名字和它不同名。 */
    private fun apart(other: UiElement, e: UiElement): Boolean =
        other !== e && other.name.isNotBlank() && norm(other.name) != norm(e.name)

    /** 两段字的中心相距多远(0–999 坐标里)。 */
    private fun apartBy(other: UiElement, e: UiElement): Double =
        hypot((other.centerX - e.centerX).toDouble(), (other.centerY - e.centerY).toDouble())

    /** 元素能被描述的字:名字;输入框还有它自己的提示语。 */
    private fun labels(e: UiElement, field: Boolean): List<String> =
        listOfNotNull(e.name, e.placeholder.takeIf { field || e.editable }).map(::norm).filter { it.isNotEmpty() }

    /** 比较用的样子:去掉空白、转小写。 */
    private fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()
}
