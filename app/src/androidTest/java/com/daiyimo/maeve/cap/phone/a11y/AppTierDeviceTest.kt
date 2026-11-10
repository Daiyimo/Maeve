package com.daiyimo.maeve.cap.phone.a11y

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport.TEST_PACKAGE
import com.daiyimo.maeve.core.action.ConfirmDecision
import com.daiyimo.maeve.core.action.Decision
import com.daiyimo.maeve.core.action.Origin
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.risk.AppTier
import com.daiyimo.maeve.core.risk.AppTiers
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机测试:应用名单三档、敏感页保护、敏感词打码落在真机上的样子。
 *
 * 和单元测试的分工:单元测试用假屏幕把三档策略、秘密框认定、打码规则逐条钉死;
 * 这里用真屏(支付宝、微信)和测试页走同一条链——observe 读屏登记快照、判定按 snapshotId
 * 找回那一屏再下结论——验证的是真机采集(ScreenReader)与名单、判定的接缝:
 * 真支付宝页面判成默认不碰、真微信页面判成每步确认、真输入框的 secret 标记和号码打码。
 * 只判定、不执行:支付 App 上一个动作都不真做。
 */
@RunWith(AndroidJUnit4::class)
class AppTierDeviceTest {
    private val agent = DeviceSupport.newAgent()
    private val screen get() = agent.screen
    private val tools = toolDispatcher(screen)
    /** 和线上一样的判定入口:带着确认关卡(询问时),不自主执行。 */
    private val decisions = ConfirmDecision(tools, ConfirmationGate(), selfPackage = null)

    @After
    fun tearDown() = agent.release()

    // ---------- 辅助 ----------

    private suspend fun snapUntil(timeoutMs: Long = 10_000, test: (ScreenSnapshot) -> Boolean): ScreenSnapshot {
        val end = System.currentTimeMillis() + timeoutMs
        var s = screen.snapshot()
        while (!test(s) && System.currentTimeMillis() < end) {
            Thread.sleep(300)
            s = screen.snapshot()
        }
        assertTrue("screen never matched: ${s.packageName} ${s.elements.map { it.name }}", test(s))
        return s
    }

    /** 打开 App,等到它的窗口在 Maeve 的屏上最上面、内容也读得到(启动页可能先上来一会儿,上面什么都没有)。 */
    private suspend fun openUntil(pkg: String): ScreenSnapshot {
        val r = screen.openApp(pkg)
        assertTrue("openApp($pkg): $r", r is ActionResult.Ok)
        return snapUntil(30_000) { it.packageName == pkg && it.elements.isNotEmpty() }
    }

    /** 读一屏:经 observe 工具读,快照据此登记进会话(判定按 snapshotId 从会话里找回它)。 */
    private suspend fun observe(): ToolExecution =
        tools.executeRead(Part.ToolCall("obs", "phone_observe", "{}")).also {
            assertEquals("observe failed: ${it.content}", "true", it.content["ok"]?.jsonPrimitive?.contentOrNull)
        }

    private val ToolExecution.snapshotId: String get() = content["snapshotId"]!!.jsonPrimitive.content

    private fun ToolExecution.elementNames(): List<String> =
        content["elements"]!!.jsonArray.map { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty() }

    /** 元素表里第一个像按钮的;没有就第一个(判定不看点的是什么,有个编号即可)。 */
    private fun ToolExecution.firstRef(): String {
        val arr = content["elements"]!!.jsonArray
        assertTrue("observe returned no elements", arr.isNotEmpty())
        val button = arr.firstOrNull { it.jsonObject["role"]?.jsonPrimitive?.contentOrNull?.contains("Button") == true }
        return (button ?: arr.first()).jsonObject["ref"]!!.jsonPrimitive.content
    }

