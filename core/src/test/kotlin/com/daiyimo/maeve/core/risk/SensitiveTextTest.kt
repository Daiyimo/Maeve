package com.daiyimo.maeve.core.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 密码 / 验证码输入框的认法与号码打码(取 dsh-go SensitiveWords 的设计,不搬代码):
 * 认得出才拦(可输入元素上的字样与资源 ID 拆词),号码只留末 4 位,短数字一律不动。
 */
class SensitiveTextTest {
    // ---- SecretWords.hit:标签 / 提示语里的字样 ----

    @Test fun secretLabelsHit() {
        for (label in listOf("请输入密码", "支付密码", "验证码", "短信验证码", "动态密码", "安全码", "CVV", "Password", "One-time code", "OTP")) {
            assertTrue(label, SecretWords.hit(label, null) != null)
        }
    }

    @Test fun ordinaryLabelsDoNotHit() {
        for (label in listOf("明天见", "请输入手机号", "确认", "搜索", "用户名")) {
            assertNull(label, SecretWords.hit(label, null))
        }
    }

    @Test fun blankLabelFallsBackToTheViewId() {
        assertNull(SecretWords.hit(null, "com.x:id/et_message"))
        assertTrue(SecretWords.hit(null, "com.x:id/et_pwd") != null)
    }

    // ---- SecretWords.idWords:资源 ID 拆词 ----

    @Test fun resourceIdsSplitIntoWords() {
        assertEquals(listOf("et", "pwd", "etpwd"), SecretWords.idWords("com.x:id/et_pwd"))
        assertEquals(listOf("sms", "code", "smscode"), SecretWords.idWords("com.x:id/smsCode"))
        assertEquals(listOf("verify", "code", "verifycode"), SecretWords.idWords("com.x:id/verify_code"))
    }

    @Test fun resourceIdsWithoutSecretWordsMiss() {
        assertNull(SecretWords.hit(null, "com.x:id/username"))
        assertNull(SecretWords.hit(null, "com.x:id/et_message"))
        assertTrue(SecretWords.idWords(null).isEmpty())
        assertTrue(SecretWords.idWords("").isEmpty())
    }

    // ---- SecretWords.secretField:只有可输入元素才算 ----

    @Test fun passwordFieldsAreSecretWhateverTheLabelSays() {
        assertTrue(SecretWords.secretField("明天见", null, null, editable = true, password = true))
    }

    @Test fun theGetCodeButtonIsNotASecretField() {
        // 「获取验证码」是按钮:字样命中,但不是可输入元素,不算
        assertFalse(SecretWords.secretField("获取验证码", null, null, editable = false, password = false))
        assertFalse(SecretWords.secretField("发送验证码", null, null, editable = false, password = false))
    }

    @Test fun editableFieldsWithSecretWordsLabelsHintsOrIdsAreSecret() {
        assertTrue(SecretWords.secretField("请输入验证码", null, null, editable = true, password = false))
        assertTrue(SecretWords.secretField("明天见", "请输入密码", null, editable = true, password = false))
        assertTrue(SecretWords.secretField(null, null, "com.x:id/et_pwd", editable = true, password = false))
        assertTrue(SecretWords.secretField("✉️", "验证码", null, editable = true, password = false))
    }

    @Test fun ordinaryEditableFieldsAreNotSecret() {
        assertFalse(SecretWords.secretField("明天见", "说点什么", "com.x:id/et_message", editable = true, password = false))
        assertFalse(SecretWords.secretField(null, null, null, editable = true, password = false))
    }

    // ---- NumberMask:银行卡号 / 身份证号打码 ----

    @Test fun cardNumbersKeepOnlyTheLastFourDigits() {
        assertEquals("•••••••••••••••0123", NumberMask.mask("6222021234567890123"))
        assertEquals("•••• •••• •••• 5678", NumberMask.mask("6222 0200 1234 5678"))
        assertEquals("••••-••••-••••-5678", NumberMask.mask("6222-0200-1234-5678"))
    }

    @Test fun idCardsKeepOnlyTheLastFourDigits() {
        assertEquals("•••••••••••••7801X", NumberMask.mask("11010119900307801X"))
        assertEquals("••••••••••••••8012", NumberMask.mask("110101199003078012"))
    }

    @Test fun shortNumbersAreLeftAlone() {
        // 电话(11 位)、验证码一类的短数字、金额、年份:不动
        for (text in listOf("13800138000", "482913", "¥25", "2026", "共 3 件", "12:30")) {
            assertEquals(text, NumberMask.mask(text))
        }
    }

    @Test fun numbersInsideSentencesAreMasked() {
        assertEquals("卡号 •••••••••••••••0123 过期", NumberMask.mask("卡号 6222021234567890123 过期"))
        assertEquals("证件 •••••••••••••7801X 已登记", NumberMask.mask("证件 11010119900307801X 已登记"))
    }

    @Test fun textWithoutDigitsComesBackUnchanged() {
        val text = "明天见"
        assertEquals(text, NumberMask.mask(text))
        assertTrue(NumberMask.mask("").isEmpty())
    }

    @Test fun overLongDigitRunsAreNotTouched() {
        // 20 位连续数字认不准是什么:不按号码处理,原样留下
        assertEquals("12345678901234567890", NumberMask.mask("12345678901234567890"))
    }
}
