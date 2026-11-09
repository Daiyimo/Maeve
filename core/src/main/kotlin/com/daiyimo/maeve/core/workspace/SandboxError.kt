package com.daiyimo.maeve.core.workspace

/**
 * 沙箱实现向工具层报告的可识别失败。工具层把 [code] 原样放进结果的 error 字段
 * (例如 `sandbox_network_isolation_unavailable`);[recoverable] 表示换个做法还能继续。
 */
class SandboxError(val code: String, message: String, val recoverable: Boolean = false) : Exception(message)
