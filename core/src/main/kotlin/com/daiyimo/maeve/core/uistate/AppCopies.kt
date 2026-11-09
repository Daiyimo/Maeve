package com.daiyimo.maeve.core.uistate

/**
 * 另一份用户(分身、工作资料这类资料)里装着的同一个 App:[packageName] 是包名,[userId] 是那份用户的编号
 * (打开时靠它指定),[userName] 是系统给那份用户的名字(跟随系统语言,不写死;读不到时为空)。
 */
data class AppCopy(val packageName: String, val userId: Int, val userName: String) {
    /** 展示给模型看的称呼,形如「App 名 (another copy: 那份用户的名字)」。 */
    fun label(appLabel: String): String = "$appLabel (another copy: ${userName.ifBlank { "user $userId" }})"
}

/**
 * 哪些别的用户里的 App 能交给 Maeve 打开,以及最终列出来的条目。纯逻辑,内置助手(判用户可用性)与 Maeve(列出、核对)共用。
 *
 * 判一份用户能不能用,依据的是安卓多用户机制给出的信息:UserManager 报的 UserInfo、UserProperties,
 * 以及系统给标记起的名字,完全不看用户自己设的名字。按顺序看:
 * 1. 先一票否决,后面不用看:用户类型带 private;系统标记名含 HIDDEN 或 PRIVATE;UserProperties 标明对别的 App 隐藏;
 *    访客、演示、临时、已停用(标记名 GUEST / DEMO / EPHEMERAL / DISABLED,读不到标记名时退回原版的这几个标记位)。
 *    私密空间之类的一律不列也不打开,哪怕它正在运行、且与当前用户连着。
 * 2. 正在运行且已解锁,不在安静模式(工作资料被暂停时就是安静模式)。
 * 3. 与当前用户连着:资料组(profileGroupId)和当前用户同组,或系统报的上级用户(parentId)就是当前用户。
 * 4. 还要看得出是分身或工作资料:用户类型带 clone,或标记名含 CLONE,或 UserProperties 说它与上级用户共用媒体,
 *    或是原版的工作资料类型 android.os.usertype.profile.MANAGED。
 * 任何一项读不到都按不满足处理:宁可少列一份,也不去碰用户没打算共享的资料。
 */
object AppCopies {
    // UserInfo.FLAG_*(原版的值);只在读不到系统的标记名时用
    const val FLAG_GUEST = 0x4
    const val FLAG_DISABLED = 0x40
    const val FLAG_QUIET_MODE = 0x80
    const val FLAG_EPHEMERAL = 0x100
    const val FLAG_DEMO = 0x200
    private const val EXCLUDED_FLAGS = FLAG_GUEST or FLAG_DISABLED or FLAG_EPHEMERAL or FLAG_DEMO
    private val EXCLUDED_NAMES = setOf("GUEST", "DEMO", "EPHEMERAL", "DISABLED")

    /** UserInfo.NO_PROFILE_GROUP_ID(即 UserHandle.USER_NULL):这份用户不属于任何资料组。 */
    const val NO_PROFILE_GROUP = -10000
    const val TYPE_MANAGED = "android.os.usertype.profile.MANAGED"

    /**
     * 内置助手读到的一份用户:[flags] 是 UserInfo 的标记位,[flagNames] 是系统给这些标记起的名字
     * (取自 UserInfo.flagsToString,含系统自定义的;读不到为 null),[type] 是用户类型(读不到为 null),
     * [profileGroupId] 是资料组,[parentId] 是系统报的上级用户(没有为 null),[running] 是否正在运行,
     * [unlocked] 是否已解锁,[quiet] 是否处于安静模式,[mediaShared] 是 UserProperties.isMediaSharedWithParent,
     * [hidden] 是 UserProperties 标明对别的 App 隐藏;后两项读不到为 null。
     */
    data class User(
        val id: Int,
        val flags: Int,
        val type: String?,
        val profileGroupId: Int,
        val running: Boolean,
        val unlocked: Boolean = running,
        val quiet: Boolean = false,
        val mediaShared: Boolean? = null,
        val hidden: Boolean? = null,
        val flagNames: List<String>? = null,
        val parentId: Int? = null,
    )

    /** 一份用户的可用性结论;只有 [Copy] 代表它里面的 App 能打开。 */
    enum class Verdict { Copy, Self, Private, Flags, Stopped, Locked, Quiet, NotLinked, NotCopy }

    fun verdict(u: User, current: Int, group: Int): Verdict {
        val type = u.type.orEmpty()
        val names = u.flagNames.orEmpty().map { it.uppercase() }
        return when {
            u.id == current -> Verdict.Self
            privateKind(u, type, names) -> Verdict.Private
            excludedKind(u, names) -> Verdict.Flags
            !u.running -> Verdict.Stopped
            !u.unlocked -> Verdict.Locked
            paused(u, names) -> Verdict.Quiet
            !linked(u, current, group) -> Verdict.NotLinked
            copyLike(u, type, names) -> Verdict.Copy
            else -> Verdict.NotCopy
        }
    }

    /** 私密空间一类:类型带 private、标了对别的 App 隐藏,或标记名带 HIDDEN / PRIVATE。 */
    private fun privateKind(u: User, type: String, names: List<String>): Boolean =
        type.contains("private", ignoreCase = true) || u.hidden == true || names.any { "HIDDEN" in it || "PRIVATE" in it }

