package com.daiyimo.maeve.intercept

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 一键接入发给 amoo 的那段说明:路径跟着包名走、覆盖升级不留旧版、装完写版本标记、试任务必须带上。 */
class AmooSetupTest {

    @Test
    fun pointsAtBothMailboxesOfThisPackage() {
        val text = setupInstruction("com.daiyimo.maeve", 2)
        assertTrue(text.contains("/sdcard/Android/media/com.daiyimo.maeve/amoo/in/"))
        assertTrue(text.contains("/sdcard/Android/media/com.daiyimo.maeve/amoo/out/"))
    }

    @Test
    fun keepsTheRulesThatMakeAmooDelegate() {
        val text = setupInstruction("com.example.maeve", 2)
        assertTrue(text.contains("maeve-bridge"))
        assertTrue(text.contains("一律交给手机端 Maeve 执行"))
        assertTrue(text.contains("不要自己尝试"))
        assertTrue(text.contains("/sdcard/Android/media/com.example.maeve/amoo/in/"))
    }

    @Test
    fun asksForASmokeTask() {
        val text = setupInstruction("com.daiyimo.maeve", 2)
        assertTrue(text.contains("只回答三个字:你好呀"))
        assertTrue(text.contains("15 分钟"))
    }

    @Test
    fun demandsOverwriteInsteadOfSecondCopy() {
        val text = setupInstruction("com.daiyimo.maeve", 7)
        assertTrue(text.contains("version: 7"))
        assertTrue(text.contains("直接覆盖"))
        assertTrue(text.contains("不要新建"))
        assertTrue(text.contains("rm -rf"))
    }

    @Test
    fun pinsTheVersionMarker() {
        val text = setupInstruction("com.daiyimo.maeve", 7)
        assertTrue(text.contains("/sdcard/Android/media/com.daiyimo.maeve/amoo/skill.json"))
        assertTrue(text.contains("\"version\":7"))
        assertEquals(7, parseSkillVersion(text.substringAfter("{")))
    }

    @Test
    fun readsVersionFromMarker() {
        assertEquals(2, parseSkillVersion("{\"skill\":\"maeve-bridge\",\"version\":2}"))
        assertEquals(11, parseSkillVersion("{\n  \"version\" : 11\n}"))
        assertNull(parseSkillVersion("{}"))
        assertNull(parseSkillVersion("not json"))
    }
}