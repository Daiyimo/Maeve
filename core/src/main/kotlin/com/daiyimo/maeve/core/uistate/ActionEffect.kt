package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.visual.FrameChange
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.tool.Verb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 一个动作对 App 或网页界面状态的实际效果:看动作前后的界面状态(元素表、控件状态、输入焦点、在哪)判出来,
 * 画面差异只作旁证;[wire] 是写进结果 effect.kind 的值。
 */
enum class EffectKind(val wire: String) {
    /** 界面明显变了,且和这个动作对得上。 */
    Done("done"),
    /** 只有小的局部变化(焦点、开关、一两个元素),也可能还在加载:得再看一眼结果。 */
    SmallChange("small_change"),
    /** 什么都没变:换个做法,别原样重来。 */
    NoEffect("no_effect"),
    /** 换了地方:手机上前台换成另一个 App,网页上换了一页(地址或标签变了);新地方见 [NewPlace]。 */
    PlaceChanged("place_changed");

    companion object {
        /** 按结果里的 effect.kind 找回种类;不认识为 null。 */
        fun of(wire: String?): EffectKind? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * 动作前后两屏元素表的结构差异。元素按内容对齐(角色 + 名字 + 资源 ID + 是否提示语),编号不参与
 * (各次读屏的编号不通用):同内容同位置同状态算没变;只有控件状态([UiElement.states])变了算 [toggled];
 * 同内容换了位置算 [moved](滚动、重排);同一个输入框里换了字算 [edited];剩下的算 [added] / [removed]。
 * [focusMoved] 表示有输入焦点的元素换了(或焦点出现、消失)。只数有信息量的元素([UiElement.meaningful])。
 */
data class InterfaceDiff(
    val added: Int,
    val removed: Int,
    val moved: Int,
    val edited: Int,
    val toggled: Int,
    val focusMoved: Boolean,
    val total: Int,
) {
    /** 变化的总条数。 */
    val changes: Int get() = added + removed + moved + edited + toggled
    /** 一点没变:没有增减挪改,焦点也没动。 */
    val none: Boolean get() = changes == 0 && !focusMoved

    /** 变化够多(门槛见 [EffectTuning]):至少 [EffectTuning.MAJOR_MIN] 条,或占到 [total] 的 [EffectTuning.MAJOR_SHARE]。 */
    val major: Boolean get() = changes >= max(EffectTuning.MAJOR_MIN, ceil(total * EffectTuning.MAJOR_SHARE).toInt())

    companion object {
        /** 前后两屏元素表的结构差异。 */
        fun between(before: ScreenSnapshot, after: ScreenSnapshot): InterfaceDiff {
            val a = before.elements.filter { it.meaningful }
            val b = after.elements.filter { it.meaningful }
            // 按宽松程度依次配对:全同 → 只差状态 → 挪了位置 → 同一个输入框换了字
            val leftA = a.toMutableList()
            val leftB = b.toMutableList()
            pairUp(leftA, leftB, ::identical)
            val toggled = pairUp(leftA, leftB, ::sameSpot)
            val moved = pairUp(leftA, leftB, ::sameContent)
            val edited = pairUp(leftA, leftB, ::sameBox)
            val focusA = a.firstOrNull { it.focused }?.let(::positionOf)
            val focusB = b.firstOrNull { it.focused }?.let(::positionOf)
            return InterfaceDiff(leftB.size, leftA.size, moved, edited, toggled, focusA != focusB, max(a.size, b.size))
        }

        /** 按 [match] 两两配对,配上的从两边都移走,返回配上的对数。 */
        private fun pairUp(a: MutableList<UiElement>, b: MutableList<UiElement>, match: (UiElement, UiElement) -> Boolean): Int {
            var paired = 0
            val left = a.iterator()
            while (left.hasNext()) {
                val x = left.next()
                val j = b.indexOfFirst { y -> match(x, y) }
                if (j < 0) continue
                b.removeAt(j)
                left.remove()
                paired++
            }
            return paired
        }

        /** 内容、位置、状态全都一样。 */
        private fun identical(x: UiElement, y: UiElement) = identity(x) == identity(y) && x.bounds == y.bounds && x.states == y.states

        /** 内容、位置一样,只有控件状态变了(拨了开关)。 */
        private fun sameSpot(x: UiElement, y: UiElement) = identity(x) == identity(y) && x.bounds == y.bounds

        /** 内容一样,位置变了(滚动、重排)。 */
        private fun sameContent(x: UiElement, y: UiElement) = identity(x) == identity(y)

        /** 同一个输入框:都能输入、同角色同资源 ID、位置相近。 */
        private fun sameBox(x: UiElement, y: UiElement) =
            x.editable && y.editable && x.role == y.role && x.resourceId == y.resourceId && sameField(x, y)

        /** 元素的内容:角色、名字、资源 ID、是否提示语。 */
        private fun identity(e: UiElement) = listOf(e.role, e.name, e.resourceId.orEmpty(), e.hint.toString())
        /** 元素的位置:角色、资源 ID、边界。 */
        private fun positionOf(e: UiElement) = listOf(e.role, e.resourceId.orEmpty(), e.bounds.toString())

        /** 两个输入框算同一个:边界都读得到时中心相距不超过 [EffectTuning.NEAR],读不到时看资源 ID 在不在。 */
        private fun sameField(x: UiElement, y: UiElement): Boolean {
            if (x.bounds.size != 4 || y.bounds.size != 4) return x.resourceId != null
            return centerDistance(x, y) <= EffectTuning.NEAR
        }
    }
}

/**
 * 动作之后到的新地方:[name] 是通用的说法(App 包名、网站主机名,和读界面时的「在哪」一致),
 * [kind] 和 [detail] 是这种界面自己的细节(手机 "app":{package};网页 "web":{url,title,tabId})。
 */
data class NewPlace(val name: String, val kind: String, val detail: JsonObject)

/**
 * 输入之后读回的输入框:[text] 是此刻框里的字(显示的是提示语时为空串;密码框为 null,不读出),
 * [matches] 是否和要输的一致(比较时去掉空白:有的框会给号码分组加空格)。
 */
data class Readback(val text: String?, val secret: Boolean, val matches: Boolean)

/**
 * 判效果时来自端口的事实:[changed] 是端口在动作里核实过的变没变;[switchedTo] 是端口看到的前台换成的 App;
 * [verified] 是输入写完当场读回一致;[loading] 是端口说页面还没静下来(网络、导航还在途);
 * [onUserScreen] 是这个 App 被开到了用户的主屏上并留在那里(用户正用着它),这时 Maeve 的屏上看不出变化也不等于没点上。
 */
data class PortFacts(
    val changed: Boolean? = null,
    val switchedTo: String? = null,
    val verified: Boolean = false,
    val loading: Boolean = false,
    val onUserScreen: String? = null,
)

/**
 * 一个动作的实际效果:种类、去了哪(换地方时)、界面结构与画面的差异、输入的读回、可能还在加载。
 * [treeBlind]:没看到变化(种类仍是 [EffectKind.NoEffect]),但这一屏的树读不出这个动作作用的那一处
 * (树不能用,或缺了一块而点不在任何元素上),只改了焦点、隐藏状态的点看起来也一样,这时不能说没点上。
 */
data class ActionEffect(
    val kind: EffectKind,
    val place: NewPlace? = null,
    val diff: InterfaceDiff? = null,
    val frame: FrameChange? = null,
    val readback: Readback? = null,
    val loading: Boolean = false,
    val treeBlind: Boolean = false,
    /**
     * 点击之后视觉模型核对出来的界面变化:page(换了页面)、minor(只有小的局部变化)、none(什么都没变)。
     * 树说变化够多而视觉说没变或只有小变化时,已据此把种类改判(见 [ScreenSession] 的视觉核查)。
     */
    val visionCheck: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("kind", kind.wire)
        place?.let { put("to", it.name); put(it.kind, it.detail) }
        diff?.let { d ->
            if (d.added > 0) put("added", d.added)
            if (d.removed > 0) put("removed", d.removed)
            if (d.moved > 0) put("moved", d.moved)
            if (d.edited > 0) put("edited", d.edited)
            if (d.toggled > 0) put("toggled", d.toggled)
            if (d.focusMoved) put("focusMoved", true)
        }
        frame?.let { put("frameChanged", (it.whole * 100).roundToInt() / 100.0) }
        readback?.let { r ->
            put("readback", if (r.secret) HIDDEN else r.text?.take(READBACK_MAX))
            put("readbackMatches", r.matches)
        }
        if (loading) put("mayStillBeLoading", true)
        if (treeBlind) put("treeBlind", true)
        visionCheck?.let { put("visionCheck", it) }
    }

    companion object {
        /** 结果里读回的占位:密码框不读出原文。 */
        const val HIDDEN = "(hidden)"
        private const val READBACK_MAX = 200

        /** 看不出变化也不能说没点上的动作:点和手势(只改焦点、隐藏状态时,树和画面都可能不动)。 */
        private val BLIND_VERBS = setOf(Verb.Tap, Verb.PhoneGesture)

        /**
         * 这一屏的树读不出这个动作作用的那一处:树不能用,或缺了一块而点(画面区域 [region] 的中心)
         * 不在任何有信息量的元素上。按树里的元素点的([target] 不为 null)只在树不能用时算。
         * [quality] 是这一屏最近一次判的树够不够用,不知道为 null。
         */
        internal fun treeBlind(verb: Verb, quality: TreeQuality?, before: ScreenSnapshot?, target: UiElement?, region: List<Int>?): Boolean {
            if (verb !in BLIND_VERBS) return false
            return when (quality) {
                TreeQuality.Unusable -> true
                TreeQuality.Partial -> {
                    if (target != null || region == null || region.size != 4) return false
                    val x = (region[0] + region[2]) / 2
                    val y = (region[1] + region[3]) / 2
                    before?.elements.orEmpty().none { e -> e.meaningful && covers(e, x, y) }
                }
                else -> false
            }
        }

        /** 元素罩住了 (x, y) 这个点(0–999 坐标里)。 */
        private fun covers(e: UiElement, x: Int, y: Int): Boolean =
            e.bounds.size == 4 && x in e.bounds[0]..e.bounds[2] && y in e.bounds[1]..e.bounds[3]

        /**
         * 按动作前后的界面状态判效果。[before] / [after] 读不到时只按端口的事实和画面判;两样都没有为 null(不报效果)。
         * [target] 是动作的目标元素(模型读到的那份里的,用来找输入框);输入([Verb.TypeText])带 [typed](要输的字),
         * [pressedEnter] 时不读回(回车之后框可能清空或已换页)。
         *
         * 拿不准时不说没效果:端口说变了就至少是小变化;界面在动或端口说还在加载时也只算小变化(可能还在加载)。
         */
        fun classify(
            verb: Verb,
            before: ScreenSnapshot?,
            after: ScreenSnapshot?,
            port: PortFacts,
            frame: FrameChange?,
            target: UiElement? = null,
            typed: String? = null,
            pressedEnter: Boolean = false,
            secret: Boolean = false,
        ): ActionEffect? {
            // 端口自己看到前台换了 App:以它为准(两次读可能都在切换之前或之后)
            port.switchedTo?.let { return placeChanged(it, frame, onUserScreen = false) }
            // 开到了用户的主屏上:换了地方,只是不在 Maeve 的屏上
            port.onUserScreen?.let { return placeChanged(it, frame, onUserScreen = true) }
            val judged = judge(verb, before, after, port, frame, target, typed, pressedEnter, secret) ?: return null
            val loading = stillLoading(port, after)
            return when {
                judged.kind != EffectKind.NoEffect -> judged.copy(loading = loading && judged.kind == EffectKind.SmallChange)
                loading -> judged.copy(kind = EffectKind.SmallChange, loading = true)
                port.changed == true -> judged.copy(kind = EffectKind.SmallChange)
                else -> judged
            }
        }

        /** 端口说还在加载、界面在读的时候又变了,或屏上有进度条:都算可能还没静下来。 */
        private fun stillLoading(port: PortFacts, after: ScreenSnapshot?): Boolean =
            port.loading || after?.changedDuringRead == true || after?.let(::showsProgress) == true

        /** 前台换成了另一个 App(或开到了用户主屏上):都算换了地方。 */
        private fun placeChanged(pkg: String, frame: FrameChange?, onUserScreen: Boolean): ActionEffect = ActionEffect(
            EffectKind.PlaceChanged,
            NewPlace(pkg, "app", buildJsonObject { put("package", pkg); if (onUserScreen) put("onUserScreen", true) }),
            frame = frame,
        )

        private fun judge(
            verb: Verb, before: ScreenSnapshot?, after: ScreenSnapshot?, port: PortFacts, frame: FrameChange?,
            target: UiElement?, typed: String?, pressedEnter: Boolean, secret: Boolean,
        ): ActionEffect? {
            if (before == null || after == null) return portOnly(port, frame)
            moved(before, after)?.let { return ActionEffect(EffectKind.PlaceChanged, it, frame = frame) }
            val diff = InterfaceDiff.between(before, after)
            val readback = when {
                verb != Verb.TypeText && verb != Verb.ClearText -> null
                pressedEnter -> null
                else -> readback(after, target, if (verb == Verb.ClearText) "" else typed, secret)
            }
            val kind = when {
                readback != null && !readback.secret && readback.matches -> EffectKind.Done
                // 端口写完当场读回过、一致,而这份界面里找不到那个框(或是密码框):按端口的核对算
                port.verified && (readback == null || readback.secret) && !pressedEnter -> EffectKind.Done
                readback != null && !readback.secret -> if (diff.none && frame?.visible != true) EffectKind.NoEffect else EffectKind.SmallChange
                diff.major -> EffectKind.Done
                diff.none -> when {
                    sparseScreen(before, after, frame) -> EffectKind.Done
                    frame?.visible == true -> EffectKind.SmallChange
                    else -> EffectKind.NoEffect
                }
                else -> EffectKind.SmallChange
            }
            return ActionEffect(kind, diff = diff, frame = frame, readback = readback)
        }

        /** 画面大变,而前后两屏有信息量的元素都很少(自绘界面):整页换了内容也算明显变了。 */
        private fun sparseScreen(before: ScreenSnapshot, after: ScreenSnapshot, frame: FrameChange?): Boolean =
            frame?.large == true && max(before.elements.count { it.meaningful }, after.elements.count { it.meaningful }) < EffectTuning.FEW_ELEMENTS

        /** 读不到前后两屏时,只按端口的事实和画面判。 */
        private fun portOnly(port: PortFacts, frame: FrameChange?): ActionEffect? = when {
            port.changed == true || frame?.visible == true -> ActionEffect(EffectKind.SmallChange, frame = frame)
            port.changed == false -> ActionEffect(EffectKind.NoEffect, frame = frame)
            else -> null
        }

        /** 界面上有没有进度条一类的控件(在加载)。 */
        private fun showsProgress(s: ScreenSnapshot) = s.elements.any { it.role.contains("progress", ignoreCase = true) && UiStates.DISABLED !in it.states }

        /** 换了 App 或换了一页:网页看地址(不算 # 之后)和标签,手机看前台包名。都没换为 null。 */
        private fun moved(before: ScreenSnapshot, after: ScreenSnapshot): NewPlace? {
            val webBefore = before.web; val webAfter = after.web
            if (webBefore != null || webAfter != null) {
                if (webBefore == null || webAfter == null) return null
                if (webBefore.tabId != webAfter.tabId || webBefore.url.substringBefore('#') != webAfter.url.substringBefore('#')) {
                    return NewPlace(after.packageName ?: webAfter.url, "web", buildJsonObject { put("url", webAfter.url); put("title", webAfter.title); put("tabId", webAfter.tabId) })
                }
                return null
            }
            val pkg = after.packageName ?: return null
            return if (before.packageName != null && before.packageName != pkg) NewPlace(pkg, "app", buildJsonObject { put("package", pkg) }) else null
        }

        /**
         * 此刻输入框里的字:先找同一个框(同角色同资源 ID、位置相近),再找有焦点的输入框;找不到为 null(不报读回)。
         * [want] 是应有的字(清空时为空串);没给为 null 时不读回。
         */
        private fun readback(after: ScreenSnapshot, target: UiElement?, want: String?, secret: Boolean): Readback? {
            want ?: return null
            val inputs = after.elements.filter { it.editable }
            val box = target?.let { t ->
                inputs.filter { it.role == t.role && it.resourceId == t.resourceId }.minByOrNull { centerDistance(it, t) }
                    ?.takeIf { centerDistance(it, t) <= EffectTuning.SAME_FIELD }
            } ?: inputs.firstOrNull { it.focused } ?: return null
            if (secret || box.secret) return Readback(null, secret = true, matches = false)
            val text = if (box.hint) "" else box.name
            return Readback(text, secret = false, matches = withoutSpaces(text) == withoutSpaces(want))
        }

        /** 比较前把空白去掉:有的框会给号码分组加空格。 */
        private fun withoutSpaces(s: String) = s.filterNot { it.isWhitespace() }
    }
}

/** 两个元素中心相距多远(0–999 坐标里);缺范围为 0。 */
private fun centerDistance(a: UiElement, b: UiElement): Int {
    if (a.bounds.size != 4 || b.bounds.size != 4) return 0
    val dx = middle(a.bounds[0], a.bounds[2]) - middle(b.bounds[0], b.bounds[2])
    val dy = middle(a.bounds[1], a.bounds[3]) - middle(b.bounds[1], b.bounds[3])
    return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toInt()
}

/** 边界两端的中间值(0–999 坐标里)。 */
private fun middle(low: Int, high: Int) = (low + high) / 2

/** 按效果给模型的一句话(prompts/feedback/tools.md 里的 effect.*);明显变了不另说。 */
internal object EffectFeedback {
    /** [portSwitched]:端口已报过前台被切走(结果里已有说明),不再重复。 */
    fun hint(e: ActionEffect, verb: Verb, portSwitched: Boolean): String? {
        val differs = readbackDiffers(e)
        return when (e.kind) {
            EffectKind.Done -> null
            EffectKind.PlaceChanged -> if (portSwitched) null else Feedback.format("effect.place_changed", "to" to e.place?.name)
            EffectKind.SmallChange -> differs ?: Feedback[if (e.visionCheck == "minor") "effect.vision_minor" else if (e.loading) "effect.loading" else "effect.small_change"]
            EffectKind.NoEffect -> listOfNotNull(
                differs,
                Feedback["effect.vision_no_change"].takeIf { e.visionCheck == "none" },
                Feedback[when {
                    e.treeBlind -> "effect.no_visible_change"
                    verb == Verb.Back -> "effect.no_effect.back"
                    verb == Verb.Scroll || verb == Verb.Swipe -> "effect.no_effect.scroll"
                    else -> "effect.no_effect"
                }],
            ).joinToString(" ")
        }
    }

    /** 读回和要输的不一致时的那句话;一致、读不到或没读回都没有。 */
    private fun readbackDiffers(e: ActionEffect): String? = e.readback
        ?.takeIf { !it.secret && !it.matches }
        ?.let { Feedback.format("effect.readback_differs", "readback" to JsonPrimitive(it.text.orEmpty()).toString()) }
}
