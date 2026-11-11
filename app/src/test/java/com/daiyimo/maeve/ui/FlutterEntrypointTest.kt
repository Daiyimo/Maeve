package com.daiyimo.maeve.ui

import io.flutter.embedding.android.FlutterActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guard the installed entry components, not just Flutter's internal route tree. */
class FlutterEntrypointTest {
    private val document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    private fun Element.android(name: String) = getAttributeNS("http://schemas.android.com/apk/res/android", name)
    private fun components(tag: String): List<Element> = document.getElementsByTagName(tag).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test fun historicalMainComponentIsAFlutterHost() {
        assertTrue(FlutterActivity::class.java.isAssignableFrom(MainActivity::class.java))
        val main = components("activity").single { it.android("name") == ".ui.MainActivity" }
        assertEquals("singleTask", main.android("launchMode"))
    }

    @Test fun everyLauncherIconUsesTheSameFlutterHost() {
        val launchers = components("activity-alias").filter { alias ->
            alias.getElementsByTagName("category").let { nodes ->
                (0 until nodes.length).any { (nodes.item(it) as Element).android("name") == "android.intent.category.LAUNCHER" }
            }
        }
        assertEquals(4, launchers.size)
        launchers.forEach { assertEquals(it.android("name"), ".ui.MainActivity", it.android("targetActivity")) }
    }

    @Test fun oldFlutterComponentIsAnAliasRatherThanASecondActivity() {
        assertFalse(components("activity").any { it.android("name") == ".ui.FlutterShellActivity" })
        val old = components("activity-alias").single { it.android("name") == ".ui.FlutterShellActivity" }
        assertEquals(".ui.MainActivity", old.android("targetActivity"))
    }

    @Test fun redundantComposeActivityIsRemoved() {
        assertFalse(components("activity").any { it.android("name").contains("LegacyCompose") })
    }
}
