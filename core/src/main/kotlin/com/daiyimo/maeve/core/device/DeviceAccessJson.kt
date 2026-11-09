package com.daiyimo.maeve.core.device

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 设备访问结果的线上格式。字段名、大小写和 [KIND_MISMATCH_HINT] 的文案都是
 * MCP/HTTP 调用方依赖的协议，改状态机时不要顺手改这里。
 */
fun DeviceAccessResult.toJson(): JsonObject = buildJsonObject {
    put("status", status)
    put("enabled", enabled)
    put("credential_configured", credentialConfigured)
    put("attempt_blocked", attemptBlocked)
    storedKind?.let { put("stored_kind", it.wireName()) }
    detectedKind?.let { put("detected_kind", it.wireName()) }
    if (status == KIND_MISMATCH) put("message", KIND_MISMATCH_HINT)
    state?.let { put("device", it.wireJson()) }
}

/** 只暴露系统状态，不暴露任何凭据内容。 */
private fun DeviceState.wireJson(): JsonObject = buildJsonObject {
    put("interactive", interactive)
    put("keyguard_showing", keyguardShowing)
    put("device_locked", deviceLocked)
    put("secure", secure)
    put("helper_available", helperAvailable)
    put("user_unlocked", userUnlocked)
    put("ready", ready)
}

private fun CredentialKind.wireName() = name.lowercase()

private const val KIND_MISMATCH = "credential_kind_mismatch"

private const val KIND_MISMATCH_HINT =
    "Re-enter the credential on the phone using the current lock-screen method (PIN or password)."