    /** 访客、演示、临时、已停用:有标记名时按名字认,没有时才退回原版的标记位。 */
    private fun excludedKind(u: User, names: List<String>): Boolean =
        if (u.flagNames != null) names.any { it in EXCLUDED_NAMES } else u.flags and EXCLUDED_FLAGS != 0

    /** 安静模式:UserProperties 说了算,或标记位、标记名里有。 */
    private fun paused(u: User, names: List<String>): Boolean =
        u.quiet || u.flags and FLAG_QUIET_MODE != 0 || "QUIET_MODE" in names

    /** 和当前用户连着:同资料组(且确实在组里),或上级用户就是当前用户。 */
    private fun linked(u: User, current: Int, group: Int): Boolean =
        u.profileGroupId == group && group != NO_PROFILE_GROUP || u.parentId == current

    /** 分身或工作资料:类型带 clone、标记名带 CLONE、与上级用户共用媒体,或是原版的工作资料类型。 */
    private fun copyLike(u: User, type: String, names: List<String>): Boolean =
        type.contains("clone", ignoreCase = true) || names.any { "CLONE" in it } || u.mediaShared == true || type == TYPE_MANAGED

    /** 当前用户 [current] 所在的资料组;该用户读不到或不在任何组里时,就它自己一个组。 */
    fun groupOf(current: Int, users: List<User>): Int =
        users.firstOrNull { it.id == current }?.profileGroupId?.takeIf { it != NO_PROFILE_GROUP } ?: current

    /** 其中哪些别的用户里的 App 可以打开,按 [users] 的先后列出编号。 */
    fun usable(current: Int, users: List<User>): List<Int> {
        val group = groupOf(current, users)
        return users.filter { verdict(it, current, group) == Verdict.Copy }.map { it.id }
    }

    /** 给诊断信息的一行:每份别的用户的编号、标记(和系统给的标记名)、类型、资料组、上级、判定;不含名字和装了什么。 */
    fun summary(current: Int, users: List<User>): String {
        val group = groupOf(current, users)
        val others = users.filter { it.id != current }
        val usable = others.count { verdict(it, current, group) == Verdict.Copy }
        if (others.isEmpty()) return "no other users"
        return "${others.size} other, $usable usable: " + others.joinToString("; ") { u -> describe(u, current, group) }
    }

    /** 一份用户在诊断行里的样子:编号、标记、类型、资料组、上级、运行状态、判定。 */
    private fun describe(u: User, current: Int, group: Int): String =
        "${u.id} flags=0x${Integer.toHexString(u.flags)}" + (u.flagNames?.let { " (${it.joinToString("|")})" } ?: "") +
            " type=${u.type ?: "?"} group=${u.profileGroupId}" + (u.parentId?.let { " parent=$it" } ?: "") +
            stateOf(u) + " -> ${verdict(u, current, group).name.lowercase()}"

    /** 运行状态:停了、已解锁、锁着。 */
    private fun stateOf(u: User): String =
        if (!u.running) " stopped" else if (u.unlocked) " unlocked" else " locked"

    /** `dumpsys user` 里一份用户的几项:系统给标记起的名字(括号里的)、上级用户(parentId)。 */
    data class Dumped(val flagNames: List<String>?, val parentId: Int?)

    private val DUMP_USER = Regex("""UserInfo\{(\d+):""")
    private val DUMP_PARENT = Regex("""parentId=(-?\d+)""")
    private val DUMP_FLAGS = Regex("""^\s*Flags:\s*-?\d+\s*\(([^)]*)\)""")

    /** 读 `dumpsys user`:每份用户(编号)的标记名和上级用户;读不到标记名的为 null。反射读不到标记名时用。 */
    fun parseDumpsys(output: String): Map<Int, Dumped> {
        val out = linkedMapOf<Int, Dumped>()
        var userId: Int? = null
        for (line in output.lineSequence()) {
            val user = DUMP_USER.find(line)
            if (user != null) {
                val uid = user.groupValues[1].toInt()
                userId = uid
                out[uid] = Dumped(null, DUMP_PARENT.find(line)?.groupValues?.get(1)?.toInt())
                continue
            }
            val uid = userId ?: continue
            val flags = DUMP_FLAGS.find(line) ?: continue
            out[uid] = out.getValue(uid).copy(flagNames = flags.groupValues[1].split('|').map(String::trim).filter(String::isNotEmpty))
        }
        return out
    }

    /**
     * 列出另一份 App:[apps] 是当前用户能打开的 App(包名, 显示名),[users] 是可用的那几份用户(带名字),
     * [packages] 是每份用户里装了的包。只收当前用户也装着的(要与 [apps] 对得上),顺序按 [apps]。
     */
    fun copies(apps: List<Pair<String, String>>, users: List<UserProfile>, packages: Map<Int, Set<String>>): List<AppCopy> {
        val out = mutableListOf<AppCopy>()
        for ((pkg, _) in apps) {
            for (u in users) {
                if (pkg in packages[u.id].orEmpty()) out += AppCopy(pkg, u.id, u.name)
            }
        }
        return out
    }
}
