package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.settings.SettingsList
import com.daiyimo.maeve.core.settings.SettingsSet
import com.daiyimo.maeve.core.tool.AgentTool

/**
 * 通用工具,不属于哪块屏,任何任务都可能用到;顺序就是发给模型的顺序。
 * 先节奏与回看(等待、回看历史画面、看用户发来的那一屏),再停下问用户或交出去(问、确认、交给用户、报障碍、没办成结束),
 * 然后查读(知识与技能)与写入(按用户要求写下技能、记忆、改画像),最后是这个对话的历史、定时任务、派生子任务,以及设置入口。
 */
internal object GeneralGroup {
    val tools: List<AgentTool> = listOf(
        Wait, ViewPastScreens, ViewUserScreen, AskUser, RequestConfirmation, HandToUser, HandleObstacle, EndNotDone,
        KnowledgeSearch, KnowledgeRead, SkillSearch, SkillRead, SkillSave, MemorySearch, MemorySave, ProfileEdit, HistoryRead, ScheduleTask, SpawnAgent,
        SettingsList, SettingsSet,
    )
}
