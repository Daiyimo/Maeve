package com.daiyimo.maeve.core.uistate

/**
 * 模型给这个任务里的 App 定下的「任务结束时怎么办」:留着(keep)还是关掉(close),并附一句原因。
 * 没标过的按默认:留在后台,结束时正在出声的关掉(由手机一侧在任务结束时执行);同一份 App 再标一次以最后一次为准。
 * [userId] 为 null 指本来那份;另一份(分身、工作资料)写那份用户的编号。
 */
class AfterTaskMarks {
    data class Mark(val packageName: String, val userId: Int?, val keep: Boolean, val reason: String) {
        /** 和手机一侧约定的键:本来那份就是包名,另一份是 包名@用户。 */
        val key: String get() = key(packageName, userId)
    }

    private val marks = LinkedHashMap<String, Mark>()

    /** 记下一条;返回这份 App 原先那一条(没有则为 null)。 */
    @Synchronized
    fun mark(packageName: String, userId: Int?, keep: Boolean, reason: String): Mark? {
        val entry = Mark(packageName, userId, keep, reason)
        val previous = marks.remove(entry.key)
        marks[entry.key] = entry
        return previous
    }

    /** 此刻记下的全部条目,按记下的先后排。 */
    @Synchronized
    fun all(): List<Mark> = marks.values.toList()

    /** 任务结束时留着的那些 App 的键。 */
    fun keepKeys(): List<String> = keysOf { it.keep }

    /** 任务结束时关掉的那些 App 的键。 */
    fun closeKeys(): List<String> = keysOf { !it.keep }

    /** 按 [predicate] 挑条目,取出它们的键,按记下的先后排。 */
    private fun keysOf(predicate: (Mark) -> Boolean): List<String> = all().filter(predicate).map { it.key }

    companion object {
        /** 和手机一侧约定的键:本来那份就是包名,另一份是 包名@用户。 */
        fun key(packageName: String, userId: Int?): String = if (userId == null) packageName else "$packageName@$userId"
    }
}