    private fun ToolExecution.refOf(name: String): String? =
        content["elements"]!!.jsonArray.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull == name }
            ?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull

    private fun tapArgs(snapshotId: String, ref: String) = buildJsonObject {
        put("ref", JsonPrimitive(ref)); put("snapshotId", JsonPrimitive(snapshotId))
    }

    private fun typeArgs(snapshotId: String, ref: String, text: String) = buildJsonObject {
        put("ref", JsonPrimitive(ref)); put("snapshotId", JsonPrimitive(snapshotId)); put("text", JsonPrimitive(text))
    }

    private fun ScreenSnapshot.field(name: String): UiElement =
        elements.firstOrNull { it.name == name } ?: error("no field named 「$name」: ${elements.map { it.name }}")

    private suspend fun installed(pkg: String): Boolean = screen.listApps().any { it.first == pkg }

    // ---------- 测试 ----------

    /**
     * 支付类 App(支付宝)在默认不碰名单里:读这一屏照常(先看得见,才能决定要不要交还用户),
     * 写操作——点一下、往里打字——一律判成不做;判定按真机上的真快照下,和线上同一条链。
     */
    @Test
    fun paymentAppIsReadButNeverWrittenTo() = runBlocking<Unit> {
        assumeTrue("alipay is not installed", installed(ALIPAY))
        val s = openUntil(ALIPAY)
        assertEquals(AppTier.Deny, AppTiers.tierOf(s.packageName))
        val exec = observe()
        val sid = exec.snapshotId
        val ref = exec.firstRef()
        assertEquals(Decision.DeniedApp, decisions.judge("phone_tap", tapArgs(sid, ref), Origin.Model(1)))
        assertEquals(Decision.DeniedApp, decisions.judge("phone_type_text", typeArgs(sid, ref, "13800138000"), Origin.Model(2)))
    }

    /** 微信(每步确认名单):询问时每一步写操作都要用户同意;这一刻没有真的用户在场,判定停在「要问」。 */
    @Test
    fun weChatAsksForEveryWriteStep() = runBlocking<Unit> {
        assumeTrue("wechat is not installed", installed(WECHAT))
        openUntil(WECHAT)
        val exec = observe()
        val d = decisions.judge("phone_tap", tapArgs(exec.snapshotId, exec.firstRef()), Origin.Model(1))
        assertTrue("expected Required(everyWrite), got $d", d is Decision.Required && d.everyWrite)
    }

    /**
     * 敏感页保护与号码打码在真机上的落点:密码框、验证码框标成秘密框,手机号框不误标;
     * 银行卡号、身份证号在元素名(入模的文字)里只剩末 4 位,模型看到的 observe 结果同样没有明文;
     * 往密码框里打字交还用户——按页认:这一页上有秘密输入框,往哪个框里打字都交还。
     */
    @Test
    fun secretFieldsAreMarkedAndNumbersAreMasked() = runBlocking<Unit> {
        DeviceSupport.startTestActivity(agent.display.displayId, SensitivePageTestActivity::class.java)
        val s = snapUntil { it.packageName == TEST_PACKAGE && it.elements.any { e -> e.name == "密码" } }
        val pwd = s.field("密码")
        assertTrue("password field must be marked secret", pwd.secret)
        assertTrue("password field must be editable", pwd.editable)
        assertTrue("code field must be marked secret", s.field("验证码").secret)
        val phone = s.field("手机号")
        assertFalse("phone field must not be marked secret", phone.secret)

        // 元素名就是入模的文字:卡号、身份证号只剩末 4 位(打码在采集时做,不在出口做)
        val names = s.elements.map { it.name }
        assertTrue("card number not masked: $names", names.any { it == MASKED_CARD })
        assertTrue("id number not masked: $names", names.any { it == MASKED_ID })
        assertTrue("raw numbers reached the model: $names", names.none { CARD in it || ID in it })

        // 模型看到的 observe 结果同样没有明文
        val exec = observe()
        val seen = exec.elementNames()
        assertTrue("raw numbers in observe: $seen", seen.none { CARD in it || ID in it })
        assertTrue("masked numbers missing in observe: $seen", seen.any { it == MASKED_CARD } && seen.any { it == MASKED_ID })

        // 往密码框里打字:交还用户,不代填
        val pwdRef = exec.refOf("密码")
        assertNotNull("password field not in observe elements", pwdRef)
        assertEquals(Decision.SecretPage, decisions.judge("phone_type_text", typeArgs(exec.snapshotId, pwdRef!!, "123456"), Origin.Model(1)))
        // 手机号框在同一页上,同样交还用户(按页认,不按框认)
        val phoneRef = exec.refOf("手机号")
        assertNotNull("phone field not in observe elements", phoneRef)
        assertEquals(Decision.SecretPage, decisions.judge("phone_type_text", typeArgs(exec.snapshotId, phoneRef!!, "13800138000"), Origin.Model(2)))
    }

    private companion object {
        const val ALIPAY = "com.eg.android.AlipayGphone"
        const val WECHAT = "com.tencent.mm"
        const val CARD = "6222020200112233"
        const val ID = "110101199003077758"
        /** 打码后留下的末 4 位,前面全是 •(16 位卡号留 4 位、18 位身份证留 4 位)。 */
        val MASKED_CARD = "•".repeat(12) + "2233"
        val MASKED_ID = "•".repeat(14) + "7758"
    }
}
