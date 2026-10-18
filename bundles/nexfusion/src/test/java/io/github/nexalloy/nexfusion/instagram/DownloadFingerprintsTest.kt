package io.github.nexalloy.nexfusion.instagram

import io.github.nexalloy.ApkContext
import io.github.nexalloy.morphe.opcodeEnum
import io.github.nexalloy.nexfusion.instagram.download.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DownloadFingerprintsTest {
    @Test
    fun testInstagramDownloadFingerprints() {
        val apkFile = File("binaries/com.instagram.android-451.0.0.53.80.apk")
        if (!apkFile.exists()) {
            println("APK not found, skipping: ${apkFile.absolutePath}")
            return
        }
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
        println("ALL FINGERPRINTS PASSED!")
    }
}
