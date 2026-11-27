package com.daiyimo.maeve.cap.phone.shell

import android.content.AttributionSource
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelperProviderCallDeviceTest {
    class PreSProvider {
        var callingPkg: String? = null
        var attributionTag: String? = null
        var authority: String? = null
        var method: String? = null
        var arg: String? = null
        var extras: Bundle? = null

        @Suppress("UNUSED_PARAMETER")
        fun call(callingPkg: String?, attributionTag: String?, authority: String, method: String,
                 arg: String?, extras: Bundle?): Bundle {
            this.callingPkg = callingPkg
            this.attributionTag = attributionTag
            this.authority = authority
            this.method = method
            this.arg = arg
            this.extras = extras
            return Bundle().apply { putBoolean("ok", true) }
        }
    }

    class PostSProvider {
        var source: AttributionSource? = null
        var authority: String? = null
        var method: String? = null
        var arg: String? = null
        var extras: Bundle? = null

        @Suppress("UNUSED_PARAMETER")
        fun call(source: AttributionSource, authority: String, method: String, arg: String?, extras: Bundle?): Bundle {
            this.source = source
            this.authority = authority
            this.method = method
            this.arg = arg
            this.extras = extras
            return Bundle().apply { putBoolean("ok", true) }
        }
    }

    class Holder(@JvmField val provider: Any)

    private val extras = Bundle().apply { putString("key", "value") }

    @Test fun providerCallUsesTheSignatureForTheRunningApiLevel() {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val provider = PostSProvider()
            val result = invokeHelperProvider(Holder(provider), "test.authority", "attach", extras)
            assertEquals(Process.myUid(), provider.source!!.uid)
            assertEquals(ShellContext.SHELL_PACKAGE, provider.source!!.packageName)
            assertEquals("test.authority", provider.authority)
            assertEquals("attach", provider.method)
            assertNull(provider.arg)
            assertSame(extras, provider.extras)
            result
        } else {
            val provider = PreSProvider()
            val result = invokeHelperProvider(Holder(provider), "test.authority", "attach", extras)
            assertEquals(ShellContext.SHELL_PACKAGE, provider.callingPkg)
            assertNull(provider.attributionTag)
            assertEquals("test.authority", provider.authority)
            assertEquals("attach", provider.method)
            assertNull(provider.arg)
            assertSame(extras, provider.extras)
            result
        }
        assertTrue(result!!.getBoolean("ok"))
    }
}
