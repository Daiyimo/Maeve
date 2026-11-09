package com.daiyimo.maeve.core.device

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** 把一次设备访问检查写成工具结果的 JSON;字段名是协议的一部分,不要顺手改。 */
fun DeviceAccessResult.toJson() = buildJsonObject {
    put("status", status)
    put("enabled", enabled)
    put("credential_configured", credentialConfigured)
    put("attempt_blocked", attemptBlocked)
    storedKind?.let { put("stored_kind", it.name.lowercase()) }
    detectedKind?.let { put("detected_kind", it.name.lowercase()) }
    if (status == "credential_kind_mismatch") {
        put("message", "Re-enter the credential on the phone using the current lock-screen method (PIN or password).")
    }
    state?.let { snapshot ->
        putJsonObject("device") {
            put("interactive", snapshot.interactive)
            put("keyguard_showing", snapshot.keyguardShowing)
            put("device_locked", snapshot.deviceLocked)
            put("secure", snapshot.secure)
            put("helper_available", snapshot.helperAvailable)
            put("user_unlocked", snapshot.userUnlocked)
            put("ready", snapshot.ready)
        }
    }
}
