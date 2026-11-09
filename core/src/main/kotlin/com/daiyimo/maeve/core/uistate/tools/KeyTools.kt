package com.daiyimo.maeve.core.uistate.tools

import com.daiyimo.maeve.core.risk.GateTarget
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.argStr
import com.daiyimo.maeve.core.tool.str
import com.daiyimo.maeve.core.uistate.PressKeys
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.action
import com.daiyimo.maeve.core.uistate.screen
import kotlinx.serialization.json.JsonObject

/** press_key 参数 key 的说明:白名单里的键名,或数字键值。 */
private const val KEY_NOTE = "One of the allowed KEYCODE_* names, or its numeric keycode string."

/** 返回:退回上一级,离开页面本身没有对外影响,不按目标判不可逆。 */
internal class Back(f: ScreenFamily) : ScreenTool(f, "back") {
    override val verb = Verb.Back
    override val label = "返回"
    override val risk = Risk.Write
    override val batchable = true
    override val routable = f.routable
    override val showsScreenAfter = true
    override val reportsEffect = true
    override val logged = true
    // 没有参数:声明只要名字和模板描述
    override val def by lazy { ToolDef(name, describe()) }

    // 没有参数:界面栈顶是哪个页面由端口自己知道
    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = ctx.action(ctx.screen.back())
}

/**
 * 按键:回车、确认键有可能是在提交,要按目标判不可逆;
 * 具体放行哪些键见 [com.daiyimo.maeve.core.uistate.PressKeys]。
 */
internal class PressKey(f: ScreenFamily) : ScreenTool(f, "press_key") {
    override val verb = Verb.PressKey
    override val label = "按键"
    override val risk = Risk.External
    override val gateTarget = GateTarget.Key
    override val batchable = true
    override val routable = f.routable
    override val showsScreenAfter = true
    override val reportsEffect = true
    override val logged = true
    override val def by lazy { ToolDef(name, describe(),
        mapOf("key" to str(KEY_NOTE)),
        listOf("key")) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val keyCode = allowedKey(a.str("key")!!)
        return ctx.action(ctx.screen.pressKey(keyCode))
    }

    /**
     * 参数里的键名换成键值:只放行导航和编辑键(见 [PressKeys]),这块屏另有限制时再查一遍;
     * 不在白名单里按参数错拒掉,说法里点明能用哪些。
     */
    private fun allowedKey(raw: String): Int {
        val keyName = raw.trim()
        val code = PressKeys.resolve(keyName) ?: throw unknownKey(keyName)
        val allowed = family.keys
        if (allowed != null && code !in allowed) throw restrictedKey(keyName)
        return code
    }

    /** 键名不在导航/编辑白名单里:说法里点明这块屏能用哪些。 */
    private fun unknownKey(keyName: String) =
        ArgError("key", "key '$keyName' is not allowed; only navigation/editing keys: ${keyNames(family)}")

    /** 这块屏另有一份键位限制(见 [ScreenFamily.keys]):优先用它的说法。 */
    private fun restrictedKey(keyName: String) =
        ArgError("key", family.keysHint ?: "key '$keyName' is not allowed here")

    override fun logTarget(o: JsonObject, r: ToolExecution, ctx: ToolContext): String? {
        return o.argStr("key")
    }
}
