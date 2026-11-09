package com.daiyimo.maeve.core.access

/**
 * 应用分类只服务于列表整理与推荐，与执行权限无关。
 * 展示用的中文/本地化名称由 app 侧资源 `values/strings_appaccess.xml` 提供。
 */
enum class AppCategory {
    Finance,
    Social,
    Game,
    Shopping,
    Productivity,
    Media,
    Other,
    Unknown,
    ;

    companion object {
        /** 按模型返回的英文枚举名匹配；匹配不上归 [Unknown]，绝不在此抛错。 */
        fun from(raw: String?): AppCategory = entries.firstOrNull { it.name == raw } ?: Unknown
    }
}

/** 一条分类结论：[reason] 是给用户看的一句话，[manual] 表示由用户手工指定而非模型判断。 */
data class AppClassification(
    val category: AppCategory,
    val reason: String = "",
    val manual: Boolean = false,
)

/**
 * 应用访问控制的当前快照。
 *
 * @property blocked 用户禁用的包名集合
 * @property shareAllowed 禁用集合里仍允许作为分享目标的对象
 * @property classifications 包名到分类的映射
 * @property available 应用访问设置本身是否可用（系统组件异常时为 false）
 * @property revision 单调递增版本号，用于判断设置是否发生过变化
 */
data class AppAccessState(
    val blocked: Set<String> = emptySet(),
    val shareAllowed: Set<String> = emptySet(),
    val classifications: Map<String, AppClassification> = emptyMap(),
    val available: Boolean = true,
    val revision: Long = 0,
) {
    /**
     * 针对 [packageName] 给出拒绝原因；可以放行时返回 null。
     * 包名未知但有黑名单时按「无法确认」处理：宁可多问一次，也不放过未知应用。
     */
    fun denial(packageName: String?): AppAccessDenied? {
        if (!available) return AppAccessDenied(CODE_UNAVAILABLE, null)
        if (packageName.isNullOrBlank()) {
            return if (blocked.isNotEmpty()) AppAccessDenied(CODE_UNVERIFIED, null) else null
        }
        return if (packageName in blocked) AppAccessDenied(CODE_BLOCKED, packageName) else null
    }

    /** [packageName] 能否作为分享/发送对象：设置可用，且未被禁用或已在白名单里。 */
    fun canShare(packageName: String): Boolean =
        available && (packageName !in blocked || packageName in shareAllowed)

    companion object {
        const val CODE_BLOCKED = "app_blocked"
        const val CODE_UNAVAILABLE = "app_access_unavailable"
        const val CODE_UNVERIFIED = "app_access_unverified"
        const val CODE_PROTECTED = "app_access_protected"
    }
}

/** 访问状态每次现取，任务执行期间不冻结权限。 */
fun interface AppAccessPolicy {
    fun current(): AppAccessState

    companion object {
        /** 不做任何限制的默认策略。 */
        val ALLOW: AppAccessPolicy = AppAccessPolicy { AppAccessState() }
    }
}

/**
 * 应用被用户禁用，或无法确认前台应用时抛出。
 *
 * 这不是普通操作失败：异常消息会直接交给模型阅读，由模型自行调整计划，
 * 因此不能被上层当成任务异常吞掉。
 */
class AppAccessDenied(val code: String, val packageName: String?) :
    RuntimeException(messageFor(code, packageName)) {

    companion object {
        /** 区分「应用访问类拒绝」与普通失败。 */
        fun isAppAccessCode(code: String): Boolean =
            code == AppAccessState.CODE_BLOCKED || code.startsWith("app_access_")

        private fun messageFor(code: String, packageName: String?): String = when (code) {
            AppAccessState.CODE_BLOCKED ->
                "Application $packageName is disabled by the user in Application access. Do not retry it, read it again, or try to bypass this with shell or another app. " +
                    "Reconsider the plan: if another app, screen, share target or web route can still achieve the user's goal, continue there. " +
                    "If this app is required, stop and tell the user plainly that this app is disabled in Settings > Application access and can be allowed there; do not repeat this error or show its code."
            AppAccessState.CODE_PROTECTED ->
                "This is an authorization or screen-sharing interface that only the user may operate. Stop and ask the user to handle this step. Do not tap or type here."
            AppAccessState.CODE_UNAVAILABLE ->
                "Application access settings are unavailable, so permission cannot be checked. Do not use phone tools. Tell the user to repair or reset Application access in Settings."
            else ->
                "Cannot verify which application is in front. This operation was not performed and no permission was changed. Observe the screen again to identify the app; if it stays uncertain, stop and ask the user."
        }
    }
}
