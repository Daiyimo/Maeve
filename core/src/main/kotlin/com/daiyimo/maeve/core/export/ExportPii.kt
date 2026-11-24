package com.daiyimo.maeve.core.export

import com.daiyimo.maeve.core.record.Redactor

/** 可选的个人联系信息与私有路径脱敏;密钥始终由 ReportExportSanitizer 先处理。 */
internal object ExportPii {
    fun mask(value: String): String = value
        .replace(EMAIL, Redactor.MASK)
        .replace(MAINLAND_PHONE, Redactor.MASK)
        .replace(INTERNATIONAL_PHONE, Redactor.MASK)
        .replace(ID_CARD, Redactor.MASK)
        .replace(BANK_CARD, Redactor.MASK)
        .replace(IPV4, Redactor.MASK)
        .replace(PRIVATE_PATH, Redactor.MASK)

    private val EMAIL = Regex("""[A-Za-z0-9.!#$%&'*+/=?^_{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+""")
    private val MAINLAND_PHONE = Regex("""(?<!\d)(?:\+?86[ -]?)?1[3-9]\d{9}(?!\d)""")
    private val INTERNATIONAL_PHONE = Regex("""(?<!\d)\+\d[\d ()-]{7,}\d(?!\d)""")
    private val ID_CARD = Regex("""(?<!\d)\d{17}[\dXx](?!\d)""")
    private val BANK_CARD = Regex("""(?<!\d)\d{16,19}(?!\d)""")
    private val IPV4 = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])""")
    private val PRIVATE_PATH = Regex("""(?<!\w)(?:/data/user/\d+/|/storage/emulated/\d+/|/sdcard/)[^\s"'<>]+""")
}
