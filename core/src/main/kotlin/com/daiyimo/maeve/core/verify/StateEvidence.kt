package com.daiyimo.maeve.core.verify

import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.ReadRegion
import java.text.Normalizer
import kotlin.math.abs

/**
 * 核对回答里明确写出的状态事实:「已开启 WLAN」「消息已发送」「没有删除文件」。
 * 只认同一行实读文字里同时出现的状态和对象;按钮上的「开启」「发送」不算已经生效。
 */
internal object StateEvidence {
    data class Result(
        val matched: Int,
        val total: Int,
        val unbound: Boolean = false,
        val missing: List<String> = emptyList(),
        val outwardMatched: Int = 0,
        val outwardTotal: Int = 0,
    )

    private enum class Family(val aliases: List<String>, val outward: Boolean = false) {
        On(listOf("开启", "打开", "启用", "enabled", "turnedon", "switchedon")),
        Off(listOf("关闭", "禁用", "disabled", "turnedoff", "switchedoff")),
        Send(listOf("发送", "sent"), outward = true),
        Save(listOf("保存", "saved")),
        Submit(listOf("提交", "submitted"), outward = true),
        Publish(listOf("发布", "published"), outward = true),
        Order(listOf("下单", "ordered"), outward = true),
        Pay(listOf("付款", "支付", "paid"), outward = true),
        Transfer(listOf("转账", "transferred"), outward = true),
        Delete(listOf("删除", "deleted"), outward = true),
        Create(listOf("创建", "新建", "created")),
        Add(listOf("添加", "added")),
        Complete(listOf("完成", "completed", "done")),
        Pause(listOf("暂停", "paused")),
        Resume(listOf("恢复", "继续", "resumed")),
        Cancel(listOf("取消", "cancelled", "canceled")),
        Select(listOf("选中", "选定", "selected")),
        Connect(listOf("连接", "connected")),
        Disconnect(listOf("断开", "disconnected")),
        Sync(listOf("同步", "synced")),
        Update(listOf("更新", "updated")),
        Install(listOf("安装", "installed")),
        Download(listOf("下载", "downloaded")),
        Upload(listOf("上传", "uploaded")),
        Copy(listOf("复制", "copied")),
        Move(listOf("移动", "moved")),
        Rename(listOf("重命名", "renamed")),
        Clear(listOf("清空", "cleared"), outward = true),
        Set(listOf("设为", "设置为", "switchedto")),
        Switch(listOf("切换到", "switchedto")),
        ;

        companion object {
            fun of(verb: String): Family? = entries.firstOrNull { family -> family.aliases.any { it == verb } }
        }
    }

    private data class Claim(
        val text: String,
        val family: Family,
        val positive: Boolean,
        val objects: List<String>,
    )

    private val SOURCES = Regex("(?im)^\\s*\\**(?:依据|来源|sources?|evidence)\\**\\s*[:：]")
    private val CLAUSE = Regex("[，,。；;！？!?]|并且|而且|(?<!并)并(?=已|未|没|开|关|保|发|提|删|建|添|完|暂|恢|取|选|连|断|同|更|安|下|上|复|移|清|设|切)")
    private val STATE = Regex(
        "(未|没有|没能)?(?:已经|已)?(开启|打开|启用|关闭|禁用|发送|保存|提交|发布|下单|付款|支付|转账|删除|创建|新建|添加|完成|暂停|恢复|继续|取消|选中|选定|连接|断开|同步|更新|安装|下载|上传|复制|移动|重命名|清空|设置为|设为|切换到)",
    )
    private val FAILURE = Regex("失败|未成功|不成功")
    private val NEGATIVE = listOf("未", "没有", "没能", "失败", "未成功", "不成功", "notsent", "unsent", "failed")
    private val POSITIVE = listOf("已", "已经", "成功", "当前", "状态", "正在", "enabled", "disabled", "completed", "done", "sent", "saved")
    private val TOKEN = Regex("[\\p{L}][\\p{L}\\p{N}._-]*")
    private val LEADING = Regex("^(?:已?(?:给|到|向|在|把|将)|了|的|成功|失败)+")
    private val MEASURE = Regex("^(个|条|篇|张|项|件|次|步|名|位)+")
    private val GENERIC = setOf(
        "我", "你", "他", "她", "它", "我们", "你们", "他们", "已经", "帮你", "给", "到", "向", "在", "把", "将", "的", "了",
        "成功", "失败", "结果", "任务", "页面", "界面", "内容", "消息", "操作", "设置",
    )
    private val OBJECT_ALIASES = mapOf(
        "wifi" to listOf("wifi", "wlan", "无线局域网"),
        "wlan" to listOf("wifi", "wlan", "无线局域网"),
        "蓝牙" to listOf("蓝牙", "bluetooth"),
        "安卓" to listOf("安卓", "android"),
        "android" to listOf("安卓", "android"),
        "微信" to listOf("微信", "wechat"),
        "淘宝" to listOf("淘宝", "taobao"),
    )

