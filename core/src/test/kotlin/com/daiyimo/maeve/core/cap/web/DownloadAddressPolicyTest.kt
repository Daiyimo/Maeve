package com.daiyimo.maeve.core.cap.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DownloadAddressPolicyTest {
    @Test fun sameRuleAsNavigationFirst() {
        assertNotNull(DownloadAddressPolicy.refusal("file:///data/data/x"))
        assertNotNull(DownloadAddressPolicy.refusal("intent://x#Intent;package=a.b;end"))
        assertNotNull(DownloadAddressPolicy.refusal("javascript:alert(1)"))
    }

    @Test fun addressClasses() {
        assertTrue(DownloadAddressPolicy.blockedAddress(InetAddress.getByName("198.18.0.1")))
        assertFalse(DownloadAddressPolicy.blockedAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(DownloadAddressPolicy.blockedAddress(InetAddress.getByName("64:ff9b::a00:1"))) // NAT64 里的 10.0.0.1
    }
}
