package io.github.nexalloy.nexfusion.instagram

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.nexfusion.instagram.download.DownloadMedia
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.patch
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.Locale

/**
 * Instagram patches ported from SysAdminDoc/HushGram (GPL-3.0),
 * supporting Instagram 451.0.0.53.80, 450.0.0.46.79, 447.0.0.55.81, and compatible versions.
 *
 * Three kinds of mechanism are used, and only these:
 *
 * - InstagramRequestFilter hooks `TigonServiceLayer.startRequest`, the kept Instagram API entry
 *   point, and answers the matched endpoints with an unreachable address. The ad, suggested,
 *   story-seen, live-viewer and analytics rules all ride on it. The endpoints each rule matches
 *   are asserted in InstagramPatchesTest.
 * - The ad injector is resolved with DexKit once per process and made to answer that nothing went
 *   in (see InstagramAds), the one ad mechanism that never reaches a request endpoint.
 * - Framework hooks: Window flag handling, ClipData text sharing and the screen capture
 *   callbacks, none of which depend on Instagram's obfuscation.
 * - The download menu hooks restored from this repository's own Piko bundle (see
 *   instagram/download/ and the NOTICE).
 *
 * Removed in this pass, because they only logged or hooked class names that do not exist in
 * release builds: "View DMs anonymously" and "Disable typing status" (both need the direct
 * message targets upstream resolves structurally, HushGram direct/seen and direct/typing), and
 * the log-only stubs "Ask before like", "Ask before refresh", "Disable video autoplay",
 * "Remove build expired popup" and "Open developer options" (each has a real upstream
 * implementation to port, from HushGram feed/confirm/, misc/buildexpiry/ and
 * misc/developeroptions/). "Download any media" was restored from the Piko bundle instead.
 * See the audit report for the backlog.
 */

private const val FLAG_SECURE = 0x2000
private fun withoutSecure(flags: Int): Int = flags and FLAG_SECURE.inv()

private fun clearSecure(params: Any?) {
    val layoutParams = params as? WindowManager.LayoutParams ?: return
    layoutParams.flags = withoutSecure(layoutParams.flags)
}

/**
 * The hosts that stay in the in-app browser, each with its subdomains: login, account linking,
 * checkout and Meta's own pages need the in-app browser's bridges, and HushGram keeps them.
 */
private val IN_APP_HOSTS = listOf(
    "instagram.com", "instagr.am", "ig.me", "facebook.com", "fb.com", "fb.me",
    "fb.watch", "messenger.com", "meta.com", "threads.net", "threads.com",
)

/** The extra Instagram's browser launchers put the link's context in; an ad's is an IABAdsContext. */
private const val IAB_CONTEXT_EXTRA = "EXTRA_IAB_CONTEXT"
private const val ADS_CONTEXT_CLASS = "com.facebook.browser.iabcontext.IABAdsContext"

/**
 * Instagram's in-app browser, a kept class name HushGram anchors on; the patch fails when it is
 * gone rather than letting links keep opening in an activity nothing redirects.
 */
private const val IN_APP_BROWSER_CLASS = "com.instagram.inappbrowser.fragments.BrowserLiteInMainProcessIGActivity"

val HideAds = patch(
    name = "Hide ads",
    description = "Hides sponsored posts, reels, stories, and feed ad units without leaving gaps.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.ADS)
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.REELS_ADS)
    InstagramRequestFilter.ensureHooks(classLoader)
    guardAdInjector()
    PatcherLog.info { "NexFusion Instagram: ad endpoints are blocked and the ad injector is guarded" }
}

val HideSuggestedPosts = patch(
    name = "Hide suggested posts",
    description = "Removes suggested posts, recommended accounts, and explore chaining suggestions from the feed.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.SUGGESTED)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: suggestion endpoints are blocked through the Tigon filter" }
}

