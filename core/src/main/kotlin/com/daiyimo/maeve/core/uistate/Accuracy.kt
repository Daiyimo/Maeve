package com.daiyimo.maeve.core.uistate

import kotlin.math.hypot

/**
 * 用来在新一屏里认出「同一个元素」的特征。[context] 是遍历顺序里排在它前面最近的一个有名元素,
 * 免得滚动之后别处的同名按钮顶上来被误认;[hint]:名字是输入框的提示语/预置词(会轮换);
 * [inList]:元素在列表容器里(列表项会复用,同一位置可能换成别的条目)。
 */
data class ElementLocator(
    val role: String,
    val name: String,
    val resourceId: String?,
    val centerX: Int,
    val centerY: Int,
    val context: String?,
    val hint: Boolean = false,
    val inList: Boolean = false,
)

sealed interface Relocation {
    /** [index] 是它在新列表里的下标。 */
    data class Found(val index: Int) : Relocation
    /** 还在,只是上下文变了或挪得太远。 */
    data object Moved : Relocation
    /** 没名字也没资源 ID,或者有好几个一样的。 */
    data object Ambiguous : Relocation
    /** 新界面里不见了。 */
    data object Gone : Relocation
}

/** 找回时中心最多能挪这么多:屏幕短边的这个比例。 */
const val RELOCATE_MAX_SHIFT = 0.15

/** 算「还在原地」的偏移上限:屏幕短边的这个比例。 */
const val SAME_SPOT_SHIFT = 0.02

/**
 * 编号过期后,在 [after] 里给 [previous] 找唯一对应者。几条同时满足才算找到:类型、名字、资源 ID 相同,
 * 且在旧列表 [before] 和新列表里都只有这一个;上下文也得相同;中心挪动不超过短边的 [RELOCATE_MAX_SHIFT]。
 *
 * 页面整体没动(见 [stationary])时再放宽两条,应付轮播、倒计时这类只有局部在变的页面:
 * - 上下文变了,但元素还在原地、且不在列表容器里(列表项会复用),照样算找到;
 * - 名字变了,但新旧都有同一个资源 ID、类型相同、还在原地且原地只有它,而且名字只差数字和空白
 *   (「跳过 3」→「跳过 2」),或旧名字是输入框轮换的预置词(hint),也算找到。
 *   「关注」→「已关注」、「下一步」→「确认支付」这种换了含义的不算。
 */
fun relocate(previous: ElementLocator, before: List<ElementLocator>, after: List<ElementLocator>, shortSide: Int): Relocation {
    if (previous.name.isBlank() && previous.resourceId.isNullOrBlank()) return Relocation.Ambiguous
    val spot = shortSide * SAME_SPOT_SHIFT
    val still by lazy { stationary(before, after, spot) }
    val candidates = after.withIndex().filter { (_, e) -> sameIdentity(e, previous) }
    if (candidates.isEmpty()) return renamedInPlace(previous, after, spot) { still }
    if (candidates.size > 1 || before.count { sameIdentity(it, previous) } > 1) return Relocation.Ambiguous
    val (index, found) = candidates.single()
    val moved = centerShift(found, previous)
    if (moved > shortSide * RELOCATE_MAX_SHIFT) return Relocation.Moved
    if (!inPlace(found, previous, moved, spot, still)) return Relocation.Moved
    return Relocation.Found(index)
}

/** 上下文没变;或页面没动而元素还在原地、且不在列表容器里(列表项会复用)。 */
private fun inPlace(found: ElementLocator, previous: ElementLocator, moved: Double, spot: Double, still: Boolean): Boolean =
    found.context == previous.context || still && moved <= spot && !previous.inList

/** 两个元素是不是同一个:角色、名字、资源 ID 都相同。 */
private fun sameIdentity(a: ElementLocator, b: ElementLocator) =
    a.role == b.role && a.name == b.name && a.resourceId == b.resourceId

/**
 * 名字对不上了:页面没动、原地只有一个同类型同资源 ID 的控件,且名字只是轮换或数字变化时,算找到了。
 * 没有资源 ID、页面在动、原地不止一个,或名字换了含义,都是 [Relocation.Gone]。
 */
private fun renamedInPlace(previous: ElementLocator, after: List<ElementLocator>, spot: Double, still: () -> Boolean): Relocation {
    if (previous.resourceId.isNullOrBlank() || !still()) return Relocation.Gone
    val inPlace = after.withIndex().filter { (_, e) ->
        e.role == previous.role && e.resourceId == previous.resourceId && centerShift(e, previous) <= spot
    }
    val found = inPlace.singleOrNull() ?: return Relocation.Gone
    val alike = previous.hint || withoutDigits(found.value.name) == withoutDigits(previous.name)
    return if (alike) Relocation.Found(found.index) else Relocation.Gone
}

