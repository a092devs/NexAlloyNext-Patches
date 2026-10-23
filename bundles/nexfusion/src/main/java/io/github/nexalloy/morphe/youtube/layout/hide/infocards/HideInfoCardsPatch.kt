package io.github.nexalloy.morphe.youtube.layout.hide.infocards

import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.patch

/**
 * Hides info cards that creators add to videos. Ported from Morphe dev branch.
 *
 * Supports YouTube 21.41.525.
 */
val hideInfoCardsPatch = patch(
    name = "Hide info cards",
    description = "Adds an option to hide info cards that creators add in the video player.",
) {
    dependsOn(VersionCheck)

    runCatching {
        ::hideInfoCardsMethod.hookMethod(object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam): Any = false
        })
        PatcherLog.info { "NexFusion YouTube: Hide info cards installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion YouTube: Hide info cards skipped" } }
}
