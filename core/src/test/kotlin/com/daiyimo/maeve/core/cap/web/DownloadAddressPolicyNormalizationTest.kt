package com.daiyimo.maeve.core.cap.web

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress

class DownloadAddressPolicyNormalizationTest {

    @Test fun localAndPrivateAddressesAreRefused() {
        for (u in listOf(
            "http://localhost/x", "http://LOCALHOST./x", "http://printer.local/x", "http://a.localhost/x", "http://nas.lan/",
            "http://127.0.0.1/x", "http://127.9.9.9/", "http://10.1.2.3/", "http://172.16.0.1/", "http://172.31.255.255/",
            "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data", "http://100.64.0.1/", "http://0.0.0.0/",
            "http://[::1]/", "http://[fe80::1]/", "http://[fec0::1]/", "http://[fd00::1]/", "http://[::ffff:192.168.0.1]/", "http://224.0.0.1/",
            "http://127.1/x", "http://127.0.1/x", "http://2130706433/x", "http://0177.0.0.1/x", "http://0x7f000001/x",
            "http://0x7f.0.0.1/x", "http://127.0.0.1./x", "http://999.1.1.1/x", "http://08.0.0.1/x",
            "http://[::ffff:127.1]/", "http://[::ffff:0x7f.0.0.1]/", "http://[64:ff9b::127.1]/",
            "http://[64:ff9b:1:fffe::a00:1]/", "http://[64:ff9b:1::]/",
        )) assertNotNull(u, DownloadAddressPolicy.refusal(u))
    }

    @Test fun publicAddressesPass() {
        for (u in listOf("https://example.com/a.pdf", "http://8.8.8.8/x", "https://[2606:4700::1111]/", "http://172.32.0.1/", "http://11.0.0.1/",
            "http://134744072/x", "http://010.010.010.010/x", "http://0x08080808/x", "http://[::ffff:8.8.8.8]/")) {
            assertNull(u, DownloadAddressPolicy.refusal(u))
        }
    }

    @Test fun namesThatResolveToThePrivateNetworkAreRefusedAtLookup() {
        val fake = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = when (hostname) {
                "rebind.example" -> listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("192.168.0.10"))
                "mapped.example" -> listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("::ffff:127.0.0.1"))
                else -> listOf(InetAddress.getByName("93.184.216.34"))
            }
        }
        val dns = DownloadAddressPolicy.guardedDns(fake)
        assertEquals(1, dns.lookup("example.com").size)
        try { dns.lookup("rebind.example"); fail("private address passed") } catch (e: DownloadAddressPolicy.BlockedAddress) { }
        try { dns.lookup("mapped.example"); fail("mapped private address passed") } catch (e: DownloadAddressPolicy.BlockedAddress) { }
        try { dns.lookup("localhost"); fail("localhost passed") } catch (e: DownloadAddressPolicy.BlockedAddress) { }
    }

    @Test fun literalFormsNormalizeWithoutDns() {
        for ((raw, canonical) in listOf(
            "127.1" to "127.0.0.1", "127.0.1" to "127.0.0.1", "2130706433" to "127.0.0.1",
            "0177.0.0.1" to "127.0.0.1", "0x7f000001" to "127.0.0.1", "0x7f.1" to "127.0.0.1",
            "[::ffff:127.1]" to "::ffff:127.0.0.1",
        )) assertEquals(raw, InetAddress.getByName(canonical), DownloadAddressPolicy.parseAddress(raw))
        assertEquals(null, DownloadAddressPolicy.parseAddress("example.com"))
    }

    @Test fun allowedAddressesPinsLiteralsWithoutDnsAndRejectsMixedAnswers() {
        var lookups = 0
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                lookups++
                return if (hostname == "mixed.example") listOf(
                    InetAddress.getByName("93.184.216.34"), InetAddress.getByName("192.168.0.10"),
                ) else listOf(InetAddress.getByName("93.184.216.34"))
            }
        }
        assertEquals(listOf(InetAddress.getByName("8.8.8.8")), DownloadAddressPolicy.allowedAddresses("8.8.8.8", dns))
        assertEquals(0, lookups)
        try {
            DownloadAddressPolicy.allowedAddresses("127.1", dns)
            fail("short loopback passed")
        } catch (_: DownloadAddressPolicy.BlockedAddress) { }
        assertEquals(0, lookups)
        try {
            DownloadAddressPolicy.allowedAddresses("mixed.example", dns)
            fail("mixed public/private answer passed")
        } catch (_: DownloadAddressPolicy.BlockedAddress) { }
        assertEquals(1, lookups)
    }

    @Test fun emptyDnsAnswerIsRefused() {
        val dns = DownloadAddressPolicy.guardedDns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = emptyList()
        })
        try { dns.lookup("empty.example"); fail("empty DNS answer passed") } catch (_: DownloadAddressPolicy.BlockedAddress) { }
    }
}
