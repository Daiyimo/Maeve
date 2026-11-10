package com.daiyimo.maeve.core.risk

/**
 * 密码 / 验证码一类输入框的认法(取 dsh-go SensitiveWords 的设计,不搬代码):
 * 标签、内容描述、提示语里的字样,或资源 ID 拆出的单词。界面采集据此把输入框标成
 * [com.daiyimo.maeve.core.uistate.UiElement.secret],执行入口据此把要打字进去的一步交还用户。
 */
object SecretWords {
    /** 标签里出现就算的字(输入框的说明文字,不是内容)。 */
    val LABELS: List<String> = listOf(
        "支付密码", "密码", "验证码", "校验码", "动态码", "安全码", "cvv", "cvc",
        "password", "passcode", "verification code", "one-time code", "otp",
    )

    /** 资源 ID 拆词后命中的单词(`et_pwd`、`smsCode`)。 */
    val ID_WORDS: Set<String> = setOf(
        "password", "passwd", "pwd", "passcode", "otp", "cvv", "cvc",
        "smscode", "verifycode", "authcode", "paypassword", "paypwd",
    )

    /** 命中的字;不是秘密输入框时为 null。[label] 是显示字(文字、内容描述、提示语),[viewId] 是资源 ID。 */
    fun hit(label: String?, viewId: String?): String? {
        val text = label?.trim()?.lowercase().orEmpty()
        if (text.isNotEmpty()) {
            LABELS.firstOrNull { it in text }?.let { return it }
        }
        return idWords(viewId).firstOrNull { it in ID_WORDS }
    }

    /**
     * 资源 ID 尾部按非字母数字和驼峰拆开,小写,再捎上每对相邻词的拼接(`verify_code` 也要认得出)。
     */
    fun idWords(viewId: String?): List<String> {
        if (viewId.isNullOrBlank()) return emptyList()
        val tail = viewId.substringAfterLast(":id/").substringAfterLast('/')
        val words = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                words += current.toString().lowercase()
                current.setLength(0)
            }
        }
        tail.forEachIndexed { index, char ->
            when {
                !char.isLetterOrDigit() -> flush()
                char.isUpperCase() && index > 0 && tail[index - 1].isLowerCase() -> {
                    flush(); current.append(char)
                }
                else -> current.append(char)
            }
        }
        flush()
        return words + words.zipWithNext { a, b -> a + b }
    }

    /**
     * 这个输入框是不是秘密框:系统标了密码,或可按词认出([hit] 标签 / 提示语 / 资源 ID)。
     * [editable] 为 false 时只认系统标的那一种——「获取验证码」按钮不算秘密框。
     * 界面采集据此标 [com.daiyimo.maeve.core.uistate.UiElement.secret],打字的结果也据此
     * 决定读回与运行记录里留不留原文。
     */
    fun secretField(label: String?, hint: String?, viewId: String?, editable: Boolean, password: Boolean): Boolean =
        password || (editable && (hit(label, viewId) != null || hit(hint, null) != null))
}

/**
 * 银行卡号、身份证号:入模(界面文字、运行记录)之前打码,只留末 4 位。
 * 只认这两种:16–19 位连续数字(常带 4 位一组的分隔)、18 位身份证(含末位 X)。
 * 电话号码(11 位)、时间戳一类的短数字不动;认不准的多半也会被拦下——宁可多打码,不漏号码。
 */
object NumberMask {
    /** 打码后留下的末几位。 */
    const val KEEP = 4

    private val CARD_RUN = Regex("(?<![0-9])([0-9][ -]?){15,18}[0-9](?![0-9])")
    private val ID_CARD = Regex("(?<![0-9A-Za-z])[0-9]{17}[0-9Xx](?![0-9A-Za-z])")

    /** 把 [text] 里的银行卡号、身份证号换成末 4 位(前面打码);没有号码时原样返回。 */
    fun mask(text: String): String {
        if (text.none { it.isDigit() }) return text
        return ID_CARD.replace(text) { masked(it.value) }
            .let { CARD_RUN.replace(it) { run -> masked(run.value) } }
    }

    /** 保留末 [KEEP] 位数字,其余换成 •;分组用的分隔号不去(认得出还是银行卡)。 */
    private fun masked(run: String): String {
        val total = run.count { it.isDigit() }
        var seen = 0
        return buildString(run.length) {
            for (char in run) {
                if (!char.isDigit()) {
                    append(char)
                    continue
                }
                seen++
                append(if (seen > total - KEEP) char else '•')
            }
        }
    }
}
