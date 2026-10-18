package io.github.nexalloy.morphe.youtube.misc.playback

import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.patch

/**
 * Disables the "Continue watching?" prompt that appears after watching
 * several videos in a row. Ported from Morphe dev branch.
 *
 * Supports YouTube 21.41.525.
 */
val disableContinueWatchingPrompt = patch(
    name = "Disable continue watching prompt",
    description = "Stops YouTube from asking \"Continue watching?\" after watching several videos.",
) {
    dependsOn(VersionCheck)

    runCatching {
        ::continueWatchingPromptMethod.hookMethod(object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): Any = false
        })
        PatcherLog.info { "NexFusion YouTube: Disable continue watching prompt installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion YouTube: Disable continue watching prompt skipped" } }
}