val HideReelsAds = patch(
    name = "Hide reels ads",
    description = "Suppresses sponsored reels in the Reels tab and viewer.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.REELS_ADS)
    InstagramRequestFilter.ensureHooks(classLoader)
    guardAdInjector()
    PatcherLog.info { "NexFusion Instagram: reels ad endpoints are blocked and the ad injector is guarded" }
}

val ViewStoriesAnonymously = patch(
    name = "View stories anonymously",
    description = "Prevents sending story seen receipts so you do not appear in story viewer lists.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.STORY_SEEN)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: story seen receipts are blocked through the Tigon filter" }
}

val ViewLiveAnonymously = patch(
    name = "View live anonymously",
    description = "Suppresses live viewer heartbeat pings so you do not appear on live stream viewer lists.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.LIVE_VIEWER)
    InstagramRequestFilter.ensureHooks(classLoader)
}

val AllowScreenshots = patch(
    name = "Allow screenshots",
    description = "Clears FLAG_SECURE so screenshots and screen recordings work everywhere, including disappearing media.",
) {
    val hook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            when (param.method.name) {
                "setFlags", "addFlags" -> {
                    val flags = param.args.getOrNull(0) as? Int ?: return
                    param.args[0] = withoutSecure(flags)
                }
                "setAttributes" -> clearSecure(param.args.getOrNull(0))
                "addView", "updateViewLayout" -> clearSecure(param.args.getOrNull(1))
            }
        }
    }

    // Window and its PhoneWindow implementation; PhoneWindow overrides setAttributes.
    for (className in listOf("android.view.Window", "com.android.internal.policy.PhoneWindow")) {
        val window = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
        window.declaredMethods
            .filter { it.name in listOf("setFlags", "addFlags", "setAttributes") }
            .forEach { XposedBridge.hookMethod(it, hook) }
    }

    // WindowManagerImpl receives the LayoutParams the window finally gets.
    runCatching { classLoader.loadClass("android.view.WindowManagerImpl") }
        .getOrNull()
        ?.declaredMethods
        ?.filter { method ->
            method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == View::class.java &&
                method.parameterTypes[1] == ViewGroup.LayoutParams::class.java &&
                (method.name == "addView" || method.name == "updateViewLayout")
        }
        ?.forEach { XposedBridge.hookMethod(it, hook) }

    PatcherLog.info { "NexFusion Instagram: FLAG_SECURE hooks installed" }
}

val BlockScreenshotDetection = patch(
    name = "Block screenshot detection",
    description = "Stops Instagram from notifying people when you take screenshots or screen recordings.",
) {
    var observerHooked = false
    var frameworkHooked = 0

    // The photo library observer every Meta screenshot detector shares keeps its class name in
    // release builds; returning at once in its onChange hides every screenshot from Instagram.
    runCatching {
        val observer = classLoader.loadClass("com.facebook.screenshot.ScreenshotContentObserver")
        val onChange = observer.declaredMethods.single {
            it.name == "onChange" && it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE, Uri::class.java))
        }
        XposedBridge.hookMethod(onChange, object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? = null
        })
        observerHooked = true
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: screenshot observer hook skipped" } }

    // Android 14+ activity screen capture callbacks; Android 15+ screen recording callbacks.
    val noop = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? = null
    }
    if (Build.VERSION.SDK_INT >= 34) {
        for (className in listOf("android.app.Activity", "androidx.activity.ComponentActivity")) {
            val activity = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            activity.declaredMethods
                .filter { it.name == "registerScreenCaptureCallback" && it.parameterTypes.size == 2 }
                .forEach {
                    XposedBridge.hookMethod(it, noop)
                    frameworkHooked++
                }
        }
    }
    if (Build.VERSION.SDK_INT >= 35) {
        runCatching { classLoader.loadClass("android.view.WindowManagerImpl") }
            .getOrNull()
            ?.declaredMethods
            ?.filter { it.name == "addScreenRecordingCallback" || it.name == "removeScreenRecordingCallback" }
            ?.forEach {
                XposedBridge.hookMethod(it, noop)
                frameworkHooked++
            }
    }

    if (!observerHooked && frameworkHooked == 0) {
        throw IllegalStateException("Block screenshot detection: no Instagram observer or framework call was found")
    }
    PatcherLog.info { "NexFusion Instagram: screenshot detection hooks installed (observer=$observerHooked, framework=$frameworkHooked)" }
}

