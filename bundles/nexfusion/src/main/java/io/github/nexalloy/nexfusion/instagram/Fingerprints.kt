package io.github.nexalloy.nexfusion.instagram

import io.github.nexalloy.morphe.findMethodDirect
import org.luckypray.dexkit.query.enums.StringMatchType

/**
 * Fingerprint anchors for the Instagram HushGram feature ports, verified against
 * Instagram 451.0.0.53.80 (see commit 4237101 in this repository's history).
 *
 * Each val is a DexKit finder that must resolve on the target build; the shared
 * FingerprintsKtTest harness runs them against the APK in `bundles/nexfusion/binaries/`
 * so an anchor that stops resolving fails the test instead of silently doing nothing
 * on the device.
 */

// 2. DM visual-seen marker — direct_v2/visual_threads/%s/item_seen/.
internal val visualSeenMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("direct_v2/visual_threads/%s/item_seen/", "raven_media"), StringMatchType.Contains)
            returnType = "void"
        }
    }.first()
}

// 3. High-quality media upload preference.
internal val highQualityPrefMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("high_quality_media_upload"), StringMatchType.Contains)
            returnType = "boolean"
        }
    }.first()
}

// 4. Feed video tap handler.
internal val feedMediaTapMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("FeedMediaTapHandlerV2-softError"), StringMatchType.Contains)
            returnType = "void"
        }
    }.first()
}

// 5. Profile header full-name binder (follows-you indicator anchor).
internal val profileHeaderFullNameMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("bindFullName"), StringMatchType.Contains)
            returnType = "void"
        }
    }.first()
}

// 6. Story viewer media binder (story mentions anchor).
internal val reelViewerBindMediaMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("ReelViewerItemBinder.bindMedia"), StringMatchType.Contains)
            returnType = "void"
        }
    }.first()
}

// 7. Relative-time formatter ("MMMM d") — exact-time patch anchor.
internal val timeFormatterMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("MMMM d"), StringMatchType.Contains)
            returnType = "java.lang.String"
            paramCount = 10
        }
    }.first()
}

// 8. User options overflow (profile picture anchor).
internal val profileOverflowMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("UserOptionsOverflowHelper"), StringMatchType.Contains)
            returnType = "void"
            paramTypes = listOf("android.view.View")
        }
    }.first()
}

// 9. Developer options opener (debug_options_error).
internal val optionsOpenerMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("debug_options_error"), StringMatchType.Contains)
            returnType = "void"
        }
    }.first()
}

// Home tab long-press (developer options trigger).
internal val homeLongPressMethod = findMethodDirect {
    runCatching {
        findMethod {
            matcher {
                usingStrings(listOf("click", "activity"), StringMatchType.Contains)
                name = "onLongClick"
                returnType = "boolean"
            }
        }.first()
    }.getOrElse {
        findMethod {
            matcher {
                usingStrings(listOf("home", "tab"), StringMatchType.Contains)
                name = "onLongClick"
                returnType = "boolean"
            }
        }.first()
    }
}
