package io.github.nexalloy.nexfusion

import io.github.nexalloy.ApkContext
import io.github.nexalloy.nexfusion.discord.PrivacyTarget
import io.github.nexalloy.nexfusion.discord.PrivacyTargets
import io.github.nexalloy.nexfusion.facebook.adBreakBannerFetch
import io.github.nexalloy.nexfusion.facebook.adBreakFetch
import io.github.nexalloy.nexfusion.facebook.adBreakVideoFetches
import io.github.nexalloy.nexfusion.facebook.advertiserIdReportSender
import io.github.nexalloy.nexfusion.facebook.attachmentMediaGetter
import io.github.nexalloy.nexfusion.facebook.attachmentSubattachmentsGetter
import io.github.nexalloy.nexfusion.facebook.canViewerDownloadGates
import io.github.nexalloy.nexfusion.facebook.deferredCardState
import io.github.nexalloy.nexfusion.facebook.feedFunnel
import io.github.nexalloy.nexfusion.facebook.inorganicClashStoryBuckets
import io.github.nexalloy.nexfusion.facebook.insertAdsSplice
import io.github.nexalloy.nexfusion.facebook.instreamBreakFetch
import io.github.nexalloy.nexfusion.facebook.launchCustomDataSender
import io.github.nexalloy.nexfusion.facebook.newInstallSender
import io.github.nexalloy.nexfusion.facebook.postMenuFillers
import io.github.nexalloy.nexfusion.facebook.preEofReelsInjectors
import io.github.nexalloy.nexfusion.facebook.privacySandboxRegisterSender
import io.github.nexalloy.nexfusion.facebook.profileReelsAdsFetch
import io.github.nexalloy.nexfusion.facebook.adBucketDataSourceStoryBuckets
import io.github.nexalloy.nexfusion.facebook.adPaginatingStoryBuckets
import io.github.nexalloy.nexfusion.facebook.realtimeIntentAdInsert
import io.github.nexalloy.nexfusion.facebook.reelMoreSheetBuilder
import io.github.nexalloy.nexfusion.facebook.reelsAdPoolVends
import io.github.nexalloy.nexfusion.facebook.savePhotoAction
import io.github.nexalloy.nexfusion.facebook.storyAttachedStoryGetter
import io.github.nexalloy.nexfusion.facebook.storyAttachmentsGetter
import io.github.nexalloy.nexfusion.facebook.storyCapabilityConstructor
import io.github.nexalloy.nexfusion.facebook.storySaveAction
import io.github.nexalloy.nexfusion.facebook.storySaveCapabilityMethod
import io.github.nexalloy.nexfusion.facebook.storyViewerMidCardBuckets
import io.github.nexalloy.nexfusion.facebook.reelsVideoAdsFetch
import io.github.nexalloy.nexfusion.facebook.sfdAdInsert
import io.github.nexalloy.nexfusion.facebook.videoPlayerParamsConstructors
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.luckypray.dexkit.DexKitBridge
import java.io.File

class FacebookDiscordCompatibilityTest {

    private val facebookApk = "compat-binaries/com.facebook.katana-583.0.0.39.55.apk"
    private val discordApk = "compat-binaries/com.discord-349.9.apk"
    private val discord350Apk = "compat-binaries/com.discord-350.8.apk"

