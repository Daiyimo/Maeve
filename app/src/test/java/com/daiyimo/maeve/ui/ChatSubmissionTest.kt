package com.daiyimo.maeve.ui

import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.core.agent.TaskState
import com.daiyimo.maeve.core.shell.BuiltinShellStatus
import com.daiyimo.maeve.settings.AccessibilityState
import com.daiyimo.maeve.settings.Prerequisites
import com.daiyimo.maeve.settings.SystemStatus
import com.daiyimo.maeve.takeover.Takeover
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSubmissionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ok = Prerequisites.check(
        SystemStatus(34, BuiltinShellStatus.Running, AccessibilityState.On, notificationsAllowed = true),
        hasMainModel = true,
    )
    private val actions = object : TaskActions {
        override fun start(goal: String, conversationId: String) = error("submission must register through the service")
        override fun cancel() = Unit
        override fun resume() = Unit
        override fun takeOver() = Unit
        override fun handBack() = Unit
        override fun finishedMyself() = Unit
        override fun skip() = Unit
        override fun answer(text: String) = Unit
        override fun approve(key: Int) = Unit
        override fun decline(key: Int) = Unit
    }

    private fun TestScope.vm(serviceBusy: Boolean = false) = ChatViewModel(
        backgroundScope, MutableStateFlow(TaskState.Idle), MutableStateFlow(Takeover.Stage.None),
        actions, ConversationStore(tmp.newFolder()), serviceBusy = MutableStateFlow(serviceBusy),
        io = StandardTestDispatcher(testScheduler),
    )

    @Test fun flutterSubmissionWhileServiceBusyWaitsForDurableRegistration() = runTest {
        val vm = vm(serviceBusy = true)
        val responses = mutableListOf<String>()
        val accepted = vm.submit("下一件事", null, ok) { goal, thread ->
            assertEquals(vm.ui.value.conversationId, thread)
            responses += goal
            true
        }
        assertTrue(accepted)
        assertEquals(listOf("下一件事"), responses)
        assertFalse(vm.newConversation())
    }

    @Test fun rejectedRegistrationRestoresIdleConversation() = runTest {
        val vm = vm()
        assertFalse(vm.submit("下一件事", null, ok) { _, _ -> false })
        assertEquals(ComposerMode.Idle, vm.ui.value.composer)
        assertTrue(vm.ui.value.exchanges.isEmpty())
        assertNull(vm.ui.value.conversationId)
    }
}