    fun check(answer: String, reads: List<Observation>): Result {
        val body = answer.split(SOURCES, limit = 2).first()
        val claims = body.lines().flatMap { line -> CLAUSE.split(line).mapNotNull(::claim) }
            .distinctBy { it.text to it.family to it.positive to it.objects }
        if (claims.isEmpty()) return Result(0, 0)
        val lines = reads.flatMap(::evidenceLines).distinct()
        var matched = 0
        var outwardMatched = 0
        var unbound = false
        val missing = mutableListOf<String>()
        for (c in claims) {
            val ok = lines.any { matches(c, it) }
            if (ok) {
                matched++
                if (c.family.outward) outwardMatched++
            } else {
                val familySeen = lines.any { line -> c.family.aliases.any { it in line } }
                val objectSeen = c.objects.isNotEmpty() && lines.any { line -> c.objects.all { aliases(it).any { a -> a in line } } }
                if (familySeen || objectSeen) unbound = true
                missing += c.text
            }
        }
        return Result(
            matched = matched,
            total = claims.size,
            unbound = unbound,
            missing = missing.take(3),
            outwardMatched = outwardMatched,
            outwardTotal = claims.count { it.family.outward },
        )
    }

    private fun claim(clause: String): Claim? {
        val m = STATE.find(clause) ?: return null
        val family = Family.of(m.groupValues[2]) ?: return null
        val negative = m.groupValues[1].isNotEmpty() || FAILURE.containsMatchIn(clause.substring(m.range.last + 1))
        val before = clause.substring(0, m.range.first).trim()
        val after = clause.substring(m.range.last + 1).trim()
        val objects = objectTokens(after.ifBlank { before })
        return Claim(clause.trim(), family, !negative, objects)
    }

    private fun objectTokens(text: String): List<String> {
        val raw = canon(text)
        return TOKEN.findAll(raw).mapNotNull { m ->
            var token = m.value.replaceFirst(LEADING, "")
            if (raw.getOrNull(m.range.first - 1)?.isDigit() == true) token = token.replaceFirst(MEASURE, "")
            token
        }.filter { it.length >= 2 && it !in GENERIC }.distinct().toList()
    }

    private fun matches(c: Claim, line: String): Boolean {
        if (c.family.aliases.none { it in line }) return false
        val negative = NEGATIVE.any { it in line }
        if (c.positive == negative) return false
        if (c.positive) {
            val explicit = POSITIVE.any { it in line }
            val stateAtEnd = c.objects.isNotEmpty() && c.family.aliases.any { line.endsWith(it) }
            val completed = c.objects.isEmpty() && line.endsWith("完成") && line.length > 2
            if (!explicit && !stateAtEnd && !completed) return false
        }
        return c.objects.isEmpty() || c.objects.all { objectKey -> aliases(objectKey).any { it in line } }
    }

    private fun aliases(key: String): List<String> = OBJECT_ALIASES[key].orEmpty().ifEmpty { listOf(key) }

    private fun evidenceLines(read: Observation): List<String> =
        (read.text.lines().map(::canon) + regionRows(read.regions).map(::canon)).filter { it.isNotEmpty() }

    private fun regionRows(regions: List<ReadRegion>): List<String> {
        if (regions.isEmpty()) return emptyList()
        data class Row(val regions: MutableList<ReadRegion>, var center: Double, var height: Int)
        val rows = mutableListOf<Row>()
        for (region in regions.sortedBy { (it.bounds.getOrNull(1) ?: 0) + (it.bounds.getOrNull(3) ?: 0) }) {
            if (region.bounds.size != 4 || region.text.isBlank()) continue
            val top = region.bounds[1]
            val bottom = region.bounds[3]
            if (bottom <= top) continue
            val center = (top + bottom) / 2.0
            val height = bottom - top
            val row = rows.lastOrNull()?.takeIf { abs(center - it.center) <= maxOf(it.height, height) * 0.6 + 2 }
            if (row == null) rows += Row(mutableListOf(region), center, height)
            else {
                val count = row.regions.size
                row.regions += region
                row.center = (row.center * count + center) / (count + 1)
                row.height = maxOf(row.height, height)
            }
        }
        return rows.map { row -> row.regions.sortedBy { it.bounds[0] }.joinToString(" ") { it.text } }
    }

    private fun canon(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace("**", "").replace("__", "").replace("`", "")
        .lowercase().filter { it.isLetterOrDigit() }
}
