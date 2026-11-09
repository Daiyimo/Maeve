package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.access.AppAccessDenied
import kotlinx.coroutines.CancellationException

/**
 * 读屏时的尽力而为兜底:权限拒绝不是「没有内容」,不能降级,原样抛回调用方。
 * 只认权限拒绝这一种,别的异常不归这里管。
 */
internal fun Exception.rethrowAppAccess() {
    if (this is AppAccessDenied) throw this
}

/**
 * 尽力而为地采一屏([body]):普通失败降级成 null;
 * 取消和权限拒绝必须回到调用方,由它们自己处理。
 * 协程取消也一样:吞掉取消会让协程停不下来。
 */
internal suspend inline fun <T> screenAttempt(body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    if (e is CancellationException) throw e
    e.rethrowAppAccess()
    null
}
