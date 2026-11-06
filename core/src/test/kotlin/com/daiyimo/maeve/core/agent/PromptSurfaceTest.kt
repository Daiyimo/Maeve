package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.cap.root.RootPort
import com.daiyimo.maeve.core.cap.root.RootRun
import com.daiyimo.maeve.core.knowledge.GuideCatalog
import com.daiyimo.maeve.core.memory.MemoryItem
import com.daiyimo.maeve.core.memory.MemoryPersistence
import com.daiyimo.maeve.core.memory.MemoryStore
import com.daiyimo.maeve.core.memory.UserUtterances
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.StopReason
import com.daiyimo.maeve.core.cap.sandbox.FakeSandbox
import com.daiyimo.maeve.core.cap.sandbox.SandboxStatus
import com.daiyimo.maeve.core.testing.PromptSurface
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import com.daiyimo.maeve.core.vision.VisionService
import com.daiyimo.maeve.core.vision.says
import com.daiyimo.maeve.core.cap.web.FakeWebPort
import kotlinx.coroutines.runBlocking
import org.junit.Test
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.knowledge.KnowledgeStore

/**
 * 模型能看到的全部提示词表面:每种能力组合的完整系统提示词、每个工具的定义(名字、说明、参数 schema)、
 * 内部角色的提示词。金样在 core/src/test/resources/prompt-surface/;重构前后必须一字不差。
 * 重新生成:./gradlew :core:test --tests '*PromptSurface*' -PupdateSnapshots
 */
class PromptSurfaceTest {
    private class Mem : MemoryPersistence {
        override fun load(): List<MemoryItem> = emptyList()
        override fun save(items: List<MemoryItem>) {}
    }

    private class Combo(val tools: () -> ToolDispatcher, val flags: SpeedFlags = SpeedFlags.OFF, val memory: MemoryStore? = null)

    private val bundled = KnowledgeStore(KnowledgeStore.loadBundled())
    private val LAYERS = linkedMapOf("chat" to emptyList(), "phone" to listOf("phone"), "web" to listOf("web"), "all" to listOf("phone", "web", "sandbox"))
    private val combos: Map<String, Combo> = linkedMapOf(
        "phone" to Combo({ toolDispatcher(FakeScreen()) }),
        "phone-inline-batch" to Combo({ toolDispatcher(FakeScreen(), inlineScreen = true) }, SpeedFlags(inlineScreen = true, batchSteps = true)),
        "phone-web" to Combo({ toolDispatcher(FakeScreen(), web = FakeWebPort()) }),
        "phone-sandbox-offline" to Combo({ toolDispatcher(FakeScreen(), sandbox = FakeSandbox(), workspace = { "w" }) }),
        "phone-web-sandbox-online" to Combo({
            toolDispatcher(FakeScreen(), web = FakeWebPort(), sandbox = FakeSandbox(SandboxStatus.Ready(network = true)), workspace = { "w" })
        }),
        "phone-sandbox-preparing" to Combo({ toolDispatcher(FakeScreen(), sandbox = FakeSandbox(SandboxStatus.NotInstalled), workspace = { "w" }) }),
        // 已 root 的手机上用户打开了 root 命令
        "phone-root" to Combo({ toolDispatcher(FakeScreen(), root = object : RootPort {
            override suspend fun run(command: String, timeoutMs: Long): RootRun = RootRun.Unavailable
        }) }),
        "everything" to MemoryStore(Mem(), clock = { 1L }, ids = { "m" }).let { mem ->
            Combo({
                toolDispatcher(
                    FakeScreen(), visionEnabled = true, skills = GuideCatalog.loadBundled(), vision = VisionService(says(), JvmTestImageOps),
                    web = FakeWebPort(), inlineScreen = true, deepLinks = true,
                    memory = mem, utterances = UserUtterances(), sandbox = FakeSandbox(SandboxStatus.Ready(network = false)), workspace = { "w" },
                    personalSkills = com.daiyimo.maeve.core.skills.InMemoryPersonalSkills(),
                    // 子任务和之前几轮的记录:之前有记过运行的一轮时才有 history_read
                    runSpec = com.daiyimo.maeve.core.run.RunSpec(com.daiyimo.maeve.core.run.RunId("t"), "t", "thread",
                        priorTurns = listOf(Turn("之前", "好", "succeeded", runId = "r1"))),
                    childHost = com.daiyimo.maeve.core.child.ChildRunHost { spec, _ ->
                        com.daiyimo.maeve.core.child.ChildResult(spec.id, com.daiyimo.maeve.core.child.ChildPhase.Completed, "") },
                    runRecords = NoRecords,
                )
            }, SpeedFlags(inlineScreen = true, batchSteps = true, deepLinks = true), mem)
        },
    )

