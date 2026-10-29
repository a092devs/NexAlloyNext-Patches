package io.github.nexalloy.nexfusion.discord

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.luckypray.dexkit.DexKitBridge
import java.io.File

/**
 * Checks the hard-coded privacy descriptors against the Discord APKs in `binaries/`.
 *
 * The privacy patches do not use fingerprints: each entry point is resolved by its exact JVM
 * descriptor at runtime, and a rename silently skips that hook. This test makes such a rename
 * visible before a release. It skips when no Discord APK is present, like the Instagram checks.
 */
class PrivacyTargetsApkTest {

    companion object {
        init {
            try {
                System.loadLibrary("dexkit")
            } catch (_: UnsatisfiedLinkError) {
                System.loadLibrary("libdexkit")
            }
        }
    }

    @Test
    fun `privacy targets exist in the Discord APK`() {
        val apks = File("binaries")
            .listFiles { file -> file.isFile && file.name.startsWith("com.discord-") && file.name.endsWith(".apk") }
            ?.sortedBy { it.name }
            .orEmpty()
        if (apks.isEmpty()) {
            println("No Discord APK found, skipping")
            return
        }

        val targets = listOf(
            PrivacyTargets.crash,
            PrivacyTargets.telemetry,
            PrivacyTargets.advertising,
            PrivacyTargets.attribution,
        ).flatten()

        for (apk in apks) {
            println("=== ${apk.name} ===")
            // ApkContext's version parser only accepts numeric versions; Discord reports
            // "351.11 - Stable", so open DexKit directly for this APK.
            DexKitBridge.create(apk.absolutePath).use { dexkit ->
                val missing = targets.filter { target ->
                    dexkit.findMethod { matcher { descriptor(target.descriptor) } }.isEmpty()
                }

                println("Verified ${targets.size - missing.size}/${targets.size} privacy targets")
                if (missing.isNotEmpty()) {
                    println("Missing privacy targets:")
                    missing.forEach { println("  - ${it.descriptor}") }
                }
                assertTrue(missing.isEmpty(), "${apk.name}: ${missing.size} privacy targets no longer exist")

                // The phone-font patch resolves these class names directly.
                assertTrue(
                    dexkit.findClass {
                        matcher { className("com.facebook.react.common.assets.ReactFontManager") }
                    }.isNotEmpty(),
                    "${apk.name}: ReactFontManager not found",
                )
                assertTrue(
                    dexkit.findMethod {
                        matcher {
                            declaredClass("com.facebook.react.common.assets.ReactFontManager")
                            name = "getTypeface"
                            paramTypes("java.lang.String", "com.facebook.react.common.assets.ReactFontManager\$TypefaceStyle", "android.content.res.AssetManager")
                        }
                    }.isNotEmpty(),
                    "${apk.name}: ReactFontManager.getTypeface not found",
                )
                assertTrue(
                    dexkit.findMethod {
                        matcher {
                            declaredClass("com.facebook.react.common.assets.ReactFontManager\$TypefaceStyle")
                            name = "getNearestStyle"
                            paramCount = 0
                        }
                    }.isNotEmpty(),
                    "${apk.name}: TypefaceStyle.getNearestStyle not found",
                )
                assertTrue(
                    dexkit.findClass {
                        matcher { className("com.facebook.react.bridge.Arguments") }
                    }.isNotEmpty(),
                    "${apk.name}: React Arguments not found",
                )
            }
        }
    }
}
