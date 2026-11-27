package com.daiyimo.maeve.cap.phone.shell

import android.content.AttributionSource
import android.os.Build
import android.os.Bundle
import android.os.Process

/** 在已取得的 provider 上发起一次调用(attach 和保活戳都走它)。 */
internal fun invokeHelperProvider(holder: Any, authority: String, method: String, extras: Bundle?): Bundle? {
    val provider = holder.javaClass.getField("provider").get(holder)!!
    val string = String::class.java
    val bundle = Bundle::class.java
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val caller = AttributionSource.Builder(Process.myUid()).setPackageName(ShellContext.SHELL_PACKAGE).build()
        provider.javaClass.getMethod("call", AttributionSource::class.java, string, string, string, bundle)
            .invoke(provider, caller, authority, method, null, extras) as Bundle?
    } else {
        provider.javaClass.getMethod("call", string, string, string, string, string, bundle)
            .invoke(provider, ShellContext.SHELL_PACKAGE, null, authority, method, null, extras) as Bundle?
    }
}
