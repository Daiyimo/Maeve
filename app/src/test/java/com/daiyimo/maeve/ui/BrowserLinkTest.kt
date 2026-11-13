package com.daiyimo.maeve.ui

import org.junit.Assert.*
import org.junit.Test

class BrowserLinkTest {
    @Test fun webLinksPreserveTheirPathQueryAndFragment() {
        assertEquals("https://example.com/docs?q=one#section", browserLink("https://example.com/docs?q=one#section"))
        assertEquals("http://localhost/page", browserLink("http://localhost/page"))
    }
    @Test fun nonWebOrCredentialBearingLinksDoNotLeaveTheApp() {
        for (url in listOf(null, "", "javascript:alert(1)", "file:///etc/hosts", "intent://open", "https://user:secret@example.com", "https://", "not a URL")) {
            assertNull(url, browserLink(url))
        }
    }
}
