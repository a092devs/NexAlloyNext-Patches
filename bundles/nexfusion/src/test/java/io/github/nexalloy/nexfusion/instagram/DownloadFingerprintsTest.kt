package io.github.nexalloy.nexfusion.instagram

import io.github.nexalloy.ApkContext
import io.github.nexalloy.nexfusion.instagram.download.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DownloadFingerprintsTest {
    @Test
    fun testInstagramDownloadFingerprints() {
        val apks = File("binaries").listFiles { file ->
            file.isFile && file.name.startsWith("com.instagram.android-") && file.name.endsWith(".apk")
        }?.sortedBy { it.name }.orEmpty()
        if (apks.isEmpty()) {
            println("No Instagram APK found, skipping")
            return
        }
        println("Instagram APKs: ${apks.joinToString { "${it.name} (${it.length()})" }}")

        for (apkFile in apks) {
            println("=== ${apkFile.name} ===")
            val context = ApkContext(apkFile.absolutePath)
            val dexkit = context.dexkit

            assertNotNull(overflowMenuOptionsMethod(dexkit))
            assertNotNull(overflowMenuCreatorClass(dexkit))
            assertNotNull(overflowMenuOptionClickMethod(dexkit))
            val sheets = sheetMenuMethods(dexkit)
            assertTrue(sheets.isNotEmpty(), "sheetMenuMethods must not be empty")
            println("Verified ${sheets.size} sheet menu methods")
            val builder = sheetMenuBuilderClass(dexkit)
            assertNotNull(builder)
            println("Verified sheetMenuBuilderClass: ${builder.name}")
            val storyLabels = storyMenuLabelsMethod(dexkit)
            assertNotNull(storyLabels)
            println("Verified storyMenuLabelsMethod: $storyLabels")
            assertNotNull(storyMenuSheetMethod(dexkit))
            val storyRow = storyMenuRowConstructor(dexkit)
            assertNotNull(storyRow)
            println("Verified storyMenuRowConstructor: $storyRow")
            assertNotNull(profilePictureActionsMethod(dexkit))
            assertNotNull(profilePictureActionConstructor(dexkit))
            assertNotNull(highlightMenuMethod(dexkit))
            assertNotNull(highlightMenuBuilderClass(dexkit))
            assertNotNull(highlightMenuAddMethod(dexkit))
            assertNotNull(storyHeaderBinderMethod(dexkit))
            println("Verified storyHeaderBinderMethod: ${storyHeaderBinderMethod(dexkit)}")

            // Non-download anchors used by the rest of the Instagram patch set.
            assertNotNull(typingStatusMethod(dexkit))
            println("Verified typingStatusMethod: ${typingStatusMethod(dexkit)}")
            assertNotNull(feedDoubleTapMethod(dexkit))
            println("Verified feedDoubleTapMethod: ${feedDoubleTapMethod(dexkit)}")

            // Kept class names and entry points the runtime hooks load directly.
            fun assertClassExists(name: String) {
                val found = dexkit.findClass { matcher { className(name) } }
                assertTrue(found.isNotEmpty(), "Class not found: $name")
                println("Verified class: $name")
            }
            assertClassExists("com.instagram.api.tigon.TigonServiceLayer")
            assertTrue(
                dexkit.findMethod {
                    matcher {
                        declaredClass("com.instagram.api.tigon.TigonServiceLayer")
                        this.name = "startRequest"
                    }
                }.isNotEmpty(),
                "TigonServiceLayer.startRequest not found",
            )
            assertClassExists("instagram.features.stories.fragment.ReelViewerFragment")
            val observerExists = dexkit.findClass {
                matcher { className("com.facebook.screenshot.ScreenshotContentObserver") }
            }.isNotEmpty()
            println("Screenshot observer present: $observerExists (framework callbacks are the fallback)")
            assertClassExists("com.instagram.inappbrowser.fragments.BrowserLiteInMainProcessIGActivity")

            println("ALL FINGERPRINTS PASSED FOR ${apkFile.name}!")
        }
    }
}
