package com.daiyimo.maeve.chat

import com.daiyimo.maeve.core.agent.Turn
import com.daiyimo.maeve.core.tool.PlanStepStatus
import com.daiyimo.maeve.core.tool.TaskPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationPlanPersistenceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun explicitPlanSurvivesConversationReload() {
        val store = ConversationStore(temp.root)
        val plan = TaskPlan("整理资料", listOf(
            com.daiyimo.maeve.core.tool.PlanStep("打开文件", PlanStepStatus.Done),
            com.daiyimo.maeve.core.tool.PlanStep("读取内容", PlanStepStatus.Current),
            com.daiyimo.maeve.core.tool.PlanStep("汇总答案", PlanStepStatus.Pending),
        ), 2)
        store.append("c", Turn("整理资料", "完成", "succeeded", plan = plan))

        val loaded = ConversationStore(temp.root).turns("c").single()
        assertEquals(plan, loaded.plan)
        assertEquals(listOf("打开文件", "读取内容"), loaded.plan!!.visibleSteps.map { it.text })
    }

    @Test fun oldTurnsWithoutPlansStillLoad() {
        val store = ConversationStore(temp.root)
        store.append("old", Turn("你好", "在", "succeeded"))
        assertNull(ConversationStore(temp.root).turns("old").single().plan)
    }
}
