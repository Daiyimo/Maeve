package com.daiyimo.maeve.core.net

import okhttp3.Dns
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * GitHub 加速的域名解析:只动 GitHub 的域名、DoH 问不到就退回系统、自定义 IP 只收字面 IP。
 */
class GitHubDnsTest {
    private val server = MockWebServer()
    private val systemIp = InetAddress.getByName("127.0.0.1")
    private val pinned = InetAddress.getByName("20.205.243.166")

    /** 兜底解析:记下被问过谁,返回一个固定地址。 */
    private val systemAsked = mutableListOf<String>()
    private val fakeSystem = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            systemAsked += hostname
            return listOf(systemIp)
        }
    }

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    /** 模式与 IP 都可变;DoH 指向本地假服务器,兜底解析用假的。 */
    private fun dns(mode: GitHubDnsMode, ip: String = "") =
        GitHubDns({ mode }, { ip }, dohClient = okhttp3.OkHttpClient(), now = { 0 },
            aliDohUrl = { server.url("/ali?name=$it&type=A").toString() },
            tencentDohUrl = { server.url("/tencent?name=$it&type=A").toString() },
            system = fakeSystem)

    @Test fun onlyGitHubHostsAreTouched() {
        assertTrue(GitHubHosts.matches("github.com"))
        assertTrue(GitHubHosts.matches("api.github.com"))
        assertTrue(GitHubHosts.matches("objects.githubusercontent.com"))
        assertTrue(GitHubHosts.matches("raw.githubusercontent.com"))
        assertTrue(GitHubHosts.matches("codeload.github.com"))
        assertTrue(!GitHubHosts.matches("api.deepseek.com"))
        assertTrue(!GitHubHosts.matches("example.com"))
    }

    @Test fun systemModeLeavesEveryHostToThePlatform() {
        val dns = dns(GitHubDnsMode.System)
        assertEquals(listOf(systemIp), dns.lookup("github.com"))
        assertEquals(listOf(systemIp), dns.lookup("api.deepseek.com"))
        assertEquals(0, server.requestCount)
    }

    @Test fun customIpPinsEveryGitHubHost() {
        val dns = dns(GitHubDnsMode.CustomIp, ip = "20.205.243.166")
        for (host in listOf("github.com", "api.github.com", "objects.githubusercontent.com")) {
            assertEquals(listOf(pinned), dns.lookup(host))
        }
        // 别的域名不受影响
        assertEquals(listOf(systemIp), dns.lookup("api.deepseek.com"))
        assertEquals(0, server.requestCount)
    }

    /** 填了域名(不是 IP)一律当没填:免得又绕回 DNS。 */
    @Test fun customIpOnlyTakesLiteralAddresses() {
        assertNull(GitHubDns.literalIp("github.com"))
        assertNull(GitHubDns.literalIp(""))
        assertNull(GitHubDns.literalIp("20.205.243.999"))
        assertNotNull(GitHubDns.literalIp("20.205.243.166"))
    }

    @Test fun dohAnswersAreUsedAndRemembered() {
        server.enqueue(MockResponse().setBody("""{"Status":0,"Answer":[{"name":"api.github.com.","type":1,"TTL":60,"data":"20.205.243.166"}]}"""))
        val dns = dns(GitHubDnsMode.AliDoh)
        assertEquals(listOf(pinned), dns.lookup("api.github.com"))
        assertEquals("/ali?name=api.github.com&type=A", server.takeRequest().path)
        // 记住的解析第二次不再问,也不问系统
        assertEquals(listOf(pinned), dns.lookup("api.github.com"))
        assertEquals(1, server.requestCount)
        assertTrue(systemAsked.isEmpty())
    }

    /** DoH 问不到(非 0 状态、没有 A 记录、不是 JSON)时不拦路:退回系统解析。 */
    @Test fun dohFailuresFallBackToTheSystem() {
        val bodies = listOf(
            """{"Status":2,"Answer":[]}""",
            """{"Status":0,"Answer":[{"name":"x","type":28,"data":"::1"}]}""",
            """{"Status":0}""",
            "not json",
        )
        for (body in bodies) {
            systemAsked.clear()
            val asked = server.requestCount
            server.enqueue(MockResponse().setBody(body))
            val dns = dns(GitHubDnsMode.TencentDoh)
            assertEquals("fell back to the system for: $body", listOf(systemIp), dns.lookup("github.com"))
            assertEquals("asked DoH once for: $body", asked + 1, server.requestCount)
            assertEquals(listOf("github.com"), systemAsked)
        }
    }

    /** DoH 端点整个连不上:同样退回系统,不把网络错当成解析结果。 */
    @Test fun unreachableDohFallsBackToTheSystem() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        val dns = dns(GitHubDnsMode.AliDoh)
        assertEquals(listOf(systemIp), dns.lookup("github.com"))
        assertEquals(listOf("github.com"), systemAsked)
    }
}
