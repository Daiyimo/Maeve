package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.visual.FrameDiff
import com.daiyimo.maeve.core.visual.FrameGrid
import com.daiyimo.maeve.core.visual.Recall
import com.daiyimo.maeve.core.visual.Shots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt
import com.daiyimo.maeve.core.vision.LocateRegistry
import com.daiyimo.maeve.core.vision.Locator
import com.daiyimo.maeve.core.vision.ScreenChange
import com.daiyimo.maeve.core.vision.TapVisionCheck
import com.daiyimo.maeve.core.vision.VisionService
import com.daiyimo.maeve.core.observation.InputHints
import com.daiyimo.maeve.core.observation.Look
import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.Place
import com.daiyimo.maeve.core.observation.Trust
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.tool.HINT_PROTECTED
import com.daiyimo.maeve.core.tool.MAX_READ_CHARS
import com.daiyimo.maeve.core.tool.NOTE_CHANGED_DURING_READ
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.okObj
import com.daiyimo.maeve.core.net.UrlHost

internal const val MAX_ELEMENTS = 200
/** 一次全量屏幕之后最多再接几次差异。 */
private const val MAX_DIFF_CHAIN = 5
/** 写操作之后、读屏之前,等界面稳定的上限。 */
private const val INLINE_SETTLE_MS = 1500L
/** OCR 只是读屏的补充,不能无限期挡着下一次模型调用。 */
private const val OCR_WAIT_MS = 2000L
/** 树不够用时屏上文字就是主要材料,多等一会儿(端口要先等画面停稳再认)。 */
private const val OCR_WAIT_POOR_MS = 5000L
/** 不可逆判定要按 ref 所属的那次读屏找元素,最近几次要留着。 */
private const val RECENT_READS = 8
/** 最多记住多少个发出过的 snapshotId。 */
private const val ISSUED_IDS_MEMORY = 256
/** 结果里「到上限了」那句话,最多留这么长。 */
private const val LIMIT_NOTE_MAX = 120

/** find_visually 找到的一个点:找的是什么、给的原文、视觉模型看到的字;不可逆判定时都要看。[unverified]:只靠看图、没有佐证。 */
internal class Located(val target: String, val text: String?, val seen: String?, val unverified: Boolean = false)

/**
 * 一块屏的读屏状态:稳定编号、模型最近拿到的那一屏与差异链、最近几次读屏、看图找到的点。每块屏一份,
 * 那一套屏幕工具只用自己的这份;画面编号与回看则由几块屏共用([shots])。
 */
