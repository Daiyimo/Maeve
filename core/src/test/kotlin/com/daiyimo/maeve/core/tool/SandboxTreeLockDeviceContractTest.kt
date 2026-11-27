package com.daiyimo.maeve.core.tool

import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.cap.sandbox.CommandResult
import com.daiyimo.maeve.core.cap.sandbox.FakeSandbox
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.workspace.PlatformAssumes

class SandboxTreeLockDeviceContractTest {
    private val sb = FakeSandbox()
    private fun dispatcher() = toolDispatcher(FakeScreen(), sandbox = sb, workspace = { "conv1" })
    private val ws get() = sb.workspaceDir("conv1")
    private fun call(name: String, args: JsonObject) = Part.ToolCall(name, name, args.toString())

    /** 工具往工作区写(write_file、存完整输出)要拿和沙箱同一把树锁:重置/安装(exclusive)进行中不能交错。 */
    @Test fun workspaceWritesWaitForTheSandboxTreeLock() = kotlinx.coroutines.runBlocking {
        PlatformAssumes.assumeLinux()
        sb.next = { CommandResult(0, false, false, 50, "A".repeat(30_000), 0, false) }
        val calls = listOf(
            call("write_file", buildJsonObject { put("path", "locked.txt"); put("content", "x") }),
            call("run_command", buildJsonObject { put("command", "cat big") }),
        )
        for (c in calls) {
            val held = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            val holder = launch(kotlinx.coroutines.Dispatchers.Default) {
                sb.tree.exclusive { held.complete(Unit); release.await() }
            }
            held.await()
            val tool = async(kotlinx.coroutines.Dispatchers.Default) { dispatcher().execute(c) }
            kotlinx.coroutines.delay(300)
            assertFalse("${c.name} must wait for the tree lock", tool.isCompleted)
            release.complete(Unit)
            kotlinx.coroutines.withTimeout(5_000) { tool.await(); holder.join() }
        }
        assertEquals("x", File(ws, "locked.txt").readText())
    }
}
