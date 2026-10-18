package io.github.nexalloy.morphe.youtube.misc.playback

import io.github.nexalloy.morphe.findMethodDirect
import org.luckypray.dexkit.query.enums.StringMatchType

/**
 * Fingerprint anchors for YouTube continue watching prompt patches.
 * Verified against YouTube 21.41.525.
 *
 * Each val is a DexKit finder that must resolve on the target build.
 */

/**
 * The method that decides whether to show the "Continue watching?" prompt.
 * Returns a boolean - hook it to return false to disable the prompt.
 */
internal val continueWatchingPromptMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("continue_watching", "continue watching"), StringMatchType.Contains)
            returnType = "boolean"
        }
    }.first()
}

/**
 * The method that adjusts inactivity comparison for continue watching.
 * Returns an int - hook it to return -1 to disable the prompt.
 */
internal val inactivityComparisonMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("inactivity", "continue_watching"), StringMatchType.Contains)
            returnType = "int"
        }
    }.first()
}
