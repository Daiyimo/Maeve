package com.daiyimo.maeve.core.uistate

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 系统问「打开哪一个」时列出的一项。[clone]:是不是另一份用户里的那个(分身、工作资料);看不出时为 null。 */
data class ChoiceOption(val label: String, val clone: Boolean?)

/** 打开 App 后系统弹出的「打开哪一个」:[packageName] 是发问那个窗口所在的包,[options] 是提到这个 App 名的选项。 */
data class LaunchChoice(val packageName: String, val options: List<ChoiceOption>) {
    fun toJson(): JsonObject = buildJsonObject {
        put("package", packageName)
        put("options", buildJsonArray {
            for (o in options) add(buildJsonObject { put("label", o.label); o.clone?.let { put("clone", it) } })
        })
    }
}

/**
 * 认「打开哪一个」这回事:打开 App 之后,最上面换成了别的系统包的窗口,且窗口里的选项提到了这个 App 的名字。
 * 同一个 App 装在两份用户里(分身、工作资料)时,有的系统先问用哪一份;认不出来的话会当成没打开,模型便一遍遍重开、一遍遍被问。
 */
object LaunchChooser {
    /**
     * 窗口元素里提到 App 名 [appLabel] 的可点选项;一个也没有为 null(不是问打开哪一个)。只认可点的:
     * 权限框一类的说明文字也会提到 App 名,可它的按钮不带 App 名。
     * [profiles] 是这台手机另外几份用户的名字:选项里带着它的是另一份,名字正好是 App 名的是本来那份。
     */
    fun options(appLabel: String, elements: List<UiElement>, profiles: List<String>): List<ChoiceOption>? {
        val appKey = key(appLabel)
        val matches = optionIndices(appLabel, elements)?.map { elements[it] } ?: return null
        val profileKeys = profiles.map(::key).filter { it.isNotEmpty() }
        val tallies = matches.groupingBy { key(it.name) }.eachCount()
        return matches.map { e ->
            ChoiceOption(e.name.replace('\n', ' ').trim(), cloneOf(key(e.name), appKey, profileKeys, tallies))
        }
    }

    /** 这项是不是另一份用户里的那个:名字里带着别的用户的是;和 App 名一样的是本来那份;两项同名分不出,为 null。 */
    private fun cloneOf(nameKey: String, appKey: String, profileKeys: List<String>, tallies: Map<String, Int>): Boolean? = when {
        profileKeys.any { nameKey.contains(it) } -> true
        // 两项名字一模一样(只靠角标区分):光看字分不出哪个是本来那份
        tallies.getValue(nameKey) > 1 -> null
        nameKey == appKey -> false
        else -> null
    }

    /** [options] 认出的那几个选项在 [elements] 里的下标(先后不变);不是在问打开哪一个时为 null。点选项时用。 */
    fun optionIndices(appLabel: String, elements: List<UiElement>): List<Int>? {
        val appKey = key(appLabel)
        if (appKey.isEmpty()) return null
        return elements.indices.filter { elements[it].let { e -> e.clickable && e.name.isNotBlank() && key(e.name).contains(appKey) } }.ifEmpty { null }
    }

    private fun key(s: String) = s.filterNot(Char::isWhitespace).lowercase()
}

/** 这台手机的一份用户(`pm list users` 输出的一行):[id] 用户号,[name] 名字(如分身、工作资料),[running] 正在运行。 */
data class UserProfile(val id: Int, val name: String, val running: Boolean)

/** 读 `pm list users` 与 `pm list packages` 的输出。 */
object AppProfiles {
    private val USER = Regex("""UserInfo\{(\d+):(.*):[0-9a-fA-F]+\}(\s+running)?""")
    private val PACKAGE = Regex("""^package:([A-Za-z0-9_.]+)\s*$""")

    fun parseUsers(output: String): List<UserProfile> = output.lineSequence().mapNotNull { line ->
        val match = USER.find(line) ?: return@mapNotNull null
        UserProfile(match.groupValues[1].toInt(), match.groupValues[2].trim(), match.groupValues[3].isNotEmpty())
    }.toList()

    fun parsePackages(output: String): Set<String> =
        output.lineSequence().mapNotNull { PACKAGE.find(it.trim())?.groupValues?.get(1) }.toSet()

    /** 每个包还装在哪几份别的用户里:[others] 是 用户 → 那份用户装的包。 */
    fun alsoIn(others: Map<UserProfile, Set<String>>): Map<String, List<String>> {
        val byPackage = linkedMapOf<String, MutableList<String>>()
        for ((user, packages) in others) for (p in packages) byPackage.getOrPut(p) { mutableListOf() } += user.name.ifEmpty { "user ${user.id}" }
        return byPackage
    }
}