val SanitizeShareLinks = patch(
    name = "Sanitize share links",
    description = "Strips tracking parameters (igsh, utm, fbclid) from shared URLs.",
) {
    runCatching {
        // Hook clip data / text sharing
        val clipClass = classLoader.loadClass("android.content.ClipData")
        clipClass.declaredMethods.filter { it.name == "newPlainText" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val text = param.args.getOrNull(1) as? CharSequence ?: return
                    val sanitized = InstagramLinks.sanitizeUrl(text.toString())
                    param.args[1] = sanitized
                }
            })
        }
    }
}

val OpenLinksExternally = patch(
    name = "Open links in external browser",
    description = "Opens web links directly in your default browser instead of Instagram's in-app browser. Instagram pages and links opened for an ad stay in the app.",
) {
    // Instagram's own in-app browser is the activity HushGram anchors on; it is a kept name the
    // manifest declares, and the link launchers route through it. Facebook's browser classes are
    // hooked too for surfaces that reach them. The redirect runs after each activity's own
    // lifecycle has finished, so the framework never sees a skipped super call (a before-hook
    // that overwrote onCreate used to make the activity crash with SuperNotCalledException).
    var instagramBrowserHooked = false
    var hooked = 0
    val redirect = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val activity = param.thisObject as? Activity ?: return
            val intent = if (param.method.name == "onNewIntent") {
                param.args.getOrNull(0) as? Intent
            } else {
                activity.intent
            }
            openExternally(activity, intent)
        }
    }
    for (className in listOf(
        IN_APP_BROWSER_CLASS,
        "com.facebook.browser.litev2.lite.BrowserLiteDIActivity",
        "com.facebook.browser.lite.BrowserLiteActivity",
    )) {
        val browser = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
        var classHooked = 0
        for (method in browser.declaredMethods) {
            val hookable = when (method.name) {
                "onCreate" -> method.parameterTypes.contentEquals(arrayOf(Bundle::class.java))
                "onNewIntent" -> method.parameterTypes.contentEquals(arrayOf(Intent::class.java))
                else -> false
            }
            if (hookable) {
                XposedBridge.hookMethod(method, redirect)
                hooked++
                classHooked++
            }
        }
        if (className == IN_APP_BROWSER_CLASS && classHooked > 0) instagramBrowserHooked = true
    }
    if (!instagramBrowserHooked) {
        throw IllegalStateException("Open links in external browser: Instagram's in-app browser activity was not found (a new name?)")
    }
    PatcherLog.info { "NexFusion Instagram: $hooked in-app browser lifecycle hooks installed" }
}

/**
 * Opens the intent's web link in the user's browser. The link is resolved through Instagram's
 * click tracker, checked as it is sent, and sent with Meta's click id stripped; Instagram-family
 * pages and links opened for an ad stay in the in-app browser, as HushGram keeps them.
 */
private fun openExternally(activity: Activity, intent: Intent?) {
    val uri = intent?.data ?: return
    if (!isWebLink(uri)) return
    if (isAdLink(activity, intent)) return
    val target = Uri.parse(InstagramLinks.unwrapShims(uri.toString()))
    val host = target.host?.lowercase(Locale.ROOT) ?: return
    if (IN_APP_HOSTS.any { host == it || host.endsWith(".$it") }) return
    // fbclid is Meta's click id; the site the link opens could otherwise tell Meta it was
    // followed. Nothing else in the link changes.
    val cleaned = Uri.parse(InstagramLinks.withoutClickId(target.toString()))
    if (!isWebLink(cleaned)) return
    if (InstagramLinks.openExternally(activity, cleaned.toString())) {
        PatcherLog.info { "NexFusion Instagram: opened $host in the external browser" }
        runCatching { activity.finish() }
    }
}

