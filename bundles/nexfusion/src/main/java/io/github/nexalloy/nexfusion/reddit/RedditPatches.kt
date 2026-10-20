package io.github.nexalloy.nexfusion.reddit

import android.app.Activity
import android.graphics.Color
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.IHook
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.patch
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

// Ported from jkennethcarino/adobo (GPL-3.0),
// patches/src/main/kotlin/dev/jkcarino/adobo/patches/reddit/ (Reddit 2026.38.0 - 2026.14.0).
//
// Adobo rewrites Reddit's bytecode at build time (returnEarly stubs and one-instruction edits);
// this file installs runtime hooks that reach the same results. Each patch keeps its upstream
// default for its `use` flag so the manager's toggles match Adobo's picker.

/**
 * Upstream rewrites the [android.app.Activity.ScreenCaptureCallback] implementation and both
 * banner listeners so they never fire. The runtime port reaches the same effect one step earlier,
 * replacing the callback Reddit registers, so a screenshot never reaches Reddit's detection code.
 */
val DisableScreenshotBanner = patch(
    name = "Disable Reddit screenshot banner",
    description = "Disables the banner that shows up after taking a screenshot.",
) {
    val callbackClass = runCatching {
        Class.forName("android.app.Activity\$ScreenCaptureCallback", false, classLoader)
    }.getOrNull()
    if (callbackClass == null) {
        PatcherLog.info { "NexFusion: ScreenCaptureCallback not present, screenshot banner patch skipped" }
        return@patch
    }
    val register = XposedHelpers.findMethodExactIfExists(
        Activity::class.java,
        "registerScreenCaptureCallback",
        Executor::class.java,
        callbackClass,
    )
    if (register != null) {
        XposedBridge.hookMethod(register, XC_MethodReplacement.DO_NOTHING)
    }
    val unregister = XposedHelpers.findMethodExactIfExists(
        Activity::class.java,
        "unregisterScreenCaptureCallback",
        callbackClass,
    )
    if (unregister != null) {
        XposedBridge.hookMethod(unregister, XC_MethodReplacement.DO_NOTHING)
    }
}

/** Upstream replaces the click tracking getters with early returns. */
val OpenLinksDirectly = patch(
    name = "Open Reddit links directly",
    description = "Opens external links without going through the out.reddit.com redirector.",
) {
    GetAllowClickTrackingFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(false)) }
    GetOutboundClickTrackingFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(false)) }
}

/**
 * Upstream deletes the `OnAutoRefreshFeed` dispatch instructions; at runtime the dispatcher
 * completes without running its body, which is the same effect for this single-purpose method.
 */
val DisableAutoRefresh = patch(
    name = "Disable Reddit home feed auto-refresh",
    description = "Stops the home feed from refreshing automatically after viewing a post or being away.",
    use = false,
) {
    OnAutoRefreshFeedFingerprint.memberOrNull?.let { member ->
        XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Keep the original return shape: void dispatchers skip, value dispatchers see Unit.
                val method = param.method
                param.result = if (method is Method && method.returnType == Void.TYPE) null else Unit
            }
        })
    }
}

/** Upstream returns early from the `AppResumeAction` dispatcher when redirecting to home. */
val DisableHomeRedirect = patch(
    name = "Disable Reddit home screen redirect",
    description = "Stops Reddit from redirecting to the home screen when the app resumes from the background.",
) {
    AppResumeRedirectFingerprint.memberOrNull?.let { member ->
        XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val arg = param.args.firstOrNull()?.toString().orEmpty()
                if (arg.contains("REDIRECT_TO_HOME")) {
                    param.result = null
                }
            }
        })
    }
}

/**
 * Upstream forces the color and contrast constants inside Reddit's edge-to-edge setup; at runtime
 * the same values are forced on every framework call instead, which also covers later calls.
 * The framework class holding the implementation changed across Android versions, so both the
 * public [android.view.Window] method and the PhoneWindow override are attempted on their own.
 */
val TransparentSystemNavBar = patch(
    name = "Transparent Reddit navigation bar",
    description = "Makes the system navigation bar fully transparent.",
    use = false,
) {
    forceTransparentNavigationBar("android.view.Window")
    forceTransparentNavigationBar("com.android.internal.policy.PhoneWindow")
}

private fun IHook.forceTransparentNavigationBar(className: String) {
    val colorHooked = runCatching {
        XposedHelpers.findAndHookMethod(
            className, classLoader, "setNavigationBarColor", Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = Color.TRANSPARENT
                }
            },
        )
        true
    }.getOrDefault(false)
    val contrastHooked = runCatching {
        XposedHelpers.findAndHookMethod(
            className, classLoader, "setNavigationBarContrastEnforced", Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = false
                }
            },
        )
        true
    }.getOrDefault(false)
    if (!colorHooked && !contrastHooked) {
        PatcherLog.info { "NexFusion: $className navigation bar methods not found" }
    }
}

/**
 * Partial port: upstream additionally rewrites fields inside several `toString()` methods, which
 * cannot be expressed as runtime hooks; the score getters below are the portable part. Opt-in.
 */
val HideUpvoteScores = patch(
    name = "Hide Reddit upvote scores",
    description = "Stubs the score getters so upvote counts report zero or hidden.",
    use = false,
) {
    GetScoreFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(0)) }
    GetHideScoreFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(true)) }
}

/**
 * Partial port: upstream also rewrites the `toString()` view-count fields, which cannot be
 * expressed as runtime hooks; the view-count getter below is the portable part. Opt-in.
 */
val HideViewCounts = patch(
    name = "Hide Reddit post view counts",
    description = "Makes the post view-count getter report nothing so the views indicator is empty.",
    use = false,
) {
    GetViewCountFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(null)) }
}

val RedditPatches = arrayOf(
    DisableScreenshotBanner,
    OpenLinksDirectly,
    RedgifsAudio,
    RedgifsHd,
    DisableAutoRefresh,
    DisableHomeRedirect,
    TransparentSystemNavBar,
    HideUpvoteScores,
    HideViewCounts,
    HideAds,
    SanitizeSharingLinks,
    StartAsGuest,
)