/** 去掉数字和空白:「跳过 3」和「跳过 2」只差这些。 */
private fun withoutDigits(s: String) = s.filterNot { it.isDigit() || it.isWhitespace() }

/** 两个元素中心相距多远(像素)。 */
private fun centerShift(a: ElementLocator, b: ElementLocator) =
    hypot((a.centerX - b.centerX).toDouble(), (a.centerY - b.centerY).toDouble())

/**
 * 页面整体没动:前后都只出现一次的有名元素至少 2 个(开屏页这类页面元素很少),且其中八成以上还在原地。
 * 列表滚动、换页时大部分元素都挪了位置或不见了,就不算。
 */
internal fun stationary(before: List<ElementLocator>, after: List<ElementLocator>, spot: Double): Boolean {
    fun key(e: ElementLocator) = Triple(e.role, e.name, e.resourceId)
    val afterByKey = after.filter { it.name.isNotBlank() }.groupBy(::key)
    var shared = 0
    var inPlace = 0
    for ((k, list) in before.filter { it.name.isNotBlank() }.groupBy(::key)) {
        val a = afterByKey[k]?.singleOrNull() ?: continue
        if (list.size != 1) continue
        shared++
        if (centerShift(list[0], a) <= spot) inPlace++
    }
    return shared >= 2 && inPlace >= shared * 0.8
}

/** 输入框的读写:[read] 返回用户此刻看到的实际文字(显示的是提示语时为空串),读不出(密码框、节点已不在)为 null。 */
interface TextField {
    fun read(): String?
    fun write(text: String): Boolean
    /** 第一次写入后读回不一致时,换一条通道再写;没有另一通道的字段默认照原方式再写一次。 */
    fun retry(text: String): Boolean = write(text)
}

/** 比较前把空白全去掉:输入框可能自己给号码分组加空格。 */
private fun withoutSpaces(s: String) = s.filterNot { it.isWhitespace() }

/**
 * 往 [field] 里写入 [text] 并读回核对,不一致就重试,最多 [attempts] 次;[settle] 在每次写入后等输入框更新。
 * 只认「写入后读到的文字恰好是目标」;写入前就已经是目标时先清空——清不掉就分不清写没写进去,返回 UNKNOWN。
 * [text] 给空串表示清空,这时读回为空即 MATCH。
 * 有的输入框对无障碍一直报同一段文字(如提示语),读回永远对不上:每次读回都和写入前一样、
 * 至少有一次写入成功、而 [reacted] 说界面在输入后出现了含所输文字的新文字(联想词等)时,
 * App 其实收到了输入,这时返回 UNKNOWN,不算 MISMATCH。
 * [secret](密码框)只写一次、不重试、不读回:密码框读不回原文,重试只是把同一段字又往框里写一遍。
 */
suspend fun typeAndVerify(
    field: TextField,
    text: String,
    attempts: Int = 2,
    settle: suspend () -> Unit = {},
    reacted: () -> Boolean = { false },
    secret: Boolean = false,
): Verification {
    if (secret) return blindWrite(field, text)
    val want = withoutSpaces(text)
    var baseline = field.read() ?: return blindWrite(field, text)
    if (want.isNotEmpty() && withoutSpaces(baseline) == want) {
        val cleared = field.write("")
        settle()
        baseline = field.read() ?: return if (cleared) Verification.UNKNOWN else Verification.MISMATCH
        if (withoutSpaces(baseline) == want) return blindWrite(field, text)
    }
    var stuck = true
    var wrote = false
    repeat(attempts) { round ->
        val accepted = field.attemptWrite(text, round)
        if (!accepted) return@repeat
        wrote = true
        settle()
        val actual = field.read() ?: return Verification.UNKNOWN
        if (withoutSpaces(actual) == want) return Verification.MATCH
        if (actual != baseline) stuck = false
    }
    return if (wrote && stuck && reacted()) Verification.UNKNOWN else Verification.MISMATCH
}

/** 只写一次、无从核对:写入被接受算 UNKNOWN,被拒绝算 MISMATCH。 */
private fun blindWrite(field: TextField, text: String): Verification =
    if (field.write(text)) Verification.UNKNOWN else Verification.MISMATCH

/** 第 [round] 次写入:第一次直接写,之后走换通道重试。 */
private fun TextField.attemptWrite(text: String, round: Int): Boolean =
    if (round == 0) write(text) else retry(text)
