package com.daiyimo.maeve.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExportSanitizerTest {
    private val secret = "sk-" + "a1B2c3D4e5F6g7H8i9J0k1L2"
    private val email = "person@example.com"
    private val phone = "13800138000"
    private val internationalPhone = "+44 20 7946 0958"
    private val privatePath = "/data/user/0/com.daiyimo.maeve/files/private.txt"
    private val id = "11010519491231002X"
    private val input = ReportExportInput(
        title = "标题 $secret $email password=plainsecret",
        summary = listOf("结局 $secret"),
        preface = listOf("核查 $phone"),
        criteria = listOf("标准 $secret"),
        body = "正文 $secret [登录](https://user:pass@example.com/cb?access_token=abc&email=$email) " +
            "Password: hunter2 password = spacedsecret Authorization: Basic dXNlcjpwYXNz " +
                "password=\"quoted secret\" password=\"a\\\"escapedSecret\"",
        headings = listOf("页面 $secret $email"),
        urls = listOf("https://example.com/download?token=abc&sig=xyz", "https://$email/a"),
        attachmentNames = listOf("附件-$secret.txt"),
        toolTexts = listOf("""{"password":"hunter2","url":"https://example.com/?sessionId=abc"} $phone $id"""),
        notes = listOf("截图是原始字节，未匿名化。", "$internationalPhone $privatePath"),
    )

    @Test fun defaultSecretsCoverEveryTextSurfaceButKeepPii() {
        val safe = ReportExportSanitizer.sanitize(input)
        val all = safe.toString()
        assertFalse(all.contains(secret))
        assertFalse(all.contains("access_token=abc"))
        assertFalse(all.contains("token=abc"))
        assertFalse(all.contains("sig=xyz"))
        assertFalse(all.contains("sessionId=abc"))
        assertFalse(all.contains("hunter2"))
        assertFalse(all.contains("plainsecret"))
        assertFalse(all.contains("spacedsecret"))
        assertFalse(all.contains("dXNlcjpwYXNz"))
        assertFalse(all.contains("quoted secret"))
        assertFalse(all.contains("escapedSecret"))
        assertTrue(safe.body.contains("password=***"))
        assertTrue(safe.body.contains("Password: ***"))
        assertTrue(safe.body.contains("password = ***"))
        assertTrue(safe.body.contains("Authorization: ***"))
        assertFalse(all.contains("user:pass@"))
        assertTrue(all.contains(email))
        assertTrue(all.contains(phone))
        assertTrue(all.contains(internationalPhone))
        assertTrue(all.contains(privatePath))
        assertTrue(safe.body.contains("access_token=***"))
        assertTrue(safe.urls[0].contains("token=***"))
        assertTrue(safe.urls[1].contains("***@example.com"))
    }

    @Test fun secretsAndPiiAlsoMasksCommonContactAndIdentityFields() {
        val safe = ReportExportSanitizer.sanitize(input, ReportExportRedaction.SecretsAndPii)
        val all = safe.toString()
        for (value in listOf(secret, email, phone, internationalPhone, privatePath, id, "hunter2", "access_token=abc")) {
            assertFalse(value, all.contains(value))
        }
        assertTrue(all.contains("***"))
    }

}
