package com.daiyimo.maeve.settings

import com.daiyimo.maeve.core.model.Capabilities
import com.daiyimo.maeve.core.model.ModelRole
import java.io.File
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProviderTransactionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun store() = ProviderStore(tmp.root, AesGcmKeyCipher { key })
    private val caps = Capabilities(true, false, 4096, false)

    @Test fun failedCompositeSavePublishesNothingAndKeepsCredentials() {
        val store = store()
        val original = store.connect("first", ProviderConfig.Kind.OpenAiCompat, "https://one.test", "old-key", emptyList())
        val before = File(tmp.root, "providers.json").readText()
        assertTrue(runCatching { store.transaction {
            connect("second", ProviderConfig.Kind.OpenAiCompat, "https://two.test", "new-key", emptyList())
            assertEquals(1, providers.value.size) // 尚未发布中间状态。
            updateProviderKey(original.id, "replacement")
            error("second stage failed")
        } }.isFailure)
        assertEquals(before, File(tmp.root, "providers.json").readText())
        assertEquals("old-key", store.providerKey(original.id))
        assertEquals(listOf(original), store.providers.value)
        assertEquals(listOf(original), store().providers.value)
    }

    @Test fun commitFailureRollsBackAllMemoryAndDiskState() {
        val store = store()
        val original = store.connect("first", ProviderConfig.Kind.OpenAiCompat, "https://one.test", "old-key", emptyList())
        File(tmp.root, "providers-transaction.json.tmp").mkdir()
        assertTrue(runCatching { store.transaction {
            updateProviderKey(original.id, "new-key")
            use(ModelRole.Main, original.id, "model", caps)
        } }.isFailure)
        assertNull(store.roles.value.main)
        assertEquals("old-key", store.providerKey(original.id))
        assertEquals("old-key", store().providerKey(original.id))
    }

    @Test fun restartReplaysOneCommittedSnapshotAfterMirrorFailure() {
        val store = store()
        // 模拟进程在主文件替换前退出：已提交 journal，主文件仍缺失。
        val blocked = File(tmp.root, "providers.json.tmp").apply { mkdir() }
        val provider = store.transaction {
            val created = connect("complete", ProviderConfig.Kind.OpenAiCompat, "https://one.test", "key", emptyList())
            use(ModelRole.Main, created.id, "model", caps)
            created
        }
        assertTrue(File(tmp.root, "providers-transaction.json").isFile)
        assertFalse(File(tmp.root, "providers.json").exists())
        blocked.delete()
        val restored = store()
        assertEquals(ProviderStore.LoadState.Valid, restored.loadState.value)
        assertEquals(provider.id, restored.configFor(ModelRole.Main)?.providerId)
        assertEquals("key", restored.providerKey(provider.id))
        assertFalse(File(tmp.root, "providers-transaction.json").exists())
        assertTrue(File(tmp.root, "providers-snapshot.json").isFile)
    }

    @Test fun recoveryRestoresCompleteSnapshotAndPreservesCorruptOriginal() {
        val original = store().connect("saved", ProviderConfig.Kind.OpenAiCompat, "https://one.test", "key", emptyList())
        File(tmp.root, "providers.json").writeText("{truncated")
        val broken = store()
        assertEquals(ProviderStore.LoadState.Corrupt, broken.loadState.value)
        broken.recoverSnapshot()
        assertEquals(ProviderStore.LoadState.Valid, broken.loadState.value)
        assertEquals("key", broken.providerKey(original.id))
        assertEquals("key", store().providerKey(original.id))
        assertTrue(tmp.root.listFiles().orEmpty().any { it.name.startsWith("providers.json.corrupt-") && it.readText() == "{truncated" })
    }

    @Test fun damagedSnapshotCannotOverwriteBrokenFiles() {
        store().connect("saved", ProviderConfig.Kind.OpenAiCompat, "https://one.test", "key", emptyList())
        File(tmp.root, "providers.json").writeText("{truncated")
        File(tmp.root, "providers-snapshot.json").writeText("{invalid")
        val broken = store()
        assertTrue(runCatching { broken.recoverSnapshot() }.isFailure)
        assertEquals("{truncated", File(tmp.root, "providers.json").readText())
        assertEquals(ProviderStore.LoadState.Corrupt, broken.loadState.value)
    }

    @Test fun recoveryCommitFailureKeepsTheCorruptStateAndOriginalFile() {
        store().connect("saved", ProviderConfig.Kind.OpenAiCompat, "https://one.test", "key", emptyList())
        File(tmp.root, "providers.json").writeText("{truncated")
        val broken = store()
        File(tmp.root, "providers-transaction.json.tmp").mkdir()
        assertTrue(runCatching { broken.recoverSnapshot() }.isFailure)
        assertEquals(ProviderStore.LoadState.Corrupt, broken.loadState.value)
        assertEquals("{truncated", File(tmp.root, "providers.json").readText())
    }

    @Test fun caughtNestedFailureStillAbortsTheCompositeTransaction() {
        val store = store()
        assertTrue(runCatching { store.transaction {
            connect("saved", ProviderConfig.Kind.OpenAiCompat, "https://one.test", "key", emptyList())
            runCatching { transaction { error("nested failure") } }
        } }.isFailure)
        assertTrue(store.providers.value.isEmpty())
        assertFalse(File(tmp.root, "providers.json").exists())
    }

    @Test fun deletingModelRetainsSharedKeyAndResetsRoles() {
        val store = store()
        val provider = store.connect("saved", ProviderConfig.Kind.OpenAiCompat, "https://one.test", "key", emptyList(), typed = setOf("one", "two"))
        store.use(ModelRole.Main, provider.id, "one", caps)
        store.removeModel(provider.id, "one")
        val restarted = store()
        assertNull(restarted.roles.value.main)
        assertEquals("key", restarted.providerKey(provider.id))
        assertTrue("one" in restarted.provider(provider.id)!!.hiddenModels)
        assertEquals(setOf("two"), restarted.provider(provider.id)!!.typedModels)
        restarted.connect("saved", ProviderConfig.Kind.OpenAiCompat, "https://one.test", null, emptyList(), typed = setOf("one"))
        assertFalse("one" in restarted.provider(provider.id)!!.hiddenModels)
    }
}
