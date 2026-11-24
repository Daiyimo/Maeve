package com.daiyimo.maeve.core.uistate

import kotlin.math.hypot
import kotlin.math.min

/**
 * 给模型看的一屏:[elements] 的 ref 已换成模型编号,[snapshotId] 是这块屏的前缀加序号("p1"、"w2"…);
 * [port] 是端口原样的快照,[toPort] 是模型编号 → 端口编号的对照。
 */
data class ModelSnapshot(
    val snapshotId: String,
    val port: ScreenSnapshot,
    val elements: List<UiElement>,
    val toPort: Map<String, String>,
) {
    /** 换成模型编号的那份快照;不可逆判定、坐标换算都用它。 */
    val screen: ScreenSnapshot by lazy {
        val refs = toPort.entries.associate { (model, original) -> original to model }
        port.copy(
            snapshotId = snapshotId,
            elements = elements,
            windows = port.windows.map { window -> window.copy(nodes = window.nodes.map { it.withRefs(refs) }) },
        )
    }
}

/** 编号解析不出的原因;[wire] 写进工具结果的 `cause`。 */
enum class StaleCause(val wire: String) {
    /** snapshotId 不是这块屏发过的,或太旧、已经不记得了。 */
    SnapshotUnknown("snapshot_unknown"),
    /** 编号在给的那一屏之后才有:新编号配了旧 snapshotId。 */
    RefNotInSnapshot("ref_not_in_snapshot"),
    /** 编号已不在最新一屏里:元素没了,或编号写错。 */
    RefRetired("ref_retired"),
    /** 编号还在,可从模型看到的那一屏算起,元素挪远了。 */
    ElementMoved("element_moved"),
}

sealed interface Resolved {
    /** [relocated]:模型给的是旧一屏的 snapshotId,编号在最新一屏里仍指同一个元素。 */
    data class Current(val portRef: String, val portSnapshotId: String, val relocated: Boolean) : Resolved
    data class Stale(val cause: StaleCause) : Resolved
}

/** 沿用旧编号允许的最大中心位移,取屏幕短边乘这个比例(与端口找回的上限一致)。 */
const val STABLE_REF_MAX_SHIFT = RELOCATE_MAX_SHIFT

/** 旧 snapshotId 最多能往前翻译几屏;更早的按过期处理。 */
private const val KEEP_GENERATIONS = 20

/**
 * 跨读屏的稳定元素编号。端口编号只在一次读屏内有效;这里让相邻两屏里没变的元素保持同一个模型编号,
 * 到动作时再翻译回端口编号,过期判断与找回仍由端口负责。
 *
 * 沿用旧编号的条件不比端口的找回([relocate])宽:键(角色 + 名字 + 资源 ID)在新旧两屏里都唯一、
 * 前台 App 没换、遍历顺序里前一个有名字的元素(上下文)相同,或页面整体没动且它还在原地、
 * 中心位移不超过短边的 [STABLE_REF_MAX_SHIFT];列表里的元素(落在可滚动容器里)还要页面没滚动、它没挪窝。
 * 列表里重复的同名元素只有边界完全没动且页面没滚时才一对一沿用;滚动后仍拿新编号。
 * 同名歧义组里的编号不做松散沿用,避免用户条目消失后被顶部搜索框顶替。
 * 没名字也没资源 ID 的元素,认不出是不是同一个,也拿新编号。编号在任务内单调递增,退役的不再复用。
 *
 * snapshotId 是 [prefix] 加序号:每块屏一个前缀,看编号就知道是哪块屏的。
 */
class RefStabilizer(private val prefix: String = "s") {
    private var nextIndex = 1
    private var generation = 0
    var latest: ModelSnapshot? = null
        private set
    /** 每个编号首次出现在第几屏;编号一旦从某屏消失就不会再出现,所以它在 [firstSeen, 最新一屏] 里连续存在。 */
    private var firstSeen = mapOf<String, Int>()
    /** 出生在角色+名字+资源 ID 同名歧义组里的编号;这类编号不再跨屏携带。 */
    private var ambiguous = emptySet<String>()
    /** 最近 [KEEP_GENERATIONS] 屏里每个模型编号的中心;翻译旧 snapshotId 时,靠它限制累计位移。 */
    private val centers = LinkedHashMap<Int, Map<String, Pair<Int, Int>>>()

