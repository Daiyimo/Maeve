package com.daiyimo.maeve.core.workspace

/** 工作区路径检查的结论;具体判定由 app 侧的工作区路径规范化实现给出。 */
enum class WorkspacePathCheck {
    Ok,
    NotFound,
    Outside,
}
