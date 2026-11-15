package com.daiyimo.maeve.core.net

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ModelNetworkPolicyTest {
    private fun address(ip: String) = InetAddress.getByName(ip)
    @Test fun publicHttpAndLegacyHttpRequireExplicitReview() {
        assertEquals("model_https_required", (runCatching { ModelNetworkPolicy("http://example.test") }.exceptionOrNull() as ModelNetworkDenied).code)
        assertTrue(runCatching { ModelNetworkPolicy.validateAddresses(listOf(address("93.184.216.34")), true) }.exceptionOrNull() is ModelNetworkDenied)
    }
    @Test fun forbiddenDestinationsAreNeverLanExceptions() {
        for (ip in listOf("0.0.0.0", "169.254.169.254", "224.0.0.1", "255.255.255.255", "100.100.100.200", "::", "fe80::1", "ff02::1", "fd00:ec2::254", "2002:a9fe:a9fe::1")) {
            assertTrue(ip, runCatching { ModelNetworkPolicy.validateAddresses(listOf(address(ip)), false) }.exceptionOrNull() is ModelNetworkDenied)
        }
        for (ip in listOf("127.0.0.1", "192.168.1.1", "10.1.1.1", "172.16.1.1", "::1", "fd12::1")) ModelNetworkPolicy.validateAddresses(listOf(address(ip)), true)
    }
    @Test fun mixedDnsAndRebindingCannotLeakARequest() {
        MockWebServer().use { server ->
            var count = 0
            val dns = object : Dns { override fun lookup(hostname: String): List<InetAddress> =
                if (++count == 1) listOf(address("127.0.0.1")) else listOf(address("127.0.0.1"), address("93.184.216.34")) }
            val url = server.url("/v1/models").newBuilder().host("test.local").build()
            val client = ModelNetworkPolicy(url.toString(), true).protect(OkHttpClient.Builder().dns(dns).build())
            assertTrue(runCatching { client.newCall(Request.Builder().url(url).header("Authorization", "Bearer private").build()).execute().close() }.exceptionOrNull() is ModelNetworkDenied)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun explicitlyApprovedDirectLanWorksButRedirectsAreNotFollowed() {
        MockWebServer().use { server ->
            MockWebServer().use { destination ->
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/other")))
                val url = server.url("/v1/models")
                val client = ModelNetworkPolicy(url.toString(), true).protect(OkHttpClient())
                client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals(302, it.code) }
                assertEquals(1, server.requestCount)
                assertEquals(0, destination.requestCount)
            }
        }
    }
    @Test fun httpDoesNotBypassAnExplicitUserProxy() {
        MockWebServer().use { server ->
            val url = server.url("/v1")
            val client = ModelNetworkPolicy(url.toString(), true).protect(OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", server.port))).build())
            assertEquals("model_http_proxy_unverified", (runCatching { client.newCall(Request.Builder().url(url).build()).execute().close() }.exceptionOrNull() as ModelNetworkDenied).code)
            assertEquals(0, server.requestCount)
        }
    }
}