/** Whether [uri] is a web address that can be sent to a browser, host included. */
private fun isWebLink(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
}

/**
 * Whether the link was opened for an ad, which stays in the app. A link followed inside a page
 * the browser already shows comes with no context of its own, so the context of the page it was
 * followed from is used; context that cannot be read counts as an ad, so the link stays where
 * Instagram put it.
 */
@Suppress("DEPRECATION")
private fun isAdLink(activity: Activity, intent: Intent): Boolean {
    return try {
        var context: Parcelable? = intent.getParcelableExtra(IAB_CONTEXT_EXTRA)
        if (context == null && activity.intent !== intent) {
            context = activity.intent?.getParcelableExtra(IAB_CONTEXT_EXTRA)
        }
        var type: Class<*>? = context?.javaClass
        while (type != null) {
            if (type.name == ADS_CONTEXT_CLASS) return true
            type = type.superclass
        }
        false
    } catch (e: Exception) {
        PatcherLog.info(e) { "NexFusion Instagram: the link's context could not be read, leaving it in the app" }
        true
    }
}

val DisableAnalytics = patch(
    name = "Disable analytics",
    description = "Stops sending usage reports, crash reports, and logging events to Meta.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.ANALYTICS)
    InstagramRequestFilter.ensureHooks(classLoader)
}

val StopStoryAutoAdvance = patch(
    name = "Stop story auto-advance",
    description = "Keeps each story on screen until you tap or swipe, preventing stories from auto-advancing.",
) {
    val viewerClass = runCatching {
        classLoader.loadClass("instagram.features.stories.fragment.ReelViewerFragment")
    }.getOrNull()
    if (viewerClass != null) {
        var count = 0
        for (method in viewerClass.declaredMethods) {
            if (method.isBridge && method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 && method.parameterTypes[0] == Any::class.java
            ) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                    }
                })
                count++
            }
        }
        PatcherLog.info { "NexFusion Instagram: Stop story auto-advance installed ($count listeners)" }
    } else {
        PatcherLog.info { "NexFusion Instagram: ReelViewerFragment not found for auto-advance hook" }
    }
}

internal val typingStatusMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("IGDirectTypingStatusService", "is_typing_indicator_enabled"), StringMatchType.Contains)
            returnType = "void"
            paramTypes = listOf("boolean")
        }
    }.first()
}

val HideTypingStatus = patch(
    name = "Hide typing status",
    description = "Stops people you are chatting with from seeing when you are typing in direct messages.",
) {
    runCatching {
        ::typingStatusMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val isTyping = param.args.getOrNull(0) as? Boolean ?: return
                if (isTyping) {
                    param.result = null
                }
            }
        })
        PatcherLog.info { "NexFusion Instagram: typing status indicator suppressed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: typing status hook skipped" } }
}

internal val feedDoubleTapMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(
                listOf("DefaultMediaHolderGestureDetectorDelegateImpl#onDoubleTapMedia called with null activity"),
                StringMatchType.Contains,
            )
        }
    }.first()
}

val TurnOffDoubleTapLike = patch(
    name = "Turn off double tap to like",
    description = "Stops double-tapping posts and reels from liking them, preventing accidental likes while scrolling.",
) {
    runCatching {
        ::feedDoubleTapMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = null
            }
        })
        PatcherLog.info { "NexFusion Instagram: feed double-tap like suppressed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: feed double-tap hook skipped" } }
}

val InstagramPatches = arrayOf(
    HideAds,
    HideSuggestedPosts,
    HideReelsAds,
    ViewStoriesAnonymously,
    ViewLiveAnonymously,
    StopStoryAutoAdvance,
    HideTypingStatus,
    TurnOffDoubleTapLike,
    AllowScreenshots,
    BlockScreenshotDetection,
    SanitizeShareLinks,
    OpenLinksExternally,
    DownloadMedia,
    DisableAnalytics,
)