    /** [open] 时上一轮用过三组:开局都展开(本任务提供了的);否则都收着,只有入口。 */
    private fun offered(c: Combo, open: Boolean = true): FakeRequestCapture = runBlocking {
        val m = FakeRequestCapture()
        AgentLoop(m, c.tools(), SystemPrompt.identity(), flags = c.flags, memory = c.memory)
            .run("t", if (open) listOf(Turn("之前", "好", "succeeded", groups = listOf("phone", "web", "sandbox"))) else emptyList())
        m
    }

    /** 每种组合各组都展开时的系统提示词(收着时的样子见 layeredSurfaces)。 */
    @Test fun systemPromptPerCombination() {
        for ((name, c) in combos) {
            val m = offered(c)
            PromptSurface.check("system/$name", PromptSurface.system(m.system!!, m.tools))
        }
    }

    @Test fun toolDefinitions() {
        // 同名工具按本任务有几块屏可能有几种定义(多一个 surface 参数),入口工具收着时另有短说明:每种都列出
        val all = combos.values.flatMap { offered(it).tools + offered(it, open = false).tools }
            .distinctBy { it.name + it.description + it.parametersJsonSchema }.sortedBy { it.name }
        PromptSurface.check("tools", PromptSurface.tools(all))
    }

    /**
     * 分层之后各情形下第一次请求的全貌(系统提示词 + 每个工具的定义全文),组合同 everything、按产品路径不给确认工具:
     * chat 只聊天(都收着)、phone / web 上一轮用过那一组(开局展开)、all 三组都展开。大小另写进 layers/sizes。
     */
    @Test fun layeredSurfaces() {
        val c = combos.getValue("everything")
        val sizes = StringBuilder()
        for ((name, groups) in LAYERS) {
            val m = FakeRequestCapture()
            val prior = if (groups.isEmpty()) emptyList() else listOf(Turn("之前", "好", "succeeded", groups = groups))
            runBlocking { AgentLoop(m, c.tools(), SystemPrompt.identity(), flags = c.flags, memory = c.memory, confirmations = null).run("t", prior) }
            PromptSurface.check("layers/$name", PromptSurface.layer(m.system!!, m.tools))
            val system = m.system!!.toByteArray().size
            val tools = m.tools.sumOf { (it.name + it.description + it.parametersJsonSchema).toByteArray().size }
            val tokens = TokenEstimate.of(listOf(com.daiyimo.maeve.core.model.Message(com.daiyimo.maeve.core.model.Role.User,
                listOf(Part.Text(m.system!! + m.tools.joinToString("") { it.name + it.description + it.parametersJsonSchema })), com.daiyimo.maeve.core.model.Author.Mika)))
            sizes.appendLine("$name: system $system bytes, ${m.tools.size} tools $tools bytes, total ${system + tools} bytes, ~$tokens tokens")
        }
        print(sizes)
        PromptSurface.check("layers/sizes", sizes.toString())
    }

    @Test fun rolePrompts() {
        PromptSurface.check("roles", PromptSurface.roles())
    }

    @Test fun feedbackFromTools() {
        PromptSurface.check("feedback-tools", runBlocking { PromptSurfaceFeedback.tools() })
    }

    @Test fun feedbackFromLoop() {
        PromptSurface.check("feedback-loop", PromptSurfaceFeedback.loop())
    }

    private object NoRecords : com.daiyimo.maeve.core.record.RunRecords {
        override suspend fun summary(runId: String): com.daiyimo.maeve.core.record.RunSummary? = null
        override suspend fun events(runId: String) = emptyList<com.daiyimo.maeve.core.record.RunEvent>()
    }

    /** 只回一个 finish:记下开工那次请求的系统提示词和工具。 */
    private class FakeRequestCapture : com.daiyimo.maeve.core.model.ChatModel {
        override val capabilities = com.daiyimo.maeve.core.model.Capabilities(toolCalls = true, vision = false, contextTokens = 128_000, reasoning = false)
        var system: String? = null
        var tools: List<com.daiyimo.maeve.core.model.ToolSpec> = emptyList()
        override suspend fun complete(system: String, messages: List<com.daiyimo.maeve.core.model.Message>, tools: List<com.daiyimo.maeve.core.model.ToolSpec>): ModelReply {
            if (this.system == null) { this.system = system; this.tools = tools }
            return com.daiyimo.maeve.core.testing.answerReply("ok")
        }
    }
}