    fun next(port: ScreenSnapshot): ModelSnapshot {
        val n = ++generation
        val previous = latest?.takeIf { it.port.packageName == port.packageName }
        val shortEdge = min(port.width, port.height)
        val priorLocators = previous?.let { RefLocators.locators(it.screen) }.orEmpty()
        val currentLocators = RefLocators.locators(port)
        val pageStill by lazy { stationary(priorLocators, currentLocators, shortEdge * SAME_SPOT_SHIFT) }
        val previousByKey = previous?.elements?.withIndex()?.groupBy { RefLocators.key(it.value) }.orEmpty()
        val keyCounts = port.elements.groupingBy(RefLocators::key).eachCount()
        val toPort = LinkedHashMap<String, String>()
        val seenNow = HashMap<String, Int>()
        val ambiguousNow = mutableSetOf<String>()
        val used = mutableSetOf<String>()
        val elements = port.elements.mapIndexed { i, e ->
            val k = RefLocators.key(e)
            val candidates = previousByKey[k].orEmpty()
            // 同名控件先按完全相同的边界一对一配对;精确帧不依赖唯一性/上下文,列表滚动后仍可能换行。
            val exact = if (candidates.size > 1 && (keyCounts[k] ?: 0) > 1) {
                candidates.firstOrNull { (_, previous) ->
                    previous.ref !in used && identifiable(e) && previous.bounds.isNotEmpty() &&
                        previous.bounds == e.bounds && previous.centerX == e.centerX && previous.centerY == e.centerY
                }
            } else null
            // 唯一键才走带上下文、位移和列表静止检查的松散沿用。
            val loose = if (candidates.size == 1 && keyCounts[k] == 1) {
                candidates.firstOrNull { (j, previous) ->
                    previous.ref !in used && previous.ref !in ambiguous && identifiable(e) &&
                        relocate(priorLocators[j], priorLocators, currentLocators, shortEdge) == Relocation.Found(i) &&
                        (!(priorLocators[j].inList || currentLocators[i].inList) ||
                            (pageStill && RefLocators.shift(priorLocators[j], currentLocators[i]) <= shortEdge * SAME_SPOT_SHIFT))
                }
            } else null
            val carried = (exact ?: loose)?.value
            carried?.let { used += it.ref }
            val ref = carried?.ref ?: "@e${nextIndex++}"
            if (candidates.size > 1 || (keyCounts[k] ?: 0) > 1) ambiguousNow += ref
            seenNow[ref] = firstSeen[ref] ?: n
            toPort[ref] = e.ref
            e.copy(ref = ref)
        }
        firstSeen = seenNow
        ambiguous = ambiguousNow
        record(n, elements)
        return ModelSnapshot("$prefix$n", port, elements, toPort).also { latest = it }
    }

    /** 记下第 [n] 屏每个编号的中心,只留最近 [KEEP_GENERATIONS] 屏。 */
    private fun record(n: Int, elements: List<UiElement>) {
        centers[n] = elements.associate { it.ref to (it.centerX to it.centerY) }
        while (centers.size > KEEP_GENERATIONS) centers.remove(centers.keys.first())
    }

    fun resolve(ref: String, snapshotId: String): Resolved {
        val current = latest ?: return Resolved.Stale(StaleCause.SnapshotUnknown)
        val n = snapshotId.removePrefix(prefix).takeIf { snapshotId.startsWith(prefix) }?.toIntOrNull()
            ?: return Resolved.Stale(StaleCause.SnapshotUnknown)
        if (n < 1 || n > generation) return Resolved.Stale(StaleCause.SnapshotUnknown)
        // 太旧的一屏,连当时有哪些编号都没留着:先报 snapshot_unknown,别把未知/退役的编号误报成 ref_retired。
        if (n != generation && n !in centers) return Resolved.Stale(StaleCause.SnapshotUnknown)
        // 编号得真是那一屏里的:模型拼错的组合,不放过
        val bornAt = firstSeen[ref] ?: return Resolved.Stale(StaleCause.RefRetired)
        if (bornAt > n) return Resolved.Stale(StaleCause.RefNotInSnapshot)
        val portRef = current.toPort[ref] ?: return Resolved.Stale(StaleCause.RefRetired)
        if (n != generation) {
            // 每屏之间都没挪远,攒起来也可能挪远了:从模型看到的那一屏算起
            val wasAt = centers[n]?.get(ref) ?: return Resolved.Stale(StaleCause.SnapshotUnknown)
            val nowAt = centers[generation]?.get(ref) ?: return Resolved.Stale(StaleCause.SnapshotUnknown)
            val limit = shiftLimit(current.port)
            if (hypot((wasAt.first - nowAt.first).toDouble(), (wasAt.second - nowAt.second).toDouble()) > limit) return Resolved.Stale(StaleCause.ElementMoved)
        }
        return Resolved.Current(portRef, current.port.snapshotId, relocated = n != generation)
    }

    /** 有名字或资源 ID,才认得出是不是同一个元素。 */
    private fun identifiable(e: UiElement) = e.name.isNotBlank() || !e.resourceId.isNullOrBlank()

    /** 累计位移上限:这块屏的短边乘比例。 */
    private fun shiftLimit(s: ScreenSnapshot) = min(s.width, s.height) * STABLE_REF_MAX_SHIFT
}
