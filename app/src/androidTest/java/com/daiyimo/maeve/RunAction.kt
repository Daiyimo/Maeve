package com.daiyimo.maeve

import com.daiyimo.maeve.core.action.ActionExecutor
import com.daiyimo.maeve.core.action.ActionRecorder
import com.daiyimo.maeve.core.action.Origin
import com.daiyimo.maeve.core.action.execution
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.tool.ToolExecution

/** 设备测试里直接做一个工具调用:和产品一样经执行入口(不配确认关卡,不等锁屏)。 */
suspend fun ToolDispatcher.runAction(call: Part.ToolCall): ToolExecution =
    ActionExecutor(this, OperationGuards(), null, gate = {}, recorder = ActionRecorder(RunSink.NONE)).run(call, Origin.Model(0)).execution()
