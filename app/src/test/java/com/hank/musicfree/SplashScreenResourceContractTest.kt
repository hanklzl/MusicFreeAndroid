package com.hank.musicfree

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class SplashScreenResourceContractTest {

    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val projectRoot: Path = locateProjectRoot()
    private val appMain: Path = projectRoot.resolve("app/src/main")
    private val rnMain: Path = locateRnMain(projectRoot)

    @Test
    fun `androidx splash dependency is declared in version catalog and app module`() {
        assertContains(
            projectRoot.resolve("gradle/libs.versions.toml"),
            """coreSplashscreen = "1.2.0"""",
        )
        assertContains(
            projectRoot.resolve("gradle/libs.versions.toml"),
            """androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }""",
        )
        assertContains(
            projectRoot.resolve("app/build.gradle.kts"),
            "implementation(libs.androidx.core.splashscreen)",
        )
    }

    @Test
    fun `main activity installs AndroidX splash before super onCreate`() {
        val source = Files.readString(
            projectRoot.resolve("app/src/main/java/com/hank/musicfree/MainActivity.kt"),
        )
        val onCreateBody = extractFunctionBody(source, "onCreate")
        val installIndex = onCreateBody.indexOf("installSplashScreen()")
        val superIndex = onCreateBody.indexOf("super.onCreate")

        assertTrue("MainActivity should call installSplashScreen()", installIndex >= 0)
        assertTrue("MainActivity should call super.onCreate", superIndex >= 0)
        assertTrue(
            "installSplashScreen() must run before super.onCreate in onCreate",
            installIndex < superIndex,
        )
    }

    @Test
    fun `manifest points launcher activity at splash theme`() {
        val manifest = parseXml(appMain.resolve("AndroidManifest.xml"))
        val application = manifest.firstElement("application")
        val launcherActivity = manifest.elements("activity").firstOrNull { activity ->
            activity.childElements("intent-filter").any { intentFilter ->
                val hasMainAction = intentFilter.childElements("action").any {
                    it.androidAttribute("name") == "android.intent.action.MAIN"
                }
                val hasLauncherCategory = intentFilter.childElements("category").any {
                    it.androidAttribute("name") == "android.intent.category.LAUNCHER"
                }

                hasMainAction && hasLauncherCategory
            }
        } ?: throw AssertionError("Expected AndroidManifest.xml to declare a MAIN LAUNCHER activity")

        assertAndroidAttribute(application, "icon", "@mipmap/ic_launcher")
        assertAndroidAttribute(application, "roundIcon", "@mipmap/ic_launcher_round")
        assertAndroidAttribute(application, "theme", "@style/Theme.MusicFreeAndroid")
        assertAndroidAttribute(launcherActivity, "theme", "@style/Theme.MusicFreeAndroid.Splash")
    }

    @Test
    fun `manifest disables predictive back at application level`() {
        val manifest = parseXml(appMain.resolve("AndroidManifest.xml"))
        val application = manifest.firstElement("application")

        assertAndroidAttribute(application, "enableOnBackInvokedCallback", "false")
    }

    @Test
    fun `splash themes match the AndroidX and RN visual contract`() {
        val baseTheme = appMain.resolve("res/values/themes.xml")
        val v31Theme = appMain.resolve("res/values-v31/themes.xml")
        val baseThemeDocument = parseXml(baseTheme)
        val v31ThemeDocument = parseXml(v31Theme)

        assertStyleParent(
            baseThemeDocument,
            "Theme.MusicFreeAndroid",
            "android:Theme.Material.Light.NoActionBar",
        )
        assertStyleParent(baseThemeDocument, "Theme.MusicFreeAndroid.Splash", "Theme.SplashScreen")
        assertStyleItem(
            baseThemeDocument,
            "Theme.MusicFreeAndroid.Splash",
            "windowSplashScreenBackground",
            "@color/splashscreen_background",
        )
        assertStyleItem(
            baseThemeDocument,
            "Theme.MusicFreeAndroid.Splash",
            "windowSplashScreenAnimatedIcon",
            "@drawable/splashscreen_image",
        )
        assertStyleItem(
            baseThemeDocument,
            "Theme.MusicFreeAndroid.Splash",
            "postSplashScreenTheme",
            "@style/Theme.MusicFreeAndroid",
        )

        assertStyleParent(v31ThemeDocument, "Theme.MusicFreeAndroid.Splash", "Theme.SplashScreen")
        assertStyleItem(
            v31ThemeDocument,
            "Theme.MusicFreeAndroid.Splash",
            "windowSplashScreenBackground",
            "@color/splashscreen_background",
        )
        assertStyleItem(
            v31ThemeDocument,
            "Theme.MusicFreeAndroid.Splash",
            "windowSplashScreenAnimatedIcon",
            "@drawable/splashscreen_image",
        )
        assertStyleItem(
            v31ThemeDocument,
            "Theme.MusicFreeAndroid.Splash",
            "android:windowSplashScreenBrandingImage",
            "@drawable/spashscreen_branding_image",
        )
        assertStyleItem(
            v31ThemeDocument,
            "Theme.MusicFreeAndroid.Splash",
            "postSplashScreenTheme",
            "@style/Theme.MusicFreeAndroid",
        )

        assertColorValue(appMain.resolve("res/values/colors.xml"), "splashscreen_background", "#27282C")
        assertColorValue(
            appMain.resolve("res/values/ic_launcher_background.xml"),
            "ic_launcher_background",
            "#27282C",
        )
    }

    @Test
    fun `RN splash and launcher resources are copied byte for byte`() {
        val copiedResources = listOf(
            "res/drawable/splashscreen_image.png",
            "res/drawable/spashscreen_branding_image.png",
            "res/drawable/splashscreen.xml",
            "res/values/ic_launcher_background.xml",
            "res/mipmap-anydpi-v26/ic_launcher.xml",
            "res/mipmap-anydpi-v26/ic_launcher_round.xml",
            "res/mipmap-mdpi/ic_launcher.webp",
            "res/mipmap-mdpi/ic_launcher_round.webp",
            "res/mipmap-mdpi/ic_launcher_foreground.webp",
            "res/mipmap-hdpi/ic_launcher.webp",
            "res/mipmap-hdpi/ic_launcher_round.webp",
            "res/mipmap-hdpi/ic_launcher_foreground.webp",
            "res/mipmap-xhdpi/ic_launcher.webp",
            "res/mipmap-xhdpi/ic_launcher_round.webp",
            "res/mipmap-xhdpi/ic_launcher_foreground.webp",
            "res/mipmap-xxhdpi/ic_launcher.webp",
            "res/mipmap-xxhdpi/ic_launcher_round.webp",
            "res/mipmap-xxhdpi/ic_launcher_foreground.webp",
            "res/mipmap-xxxhdpi/ic_launcher.webp",
            "res/mipmap-xxxhdpi/ic_launcher_round.webp",
            "res/mipmap-xxxhdpi/ic_launcher_foreground.webp",
            "ic_launcher-playstore.png",
        )

        copiedResources.forEach { relativePath ->
            val expected = rnMain.resolve(relativePath)
            val actual = appMain.resolve(relativePath)

            assertTrue("RN reference resource should exist: $expected", Files.exists(expected))
            assertTrue("Android resource should exist: $actual", Files.exists(actual))
            assertArrayEquals(
                "Android resource should match RN reference byte-for-byte: $relativePath",
                Files.readAllBytes(expected),
                Files.readAllBytes(actual),
            )
        }
    }

    @Test
    fun `default Android template launcher resources are removed`() {
        val removedTemplateResources = listOf(
            "res/mipmap-anydpi/ic_launcher.xml",
            "res/mipmap-anydpi/ic_launcher_round.xml",
            "res/drawable/ic_launcher_background.xml",
            "res/drawable/ic_launcher_foreground.xml",
        )

        removedTemplateResources.forEach { relativePath ->
            val actual = appMain.resolve(relativePath)
            assertFalse("Template launcher resource should be removed: $actual", Files.exists(actual))
        }
    }

    private fun assertContains(path: Path, expected: String) {
        assertTrue("Expected file to exist: $path", Files.exists(path))
        val text = Files.readString(path)
        assertTrue(
            "Expected $path to contain:\n$expected",
            text.contains(expected),
        )
    }

    private fun parseXml(path: Path): Document {
        assertTrue("Expected XML file to exist: $path", Files.exists(path))
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(path.toFile())
            .apply { documentElement.normalize() }
    }

    private fun assertAndroidAttribute(element: Element, name: String, expected: String) {
        assertEquals("Expected android:$name on <${element.tagName}>", expected, element.androidAttribute(name))
    }

    private fun assertStyleParent(document: Document, styleName: String, expected: String) {
        val style = document.styleElement(styleName)
        assertEquals("Expected parent for style $styleName", expected, style.getAttribute("parent"))
    }

    private fun assertStyleItem(document: Document, styleName: String, itemName: String, expected: String) {
        val style = document.styleElement(styleName)
        val actual = style.childElements("item")
            .firstOrNull { it.getAttribute("name") == itemName }
            ?.textContent
            ?.trim()

        assertEquals("Expected item $itemName in style $styleName", expected, actual)
    }

    private fun assertColorValue(path: Path, colorName: String, expected: String) {
        val color = parseXml(path)
            .elements("color")
            .firstOrNull { it.getAttribute("name") == colorName }
            ?: throw AssertionError("Expected color $colorName in $path")

        assertEquals("Expected color value for $colorName", expected, color.textContent.trim())
    }

    private fun Document.firstElement(tagName: String): Element {
        return elements(tagName).firstOrNull()
            ?: throw AssertionError("Expected XML element <$tagName>")
    }

    private fun Document.styleElement(styleName: String): Element {
        return elements("style").firstOrNull { it.getAttribute("name") == styleName }
            ?: throw AssertionError("Expected style $styleName")
    }

    private fun Document.elements(tagName: String): List<Element> {
        return getElementsByTagName(tagName).asElements()
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val children = childNodes
        val elements = mutableListOf<Element>()

        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.tagName == tagName) {
                elements += child
            }
        }

        return elements
    }

    private fun Element.androidAttribute(name: String): String {
        return getAttributeNS(androidNamespace, name)
    }

    private fun NodeList.asElements(): List<Element> {
        val elements = mutableListOf<Element>()

        for (index in 0 until length) {
            val node = item(index)
            if (node is Element) {
                elements += node
            }
        }

        return elements
    }

    private fun extractFunctionBody(source: String, functionName: String): String {
        val functionMatch = Regex("""\bfun\s+$functionName\s*\(""").find(source)
            ?: throw AssertionError("Expected function $functionName")
        val openingBrace = source.indexOf('{', startIndex = functionMatch.range.last + 1)
        if (openingBrace < 0) {
            throw AssertionError("Expected function $functionName to have a body")
        }

        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(openingBrace + 1, index)
                    }
                }
            }
        }

        throw AssertionError("Expected function $functionName body to close")
    }

    private fun locateProjectRoot(): Path {
        val userDir = Paths.get("").toAbsolutePath().normalize()
        val candidates = listOfNotNull(userDir, userDir.parent)

        return candidates.firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate project root from $userDir")
    }

    private fun locateRnMain(projectRoot: Path): Path {
        val candidates = listOf(
            projectRoot.resolve("../MusicFree/android/app/src/main").normalize(),
            projectRoot.resolve("../../MusicFree/android/app/src/main").normalize(),
            projectRoot.resolve("../../../MusicFree/android/app/src/main").normalize(),
            projectRoot.resolve("../../../../MusicFree/android/app/src/main").normalize(),
        )

        return candidates.firstOrNull { Files.isDirectory(it) }
            ?: error("Could not locate RN Android source root from $projectRoot")
    }
}