    @Test
    fun `facebook finders on installed 583`() {
        assumeTrue(File(facebookApk).isFile, "Facebook 583 APK is not in binaries/")
        val context = ApkContext(facebookApk)
        context.setupCurrentThread()
        val checks = listOf<Pair<String, () -> Any?>>(
            "feedFunnel" to { feedFunnel(context.dexkit) },
            "reelsAdPoolVends" to { reelsAdPoolVends(context.dexkit) },
            "reelsVideoAdsFetch" to { reelsVideoAdsFetch(context.dexkit) },
            "instreamBreakFetch" to { instreamBreakFetch(context.dexkit) },
            "profileReelsAdsFetch" to { profileReelsAdsFetch(context.dexkit) },
            "advertiserIdReportSender" to { advertiserIdReportSender(context.dexkit) },
            "launchCustomDataSender" to { launchCustomDataSender(context.dexkit) },
            "privacySandboxRegisterSender" to { privacySandboxRegisterSender(context.dexkit) },
            "newInstallSender" to { newInstallSender(context.dexkit) },
            "insertAdsSplice" to { insertAdsSplice(context.dexkit) },
            "realtimeIntentAdInsert" to { realtimeIntentAdInsert(context.dexkit) },
            "sfdAdInsert" to { sfdAdInsert(context.dexkit) },
            "adBreakFetch" to { adBreakFetch(context.dexkit) },
            "adBreakBannerFetch" to { adBreakBannerFetch(context.dexkit) },
            "adBreakVideoFetches" to { adBreakVideoFetches(context.dexkit) },
            "deferredCardState" to { deferredCardState(context.dexkit) },
            "inorganicClashStoryBuckets" to { inorganicClashStoryBuckets(context.dexkit) },
            "adBucketDataSourceStoryBuckets" to { adBucketDataSourceStoryBuckets(context.dexkit) },
            "adPaginatingStoryBuckets" to { adPaginatingStoryBuckets(context.dexkit) },
            "storyViewerMidCardBuckets" to { storyViewerMidCardBuckets(context.dexkit) },
            "preEofReelsInjectors" to { preEofReelsInjectors(context.dexkit) },
            "postMenuFillers" to { postMenuFillers(context.dexkit) },
            "storyAttachmentsGetter" to { storyAttachmentsGetter(context.dexkit) },
            "storyAttachedStoryGetter" to { storyAttachedStoryGetter(context.dexkit) },
            "attachmentMediaGetter" to { attachmentMediaGetter(context.dexkit) },
            "attachmentSubattachmentsGetter" to { attachmentSubattachmentsGetter(context.dexkit) },
            "savePhotoAction" to { savePhotoAction(context.dexkit) },
            "canViewerDownloadGates" to { canViewerDownloadGates(context.dexkit) },
            "reelMoreSheetBuilder" to { reelMoreSheetBuilder(context.dexkit) },
            "videoPlayerParamsConstructors" to { videoPlayerParamsConstructors(context.dexkit) },
            "storyCapabilityConstructor" to { storyCapabilityConstructor(context.dexkit) },
            "storySaveCapabilityMethod" to { storySaveCapabilityMethod(context.dexkit) },
            "storySaveAction" to { storySaveAction(context.dexkit) },
        )
        val failures = mutableListOf<String>()
        for ((name, check) in checks) {
            val result = runCatching(check)
            val value = result.getOrNull()
            val problem = result.exceptionOrNull()?.message
                ?: if (value is Collection<*> && value.isEmpty()) "no matches" else null
            if (problem != null) failures += "$name: $problem"
            println("$name: ${problem ?: value}")
        }
        val categoryClass = context.dexkit
            .getClassData("Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;")
        val categoryStrings = categoryClass?.methods
            ?.firstOrNull { it.name == "<clinit>" }
            ?.instructions
            ?.mapNotNull { it.string }
            .orEmpty()
        println("GraphQLFeedStoryCategory values: $categoryStrings")
        val reelsCategories = listOf("FB_SHORTS", "FB_SHORTS_FALLBACK", "END_OF_FEED_REELS")
        if (!categoryStrings.containsAll(reelsCategories)) {
            failures += "GraphQLFeedStoryCategory is missing ${reelsCategories - categoryStrings.toSet()}"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `discord privacy targets on installed 3499`() {
        assumeTrue(File(discordApk).isFile, "Discord 349.9 APK is not in binaries/")
        runCatching { System.loadLibrary("dexkit") }
            .recoverCatching { System.loadLibrary("libdexkit") }
            .getOrThrow()
        DexKitBridge.create(discordApk).use { dexkit ->
            val tables = listOf(
                "crash" to PrivacyTargets.crash,
                "telemetry" to PrivacyTargets.telemetry,
                "advertising" to PrivacyTargets.advertising,
                "attribution" to PrivacyTargets.attribution,
            )
            val failures = mutableListOf<String>()
            for ((label, targets) in tables) {
                val missing = targets.filter { target -> !exists(dexkit, target) }
                println("$label: ${targets.size - missing.size}/${targets.size} present")
                missing.forEach { failures += "$label: ${it.descriptor}" }
            }
            assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        }
    }

    @Test
    fun `discord privacy targets on installed 3508`() {
        assumeTrue(File(discord350Apk).isFile, "Discord 350.8 APK is not in compat-binaries/")
        runCatching { System.loadLibrary("dexkit") }
            .recoverCatching { System.loadLibrary("libdexkit") }
            .getOrThrow()
        DexKitBridge.create(discord350Apk).use { dexkit ->
            val tables = listOf(
                "crash" to PrivacyTargets.crash,
                "telemetry" to PrivacyTargets.telemetry,
                "advertising" to PrivacyTargets.advertising,
                "attribution" to PrivacyTargets.attribution,
            )
            val failures = mutableListOf<String>()
            for ((label, targets) in tables) {
                val missing = targets.filter { target -> !exists(dexkit, target) }
                println("350.8 $label: ${targets.size - missing.size}/${targets.size} present")
                missing.forEach { failures += "$label: ${it.descriptor}" }
            }
            assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        }
    }

    private fun exists(dexkit: DexKitBridge, target: PrivacyTarget): Boolean =
        runCatching { dexkit.getMethodData(target.descriptor) }.isSuccess
}