internal class ScreenSession(
    val surface: Surface,
    stableRefs: Boolean,
    private val visionEnabled: Boolean,
    private val recordFrames: Boolean,
    /** 最近一次读到的界面文字(几块屏共用);动作之后读到的若与它一样,就不再记一遍。 */
    private val lastRead: () -> String?,
    val shots: Shots,
    vision: VisionService? = null,
    private val adaptiveVision: Boolean = false,
    /** 点击前后的视觉核查(点前查落点、点后查界面变化);没有视觉模型、或视觉核查关着时为 null。 */
    private val tapVisionCheck: TapVisionCheck? = null,
) {
    /** 手机模型的结果不按元素数量截断;网页仍沿用自己的输出预算。 */
    private val modelElementsLimit get() = if (surface.id == "phone") Int.MAX_VALUE else MAX_ELEMENTS
    val screen: ScreenPort get() = surface.screen
    private var textOnlyDepth = 0

    /** 复合手机命令内部只留文字与状态;图片要时再由模型请求。 */
    suspend fun <T> textOnly(block: suspend () -> T): T {
        textOnlyDepth++
        try { return block() } finally { textOnlyDepth-- }
    }

    val stabilizer = if (stableRefs) RefStabilizer(surface.family.snapshotPrefix) else null
    /** 模型最近一次拿到的那一屏(observe 或写操作结果里的 screen),差异就基于它算。 */
    private var handed: ModelSnapshot? = null
    /** 最近一次全量屏幕,加上其后各次差异的 snapshotId:差异要靠这整条链才看得懂,缺一条就发全量。 */
    private val diffChain = mutableListOf<String>()
    /** 模型此刻还看得到的读屏 snapshotId;null 表示循环还没告诉过(那就都看得到)。 */
    var visible: Set<String>? = null
    var lastSnapshot: ScreenSnapshot? = null
    val recentSnapshots = ArrayDeque<ScreenSnapshot>()
    val readAt = mutableMapOf<String, Long>()
    /** 这块屏发出过的 snapshotId(比 [RECENT_READS] 长:模型可能拿很早的编号来用)。 */
    private val issuedIds = object : LinkedHashMap<String, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > ISSUED_IDS_MEMORY
    }

    /** find_visually 在这块屏上找到的点(@vN);这块屏上任何写操作之后清空。 */
    val located = LocateRegistry()
    val locatedInfo = mutableMapOf<String, Located>()
    /** 上次写操作之后看过 describe:这期间的坐标点击,按看图得来的点对待。 */
    var described = false
    val locator = vision?.let { Locator(screen, it, located, surface.family) }

    fun clearLocated() { located.clear(); locatedInfo.clear(); described = false }

    private var accessSeen = screen.appAccess.current().revision

    suspend fun checkAppAccess(current: Boolean = true) {
        val state = screen.appAccess.current()
        if (state.revision != accessSeen) {
            clearLocated()
            clearPrimed()
            lastSnapshot = null
            recentSnapshots.clear()
            readAt.clear()
            handed = null
            diffChain.clear()
            stale = true
            accessSeen = state.revision
        }
        if (current) screen.checkAppAccess()
    }

    /** 这个 snapshotId(读屏的,或看图找到的点的)是不是这块屏发出的。 */
    fun owns(snapshotId: String): Boolean = snapshotId in issuedIds || located.has(snapshotId)

    /** 这块屏读过几次屏(登记编号的那些);判效果时据此知道动作之后有没有读过。 */
    var reads = 0
        private set

    /**
     * 模型手里的最近一屏已经过时:动作之后界面变了,结果里却没有带回动作之后的屏幕。下次读屏之前一直为 true。
     */
    var stale = false
        private set

    /** Maeve 自己动过这块屏(停留里轻轻滚了几下):模型手里那一屏的位置已不准,标成过时。 */
    fun movedByMaeve() { stale = true }

    /**
     * 读一屏并登记编号。[internal]:不是给模型看的(按描述找目标时),模型手里那屏过没过时照旧;
     * 端口原样的这一屏留作紧接着那个动作的「动作前」([probe] 用它,不再读一次)。
     */
    suspend fun snapshot(internal: Boolean = false): ScreenSnapshot = screen.snapshot().let { portSnap ->
        (stabilizer?.next(portSnap)?.screen ?: portSnap).also { primedRead = if (internal) it.snapshotId to portSnap else null }
    }.also {
        reads++
        if (!internal) stale = false
        lastSnapshot = it
        recentSnapshots.addLast(it)
        readAt[it.snapshotId] = System.currentTimeMillis()
        issuedIds[it.snapshotId] = Unit
        while (recentSnapshots.size > RECENT_READS) recentSnapshots.removeFirst().snapshotId.let { id -> readAt.remove(id) }
    }

    /** 此刻重新读一屏,不登记编号、也不改模型手里的读屏;读不到为 null。 */
    suspend fun peek(): ScreenSnapshot? = screenAttempt { screen.peek() }

    suspend fun observe(withImage: Boolean, adaptiveImage: Boolean = false, recordFrame: Boolean = true): ToolExecution {
        val snap = snapshot()
        val (tree, ocr, ocrStatus) = assessTree(snap)
        val imageReason = if (adaptiveImage && tree.poor) tree.reason else null
        val sendImage = withImage || imageReason != null
        stabilizer?.latest?.let { handed = it; diffChain.clear(); diffChain += it.snapshotId }
        val seen = recordRead(snap, ocr.orEmpty())
        val (elements, cut) = ScreenJson.elements(snap, modelElementsLimit)
        val shotId = shots.next()
        val content = okObj {
            val w = snap.web
            if (w == null) put("currentApp", snap.packageName ?: snap.unreadableApp)
            else { put("currentSite", UrlHost.of(w.url)); put("url", w.url); put("title", w.title); put("tabId", w.tabId) }
            w?.dialog?.let { put("dialog", dialogJson(it)) }
            put("snapshotId", snap.snapshotId)
            put("screenshotId", shotId)
            imageReason?.let { put("imageReason", it) }
            qualityWire(tree)?.let { put("treeQuality", it) }
            ocrStatus?.let { put("ocr", it) }
            put("screenChangedDuringRead", snap.changedDuringRead)
            if (snap.changedDuringRead) put("note", NOTE_CHANGED_DURING_READ)
            snap.opened?.let { put("opened", openedPayload(it)) }
            readFlags(snap, tree, ocr, ocrStatus)
            put("viewport", buildJsonObject { put("width", snap.width); put("height", snap.height) })
            put("elements", elements)
            if (surface.id == "phone" && snap.windows.isNotEmpty()) put("tree", UiTreeJson.windows(snap))
            ocr?.let { put("visibleText", VisibleText.json(it)) }
            if (surface.id == "phone") ScreenMap.json(snap, ocr.orEmpty())?.let { put("screenMap", it) }
            val omitted = snap.web?.omitted ?: 0
            put("truncated", cut > 0 || omitted > 0)
            if (omitted > 0) put("omitted", cut + omitted)
        }
        // 防截屏页面截出来的是黑图,给模型只会误导
        val raw = if ((sendImage && textOnlyDepth == 0 || (recordFrames && recordFrame && textOnlyDepth == 0)) && !snap.protected) screen.screenshotPng() else null
        // 只有主模型能看图才留图；付款页与普通页面按同一规则处理
        val remembered = raw?.takeIf { visionEnabled }
        shots.remember(Recall(shotId, shots.step, snap.packageName, remembered, observationText(snap, ocr.orEmpty())))
        val sent = raw.takeIf { sendImage }
        return ToolExecution(content, image = sent, frame = raw, imageIds = listOfNotNull(sent?.let { shotId }), observations = listOf(seen))
    }

    /**
     * 读屏结果里的 protected / readable / hint 三个字段,按这一屏的实际状况择一写入:
     * 受保护页面只说是受保护;窗口读不到而字也没有时说不可读,并说明后续路线;空屏给空屏的那句;树不够用给树的那句。
     */
    private fun kotlinx.serialization.json.JsonObjectBuilder.readFlags(snap: ScreenSnapshot, tree: TreeCheck, ocr: List<VisibleTextLine>?, ocrStatus: String?) {
        if (snap.protected) {
            put("protected", true); put("hint", HINT_PROTECTED)
            return
        }
        if (unreadableNote(snap) != null && ocr.isNullOrEmpty()) {
            // 窗口不可读不等于画面不可用:有截图和视觉定位时,不能把它盖成只能 Back 或交用户。
            put("readable", false)
            put("hint", unreadableAdvice(snap, tree, ocrStatus))
            return
        }
        if (snap.packageName == null && snap.elements.isEmpty() && !snap.hasWindow) {
            put("hint", Feedback[surface.family.emptyHint])
            return
        }
        poorTreeHint(tree, ocrStatus)?.let { put("hint", it) }
    }

    /**
     * 主模型能看图时此刻屏幕的一帧(原图)和它的 screenshotId,登记进回看。规则和读屏一样:
     * 受保护的页面不截；屏上还没有 App、截不到时为 null。
     */
    suspend fun currentFrame(): Pair<ByteArray, Int>? {
        if (!visionEnabled) return null
        val snap = screenAttempt { screen.peek() } ?: return null
        if (snap.protected) return null
        if (snap.packageName == null && snap.elements.isEmpty() && !snap.hasWindow) return null
        val raw = screenAttempt { if (screen.isProtected()) null else screen.screenshotPng() } ?: return null
        val shotId = shots.next()
        shots.remember(Recall(shotId, shots.step, snap.packageName, raw, screenText(snap)))
        return raw to shotId
    }

    /**
     * 主模型能看图时,动作之后发的那一屏:画面和元素表必须是同一时刻的,模型按元素表里的 ref/snapshotId 操作,画面只用来看懂。
     * 和 observe 同一套：受保护页面不发画面；屏上还没有 App 时为 null。
     */
    suspend fun currentView(): ToolDispatcher.Glance? {
        if (!visionEnabled && !adaptiveVision) return null
        val peeked = screenAttempt { screen.peek() } ?: return null
        if (peeked.packageName == null && peeked.elements.isEmpty() && !peeked.hasWindow) return null
        if (textOnlyDepth > 0) {
            val obs = screenAttempt { observe(withImage = false, recordFrame = false) } ?: return null
            val shotId = (obs.content["screenshotId"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
            return ToolDispatcher.Glance(JsonObject(obs.content - "ok"), null, shotId, obs.observations)
        }
        if (adaptiveVision) {
            val obs = screenAttempt { observe(withImage = false, adaptiveImage = visionEnabled) } ?: return null
            val shotId = (obs.content["screenshotId"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
            return ToolDispatcher.Glance(JsonObject(obs.content - "ok"), obs.image, shotId, obs.observations)
        }
        // 先读元素表(登记 snapshotId),再按这一屏判能不能截:受保护的页面不截
        val obs = screenAttempt { observe(withImage = false) } ?: return null
        val snap = lastSnapshot ?: return null
        val shotId = (obs.content["screenshotId"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        val raw = if (snap.protected) null else screenAttempt { obs.frame ?: if (screen.isProtected()) null else screen.screenshotPng() }
        // 回看里的这一张,补上画面
        val recalls = shots.recalls
        if (raw != null) recalls.indexOfLast { it.id == shotId }.takeIf { it >= 0 }?.let { i ->
            val r = recalls[i]; recalls[i] = Recall(r.id, r.step, r.app, raw, r.elementsText)
        }
        return ToolDispatcher.Glance(JsonObject(obs.content - "ok"), raw, shotId, obs.observations)
    }

    /** 动作成功后,附上此刻的一屏(端口在动作里已等过界面变化):元素表放进 screen 字段,画面另附,结果里写上它的 screenshotId。 */
    suspend fun withFrame(r: ToolExecution): ToolExecution {
        val glance = currentView() ?: return r
        return r.with(content = JsonObject(r.content + ("screen" to glance.content) + ("screenshotId" to JsonPrimitive(glance.imageId))), images = listOfNotNull(glance.image),
            screenElements = glance.content["elements"] as? JsonArray ?: r.screenElements, imageIds = if (glance.image != null) listOf(glance.imageId) else emptyList(),
            observations = r.observations + glance.observations)
    }

    /**
     * 写操作成功后:等界面稳定(上限 [INLINE_SETTLE_MS])再读一屏,和模型上次拿到的那一屏比,差异放进 screen 字段。
     * base 或最近一次全量屏幕模型已看不到时发全量;读不到屏幕,就照原样返回动作结果。
     */
    suspend fun withScreen(r: ToolExecution): ToolExecution {
        val fresh = try {
            screen.waitStable(INLINE_SETTLE_MS)
            snapshot()
            stabilizer!!.latest!!
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.rethrowAppAccess()

            return r
        }
        val visibleIds = visible
        // 连续差异最多 MAX_DIFF_CHAIN 次就发一次全量:链越长越难读,压缩时要保留的往返也越多
        val baseVisible = diffChain.isNotEmpty() && diffChain.size - 1 < MAX_DIFF_CHAIN && (visibleIds == null || diffChain.all { it in visibleIds })
        val snap = fresh.screen
        val (tree, ocr, ocrStatus) = assessTree(snap)
        val shotId = shots.next()
        val diff = if (snap.protected) {
            // 受保护的页面看不到画面和元素；下一次从全量重新开始
            handed = null; diffChain.clear()
            buildJsonObject {
                put("snapshotId", fresh.snapshotId); put("currentApp", snap.packageName); put("full", false)
                put("protected", true); put("hint", HINT_PROTECTED)
                put("screenshotId", shotId)
            }
        } else {
            val d = InterfaceUpdate.between(handed, fresh, baseVisible, modelElementsLimit)
            handed = fresh
            if (d["full"] == JsonPrimitive(true)) diffChain.clear()
            diffChain += fresh.snapshotId
            JsonObject(d + screenExtras(snap, tree, ocr, ocrStatus, shotId))
        }
        val readText = observationText(snap, ocr.orEmpty())
        // 和上一次读到的一样,就不再记一遍,免得把较早的读屏挤出去
        val observation = if (lastRead() != readText) recordRead(snap, ocr.orEmpty()) else null
        // 和 observe 同样的规则:防截屏页面不截;只有主模型能看图,才留图回看
        val imageReason = if (adaptiveVision && visionEnabled && tree.poor) tree.reason else null
        val sendImage = visionEnabled && (!adaptiveVision || imageReason != null)
        val raw = if ((recordFrames || sendImage) && textOnlyDepth == 0 && !snap.protected) {
            screenAttempt { screen.screenshotPng() }
        } else null
        val remembered = raw?.takeIf { visionEnabled }
        shots.remember(Recall(shotId, shots.step, snap.packageName, remembered, readText))
        // 旧模式总附画面；分级模式只在元素明显不足时,才附画面。
        val attached = remembered?.takeIf { sendImage && r.images.isEmpty() }
        val screenDiff = if (imageReason == null) diff else JsonObject(diff + ("imageReason" to JsonPrimitive(imageReason)))
        return r.with(content = JsonObject(r.content + ("screen" to placeRelabeled(screenDiff))),
            frame = r.frame ?: raw?.takeIf { recordFrames }, images = attached?.let { listOf(it) } ?: r.images,
            screenElements = ScreenJson.elements(snap, modelElementsLimit).first, imageIds = if (attached != null) listOf(shotId) else r.imageIds).observing(observation)
    }

    /** 差异结果里除差异本身之外要附的字段;拼接顺序即结果里的出现顺序。 */
    private fun screenExtras(snap: ScreenSnapshot, tree: TreeCheck, ocr: List<VisibleTextLine>?, ocrStatus: String?, shotId: Int): Map<String, JsonElement> =
        mapOf("screenshotId" to JsonPrimitive(shotId)) +
            (ocr?.let { mapOf("visibleText" to VisibleText.json(it)) } ?: emptyMap()) +
            (if (surface.id == "phone") ScreenMap.json(snap, ocr.orEmpty())?.let { mapOf("screenMap" to it) } ?: emptyMap() else emptyMap()) +
            (if (snap.changedDuringRead) mapOf("note" to JsonPrimitive(NOTE_CHANGED_DURING_READ)) else emptyMap()) +
            (snap.opened?.let { mapOf("opened" to openedPayload(it)) } ?: emptyMap()) +
            (snap.web?.dialog?.let { mapOf("dialog" to dialogJson(it)) } ?: emptyMap()) +
            (qualityWire(tree)?.let { mapOf("treeQuality" to JsonPrimitive(it)) } ?: emptyMap()) +
            (ocrStatus?.let { mapOf("ocr" to JsonPrimitive(it)) } ?: emptyMap()) +
            ((unreadableNote(snap)?.takeIf { ocr.isNullOrEmpty() }?.let {
                mapOf("readable" to JsonPrimitive(false), "hint" to JsonPrimitive(unreadableAdvice(snap, tree, ocrStatus)))
            }) ?: poorTreeHint(tree, ocrStatus)?.let { mapOf("hint" to JsonPrimitive(it)) } ?: emptyMap())

    /** 动作之前的界面状态(不登记编号)和一帧画面,判效果用;读不到的为 null。[png] 是动作前的截图,点了之后给视觉核对用。 */
    class EffectProbe internal constructor(before: ScreenSnapshot?, frame: FrameGrid?, png: ByteArray? = null) {
        var before: ScreenSnapshot? = before
            internal set
        var frame: FrameGrid? = frame
            internal set
        var png: ByteArray? = png
            internal set
    }

    /** 这次动作前看的那一眼,动作判完效果前留着:动作开始之前 Maeve 自己又动过界面时由 [reprobe] 重看。 */
    private var awaitingProbe: EffectProbe? = null

    /** 这次动作的效果判完了(或者不判了)。 */
    fun probeDone() { awaitingProbe = null }

    /**
     * 动作开始之前 Maeve 自己动过界面(网页停留里轻轻滚了页面):重看一眼当作动作前的样子,免得把这点滚动算成动作的效果;
     * 模型手里那一屏的位置也已不准,标成过时;没有在等判效果的动作时,什么也不做。
     */
    suspend fun reprobe() {
        val pending = awaitingProbe ?: return
        stale = true
        withTimeoutOrNull(EffectTuning.PROBE_MS) {
            pending.before = peek(); pending.frame = gridNow()
            if (pending.png != null) pending.png = shotNow()
        }
    }

    /** 刚为按描述找目标读的那一屏:给模型的 snapshotId 和端口原样的一屏;被 [probe] 取走或作废前有效。 */
    private var primedRead: Pair<String, ScreenSnapshot>? = null

    fun clearPrimed() { primedRead = null }

    /**
     * 动作前看一眼,最多花 [EffectTuning.PROBE_MS];来不及的部分为 null(之后只按端口的事实判)。
     * 动作用的正是刚按描述读的那一屏([snapshotId] 对得上)就用它,不再读一次。
     * [withPng]:同时截一张图留着,点了之后给视觉核查当「之前」(只对点击类动作才采)。
     */
    suspend fun probe(snapshotId: String? = null, withPng: Boolean = false): EffectProbe {
        val shot = if (withPng) shotNow() else null
        val primedSnap = primedRead?.takeIf { it.first == snapshotId }?.second
        primedRead = null
        if (primedSnap != null) {
            var frameGrid: FrameGrid? = null
            withTimeoutOrNull(EffectTuning.PROBE_MS) { frameGrid = gridNow() }
            return EffectProbe(primedSnap, frameGrid, shot).also { awaitingProbe = it }
        }
        var beforeSnap: ScreenSnapshot? = null
        var frameGrid: FrameGrid? = null
        withTimeoutOrNull(EffectTuning.PROBE_MS) { beforeSnap = peek(); frameGrid = gridNow() }
        return EffectProbe(beforeSnap, frameGrid, shot).also { awaitingProbe = it }
    }

    /** 此刻的一张截图,给点击前后的视觉核查当「之前」「之后」;受保护页面、截不到时为 null。 */
    private suspend fun shotNow(): ByteArray? {
        val snap = screenAttempt { screen.peek() } ?: return null
        if (snap.protected) return null
        return screenAttempt { if (screen.isProtected()) null else screen.screenshotPng() }
    }

    private suspend fun gridNow(): FrameGrid? = screenAttempt { screen.frameGrid() }

    /**
     * 动作成功之后,判它对界面状态的实际效果(见 [ActionEffect]),写进结果的 effect 字段,并按效果给提示;结果的 changed 跟着效果走。
     * 动作之后已经读过([readsAtAction] 之后又读过,即结果里带了界面)就用那一份。否则:端口在动作里已等过界面(报了变没变或页面静没静)
     * 就直接看一眼,没等过才等界面稳定(上限 [EffectTuning.SETTLE_MS])。看起来没变时,再等 [EffectTuning.RECHECK_MS] 重看一次(异步更新)。
     * 整个判断最多多花 [EffectTuning.BUDGET_MS],到了就按端口的事实下结论。只读界面、取画面,不做任何动作。
     * 界面变了而结果里没有新的界面时,标记模型手里的那份已过时([stale])。
     */
    suspend fun withEffect(
        r: ToolExecution, probe: EffectProbe, verb: Verb, readsAtAction: Int, target: UiElement?, region: List<Int>?, typed: String?,
    ): ToolExecution {
        val content = r.content
        val openedInfo = content["opened"] as? JsonObject
        val facts = PortFacts(
            changed = r.changed,
            switchedTo = (content["app_switched"] as? JsonPrimitive)?.content,
            verified = content["verification"] == JsonPrimitive("match"),
            loading = content["settled"] == JsonPrimitive(false),
            onUserScreen = openedInfo?.takeIf { it["onUserScreen"] == JsonPrimitive(true) }?.let { (it["package"] as? JsonPrimitive)?.content },
        )
        val portAlreadyWaited = r.changed != null || "settled" in content
        val readAfterAction = reads > readsAtAction
        fun judgeWith(after: ScreenSnapshot?, frame: FrameGrid?) = ActionEffect.classify(
            verb, probe.before, after, facts, FrameDiff.compare(probe.frame, frame, region), target, typed,
            pressedEnter = content["pressedEnter"] == JsonPrimitive(true), secret = content["secret"] == JsonPrimitive(true),
        )
        var verdict: ActionEffect? = judgeWith(null, null)
        // 动作之后看到的界面里新出现的「到上限了」的说法(看过两次时任一次有就算:提示条一会儿就消失)
        var limitNote: String? = null
        withTimeoutOrNull(EffectTuning.BUDGET_MS) {
            if (!readAfterAction && !portAlreadyWaited) settle(EffectTuning.SETTLE_MS)
            val after = if (readAfterAction) lastSnapshot else peek()
            limitNote = LimitNotice.find(probe.before, after)
            verdict = judgeWith(after, gridNow())
            if (verdict?.kind == EffectKind.NoEffect) {
                // 界面可能是异步更新的:再等一会儿重看一次,仍没变才算没效果
                delay(EffectTuning.RECHECK_MS)
                val again = peek()
                limitNote = limitNote ?: LimitNotice.find(probe.before, again)
                verdict = judgeWith(again, gridNow())
            }
        }
        // 树读不出点的那一处(只在手机上判:网页没有树够不够用这回事):看不出变化不等于没点上
        val judged = verdict?.let { v ->
            if (surface.id != "phone") return@let v
            val verdictQuality = treeVerdict?.quality ?: probe.before?.let { TreeAdequacy.ofTree(it).quality }
            if (v.kind == EffectKind.NoEffect && ActionEffect.treeBlind(verb, verdictQuality, probe.before, target, region)) v.copy(treeBlind = true) else v
        } ?: return r
        // 点击之后视觉核对界面变化:树说变化够多、视觉说没变或只有小变化,正是点歪了却报成功的那类,据视觉改判
        val confirmed = if (verb == Verb.Tap && (judged.kind == EffectKind.Done || judged.kind == EffectKind.SmallChange)) visionConfirm(judged, probe.png) else judged
        if ("screen" !in content && confirmed.kind != EffectKind.NoEffect) stale = true
        val didChange = confirmed.kind != EffectKind.NoEffect
        val hints = listOfNotNull(limitNote?.let { Feedback.format("effect.limit_reached", "text" to it.take(LIMIT_NOTE_MAX)) },
            EffectFeedback.hint(confirmed, verb, portSwitched = "app_switched" in content || openedInfo != null),
            Feedback["effect.stale"].takeIf { stale && confirmed.kind != EffectKind.SmallChange }, (content["hint"] as? JsonPrimitive)?.content)
        val updated = JsonObject(content - "hint" + ("effect" to confirmed.toJson()) +
            (if ("changed" in content) mapOf("changed" to JsonPrimitive(didChange)) else emptyMap()) +
            (if (verb == Verb.Scroll && confirmed.kind == EffectKind.NoEffect) mapOf("atEnd" to JsonPrimitive(true)) else emptyMap()) +
            (if (stale) mapOf("interfaceStale" to JsonPrimitive(true)) else emptyMap()) +
            (limitNote?.let { mapOf("limitReached" to JsonPrimitive(it.take(LIMIT_NOTE_MAX))) } ?: emptyMap()) +
            (if (hints.isNotEmpty()) mapOf("hint" to JsonPrimitive(hints.joinToString(" "))) else emptyMap()))
        return r.with(content = updated, changed = didChange)
    }

    /**
     * 点击之后视觉核对界面变化(见 [TapVisionCheck.screenChange]):树判 Done/SmallChange 时,拿动作前后两张截图问视觉模型。
     * 说没变 → 改判没生效(点了而界面一点没动,多半是没点上);说只有小变化而树说明显变了 → 降级成小变化。
     * 看不了(没有视觉模型、截图拿不到、受保护、出错)时按树的原判:视觉核查只是兜底,不拦路。
     */
    private suspend fun visionConfirm(e: ActionEffect, beforePng: ByteArray?): ActionEffect {
        val vision = tapVisionCheck ?: return e
        if (beforePng == null) return e
        val afterShotPng = shotNow() ?: return e
        return when (vision.screenChange(beforePng, afterShotPng)) {
            ScreenChange.None -> e.copy(kind = EffectKind.NoEffect, visionCheck = "none")
            ScreenChange.Minor -> if (e.kind == EffectKind.Done) e.copy(kind = EffectKind.SmallChange, visionCheck = "minor") else e.copy(visionCheck = "minor")
            ScreenChange.Page -> e.copy(visionCheck = "page")
            ScreenChange.Unavailable -> e
        }
    }

    /** 最近一次读屏判的树够不够用;判效果时据此说「看不出变化」还是「没点上」。 */
    private var treeVerdict: TreeCheck? = null

    /**
     * 这一屏的树缺了一块或不能用(只在手机上判,和读屏结果里的 treeQuality 同一口径):光看树判不准时按最近一次读屏(读过屏上文字)的判断。
     * 按描述找目标(输入框或要点的东西)在树里找不到时,据此改看图找。
     */
    fun treeBlind(s: ScreenSnapshot): Boolean = surface.id == "phone" &&
        (TreeAdequacy.ofTree(s).quality ?: treeVerdict?.quality).let { it == TreeQuality.Partial || it == TreeQuality.Unusable }

    private suspend fun settle(ms: Long) {
        screenAttempt { screen.waitStable(ms) }
    }

    /** 读屏前刚从用户主屏挪回来的 App:和动作结果里的 opened 同样的字段,另带那句说明。 */
    private fun openedPayload(o: OpenedElsewhere): JsonObject = JsonObject(o.toJson() + ("note" to JsonPrimitive(openedHint(o))))

    /**
     * 前台 App 打开着、窗口却读不到时给模型的说明(不是「没打开」,不要再启动);读得到或不知道前台是谁时为 null。
     */
    private fun unreadableNote(s: ScreenSnapshot): String? {
        val pkg = s.unreadableApp ?: return null
        if (s.elements.any { it.meaningful }) return null
        return Feedback.format("unreadable_window", "app" to pkg)
    }

    /** 窗口不可读但截图可用时,保留「不要重复启动」,后续路线改用这棵树的 OCR/视觉回退。 */
    private fun unreadableAdvice(s: ScreenSnapshot, tree: TreeCheck, ocrStatus: String?): String {
        val fallback = poorTreeHint(tree, ocrStatus) ?: return unreadableNote(s)!!
        return Feedback.format("unreadable_window.fallback", "app" to s.unreadableApp!!, "fallback" to fallback)
    }

    /** 这一次读到的界面状态:可读元素的文字、在哪、网页标题、输入框里的字。 */
    private fun recordRead(s: ScreenSnapshot, ocr: List<VisibleTextLine> = emptyList()): Observation {
        val fields = inputFields(s)
        val where = s.web?.url?.let(Place::page) ?: s.packageName?.let(Place::app)
        return Observation(ObsKind.Read, observationText(s, ocr), source = surface.id, place = where, title = s.web?.title,
            trust = if (s.web != null) Trust.Untrusted else Trust.Trusted, inputs = InputHints(fields.names, fields.focused))
    }

    /**
     * 这一屏的树够不够用([TreeAdequacy]),和要补上的屏上文字(OCR,只留树里没有的、去掉认错的行)。
     * 树明显够用、受保护时不读字;判不准时读字再定;树不够用时一定读字:看不到图的主模型靠它,
     * 能看图的主模型也拿它的位置去点,证据账本(回答来源、核查)和 phone_collect 也只能从这里拿到屏上写着什么。
     */
    private suspend fun assessTree(s: ScreenSnapshot): TreeRead {
        val verdict = TreeAdequacy.ofTree(s)
        if (verdict.quality == TreeQuality.Adequate || verdict.quality == TreeQuality.Protected) return TreeRead(verdict, null, null).also { treeVerdict = it.tree }
        val (lines, status) = readScreenText(s, if (verdict.poor) OCR_WAIT_POOR_MS else OCR_WAIT_MS)
        val withText = TreeAdequacy.withText(verdict, lines)
        treeVerdict = withText
        // 树不够用、屏上文字也一行没有:照实说为什么,模型才知道该看图找,而不是再读一次
        return TreeRead(withText, lines, status.takeIf { qualityWire(withText) != null && lines.isNullOrEmpty() })
    }

    /**
     * 屏上树里没有的文字,和一行也没读到时的原因:empty(画面上没认出字)、illegible(认出的都是认错的)、
     * timeout(时限内没认完)、unavailable(端口给不出:没有画面、画面一直在大变)、failed(识别出错)。
     * 不读(网页、受保护、没窗口)时都为 null。树在读的过程中变了也照样读:字的位置来自画面本身,
     * 端口自己会等画面停稳,认的过程中整页变了会重认。
     */
    private suspend fun readScreenText(s: ScreenSnapshot, waitMs: Long): Pair<List<VisibleTextLine>?, String?> {
        if (surface.id != "phone" || s.protected || !s.hasWindow) return null to null
        var deadlineHit = true
        val lines = try {
            withTimeoutOrNull(waitMs) { screen.readVisibleText().also { deadlineHit = false } }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { e.rethrowAppAccess(); return null to "failed" }
        if (lines == null) return null to if (deadlineHit) "timeout" else "unavailable"
        if (lines.isEmpty()) return lines to "empty"
        val readable = VisibleText.legible(lines)
        if (readable.isEmpty()) return readable to "illegible"
        return VisibleText.missing(s, readable) to null
    }

    /** 写进读屏结果的 treeQuality:只在手机上、树不够用时写(网页没有屏上文字可补)。 */
    private fun qualityWire(tree: TreeCheck): String? = tree.quality?.takeIf { surface.id == "phone" && tree.poor }?.wire

    /** 树不够用时给模型的说明:屏上文字从哪来、怎么点;配了看图模型时再说没有字的图标怎么找;连字也读不到时说为什么、该怎么找。 */
    private fun poorTreeHint(tree: TreeCheck, ocrStatus: String? = null): String? = when {
        qualityWire(tree) == null -> null
        ocrStatus != null && locator != null -> Feedback.format("tree_poor.no_text.visual", "ocr" to ocrStatus)
        ocrStatus != null -> Feedback.format("tree_poor.no_text", "ocr" to ocrStatus)
        locator != null -> Feedback.format("tree_poor.visual", "tree_poor" to Feedback["tree_poor"])
        else -> Feedback["tree_poor"]
    }

    /**
     * 核查前再看一眼:此刻重新读这块屏(不登记编号,模型手里的 ref 不受影响),[withImage] 时另截一张图(防截屏页面不截);
     * 读到了时再加上能力另给的 [extra](网页正文的开头:读屏只有视口里的元素)。读不到也照样返回(没有观察)。
     */
    suspend fun freshLook(withImage: Boolean, extra: (suspend () -> Observation?)? = null): Look {
        val snap = screenAttempt { screen.snapshot() }
        val raw = if (withImage && snap != null && !snap.protected) {
            screenAttempt { screen.screenshotPng() }
        } else null
        // 树不够用时照样读屏上文字:核查要看的是屏上写着什么
        val observation = snap?.let { recordRead(it, assessTree(it).ocr.orEmpty()) }?.takeIf { it.text.isNotEmpty() }
        val moreObs = if (snap != null) extra?.invoke() else null
        return Look(listOfNotNull(observation, moreObs), raw, snap?.protected == true)
    }

    /** 「在哪」按这块屏的说法报(网页上是站点):和 observe 一样,手机与网页的「在哪」不混。 */
    private fun placeRelabeled(o: JsonObject): JsonObject {
        val key = surface.family.placeKey
        if (key == ScreenFamily.APP_PLACE || ScreenFamily.APP_PLACE !in o) return o
        return JsonObject(o.filterKeys { it != ScreenFamily.APP_PLACE } + (key to (o[ScreenFamily.APP_PLACE] ?: JsonNull)))
    }
}

/** 这一屏输入框(不是提示语、不是密码框)里的字,和有输入焦点的那个;证据账本据此分辨输入框回显、接管时用户填的字。 */
internal fun inputFields(s: ScreenSnapshot): InputFields {
    val boxes = s.elements.filter { it.editable && !it.hint && !it.secret && it.name.isNotBlank() }
    return InputFields(boxes.map { it.name.replace('\n', ' ') }, boxes.firstOrNull { it.focused }?.name?.replace('\n', ' '))
}

internal class InputFields(val names: List<String>, val focused: String?)

/** 一次读屏的树判断、补上的屏上文字,和树不够用而文字一行也没有时的原因(见 ScreenSession.readScreenText)。 */
internal data class TreeRead(val tree: TreeCheck, val ocr: List<VisibleTextLine>?, val ocrStatus: String?)

/** 读屏的元素文字,一行一个;第一行是前台 App。没有文字时为空串。密码框里的字不记。 */
internal fun screenText(s: ScreenSnapshot): String {
    val labels = s.elements.filter { it.meaningful && it.name.isNotBlank() && !it.secret }.map { it.name.replace('\n', ' ') }
    // 网页标题不在元素里,也是这一屏的事实(页面给的,和元素文字一样是待核对的材料);没有可读元素的网页也照样留着
    val pageTitle = s.web?.title?.takeIf { it.isNotBlank() }?.let { "网页标题:${it.replace('\n', ' ')}" }
    val pageDialog = s.web?.dialog?.let { "网页弹框(${it.kind}):${it.message.replace('\n', ' ')}" }
    if (labels.isEmpty() && pageTitle == null && pageDialog == null) return ""
    val place = s.web?.let { "当前网页:${it.url}" } ?: "当前 App:${s.packageName ?: "未知"}"
    return (listOfNotNull(place, pageTitle, pageDialog) + labels).joinToString("\n").take(MAX_READ_CHARS)
}

/** 无障碍文字仍是主证据；本机 OCR 只追加树里缺失的屏幕文字。 */
private fun observationText(s: ScreenSnapshot, ocr: List<VisibleTextLine>): String {
    val fromTree = screenText(s)
    if (ocr.isEmpty()) return fromTree
    return listOf(fromTree, "屏幕可见文字(OCR):", ocr.joinToString("\n") { it.text }).filter(String::isNotEmpty)
        .joinToString("\n").take(MAX_READ_CHARS)
}

internal fun toPx(n: Int, size: Int) = (n * (size - 1) / 999.0).roundToInt()

/** 读屏结果里的页面弹框:模型要先用 web_interact 的 dialog 回话,页面才会接着跑。 */
internal fun dialogJson(d: PageDialog): JsonObject = buildJsonObject {
    put("kind", d.kind)
    put("message", d.message.take(PageDialog.MAX_MESSAGE))
    d.defaultText?.let { put("defaultText", it.take(PageDialog.MAX_MESSAGE)) }
    put("hint", Feedback["page_dialog"])
}
