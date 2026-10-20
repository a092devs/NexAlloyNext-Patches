package io.github.nexalloy.nexfusion.spotify.premium

import app.twoeno.extension.spotify.UnlockPremiumPatch
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.callMethodOrNull
import io.github.nexalloy.hookMethod
import io.github.nexalloy.invokeOriginalMethod
import io.github.nexalloy.patch
import io.github.nexalloy.nexfusion.spotify.assistantContextDeserializerMethod
import io.github.nexalloy.nexfusion.spotify.buildQueryParametersMethod
import io.github.nexalloy.nexfusion.spotify.playerOptionOverridesBuilderClass
import io.github.nexalloy.nexfusion.spotify.productStateAttributesGetter

/**
 * Unlocks the client-side Spotify Premium playback and UI.
 *
 * Ported from ReVanced's `UnlockPremiumPatch` through the Femify fork (`sheerboy/Femify`,
 * `yobreehs/yfimeF`), which keeps it working on recent Spotify builds. Unlike the first attempt
 * in this repository, every hook is installed on its own: a moved anchor on a new app version
 * disables only that block instead of the whole patch, and the app keeps working either way.
 *
 * Spotify restricts accounts that use modified clients (NexAlloyNext-Patches#3) and no client
 * hook can undo that, so the patch is opt-in and must not be used on an account the user cannot
 * afford to lose.
 */
val UnlockPremium = patch(
    name = "Unlock Spotify Premium",
    description = "Unlocks on-demand playback, unforced shuffle, unlimited skips and the Premium " +
        "settings UI, and adds the popular tracks back to artist pages. Spotify restricts " +
        "accounts that use modified clients (#3), so this is disabled by default and should not " +
        "be enabled on an account you cannot lose.",
    use = false,
) {
    // The account attributes of the remote config product state drive the playback restrictions
    // and most of the Premium UI. The client wraps the map in `Collections.unmodifiableMap`, but
    // the attribute objects are mutable and are rewritten in place.
    guarded("account attributes") {
        ::productStateAttributesGetter.hookMethod {
            after { param ->
                @Suppress("UNCHECKED_CAST")
                val attributes = param.result as? Map<String, Any?> ?: return@after
                UnlockPremiumPatch.overrideAttributes(attributes)
            }
        }
    }

    // Artist pages only include the popular tracks when the query builder is told to add them.
    //
    // The matched method is an R8-merged lambda: the branch whose arguments are
    // `(Map, Boolean, …)` assembles the query parameters and the Boolean is the popular-tracks
    // flag. The other branches take different argument types, so the type check below selects
    // exactly the query builder branch.
    guarded("popular tracks") {
        ::buildQueryParametersMethod.hookMethod {
            before { param ->
                if (param.args.size != 3) return@before
                if (param.args[0] !is Map<*, *> || param.args[1] !is Boolean) return@before
                runCatching {
                    param.result = param.invokeOriginalMethod(
                        arrayOf(param.args[0], true, param.args[2])
                    )
                }.onFailure {
                    PatcherLog.error(it) {
                        "Unlock premium: popular tracks could not be enabled"
                    }
                }
            }
        }
    }

    // Google Assistant hands over `spotify:station:` contexts, which cannot be played on demand.
    guarded("assistant context") {
        ::assistantContextDeserializerMethod.hookMethod {
            after { param -> UnlockPremiumPatch.stripStationUris(param.result) }
        }
    }

    // Spotify forces shuffle when a voice assistant asks for an album or a playlist.
    guarded("forced shuffle") {
        val builder = ::playerOptionOverridesBuilderClass.clazz
        val build = XposedHelpers.findMethodExactIfExists(builder, "build")
            ?: throw Exception("build() of the player option overrides not found")
        build.hookMethod {
            before { param -> param.thisObject.callMethodOrNull("shufflingContext", false) }
        }
    }
}

/**
 * Installs [block] and logs any failure, so a single moved anchor on a new app version does not
 * disable the other Premium hooks.
 */
private inline fun guarded(name: String, block: () -> Unit) {
    try {
        block()
    } catch (throwable: Throwable) {
        PatcherLog.error(throwable) { "Unlock premium: hook '$name' could not be installed" }
    }
}
