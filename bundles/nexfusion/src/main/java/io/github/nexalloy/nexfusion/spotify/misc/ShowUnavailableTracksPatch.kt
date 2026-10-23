package io.github.nexalloy.nexfusion.spotify.misc

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.nexfusion.spotify.showUnavailableTracksField
import io.github.nexalloy.patch

// Ported from Femify (`yobreehs/yfimeF`),
// app/src/main/java/io/github/a092devs/revancedxposed/spotify/misc/ShowUnavailableTracksPatch.kt,
// GPL-3.0.

/**
 * Forces Spotify's own "Show unplayable songs" setting on.
 *
 * Spotify already ships this setting (`show_unavailable_tracks`); when disabled the app hides
 * tracks that cannot be played (wrong market, removed, …) from collections instead of showing
 * them greyed out. The setting is stored in the server-synced reactive settings state, and the
 * UI reads the boolean field of that state directly.
 *
 * We make sure every freshly built settings state has the field set, which makes the app render
 * unavailable tracks again — at least greyed out. Making them actually playable is a separate,
 * server-side concern (the audio entitlement is enforced by the backend, not by the client).
 */
val ShowUnavailableTracks = patch(
    name = "Show unplayable songs",
    description = "Forces Spotify's own \"Show unplayable songs\" setting on, so tracks that " +
        "cannot be played (wrong market, removed, …) stay visible in collections instead of " +
        "being hidden. Playing them still depends on the account.",
) {
    runCatching {
        val field = ::showUnavailableTracksField.field
        var writeFailureReported = false
        XposedBridge.hookAllConstructors(field.declaringClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    XposedHelpers.setBooleanField(param.thisObject, field.name, true)
                }.onFailure { err ->
                    // Report once: this runs on every settings state construction.
                    if (!writeFailureReported) {
                        writeFailureReported = true
                        PatcherLog.error(err) {
                            "Show unplayable songs: cannot set " +
                                "${field.declaringClass.simpleName}.${field.name}"
                        }
                    }
                }
            }
        })
        PatcherLog.info {
            "Show unplayable songs: forced ${field.declaringClass.simpleName}.${field.name} = true"
        }
    }.onFailure { err ->
        PatcherLog.info(err) { "NexFusion: Show unplayable songs anchor not found" }
    }
}
