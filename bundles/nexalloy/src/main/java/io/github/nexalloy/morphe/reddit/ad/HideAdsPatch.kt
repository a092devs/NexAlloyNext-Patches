package io.github.nexalloy.morphe.reddit.ad

import app.morphe.extension.shared.Logger
import io.github.nexalloy.getObjectField
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.reddit.misc.version.VersionCheck
import io.github.nexalloy.morphe.reddit.misc.version.is_2026_04_0_or_greater
import io.github.nexalloy.morphe.reddit.misc.version.is_2026_16_0_or_greater
import io.github.nexalloy.patch
import io.github.nexalloy.setObjectField

val HideAds = patch(
    name = "Hide ads",
    description = "Adds options to hide ads."
) {
    dependsOn(VersionCheck)

    // region Filter promoted ads (does not work in popular or latest feed)

    fun hideOldAds(fingerprint: Fingerprint) {
        fingerprint.hookMethod {
            val iLink = runCatching { classLoader.loadClass("com.reddit.domain.model.ILink") }.getOrNull() ?: return@hookMethod
            val getPromoted = iLink.methods.singleOrNull { it.name == "getPromoted" } ?: return@hookMethod
            after { param ->
                runCatching {
                    val rawChildren = runCatching { param.thisObject.getObjectField("children") }.getOrNull()
                    val arrayList = (rawChildren as? Iterable<*>) ?: return@after
                    val result = mutableListOf<Any?>()
                    var filtered = 0
                    for (item in arrayList) {
                        try {
                            if (item != null && iLink.isAssignableFrom(item.javaClass) && getPromoted(item) == true) {
                                filtered++
                                continue
                            }
                        } catch (_: Throwable) {
                            Logger.printDebug { "not iLink, keep it" }
                            // not iLink, keep it
                        }
                        result.add(item)
                    }
                    if (filtered > 0) {
                        Logger.printDebug { "Filtered $filtered ads in ${arrayList.count()} posts" }
                        param.thisObject.setObjectField("children", result)
                    }
                }.onFailure { e ->
                    Logger.printDebug { "hideOldAds error: $e" }
                }
            }
        }
    }

    hideOldAds(ListingFingerprint)

    if (!is_2026_16_0_or_greater) {
        hideOldAds(SubmittedListingFingerprint)
    }

    AdPostSectionConstructorFingerprint.hookMethod {
        val immutableListBuilder = ::ImmutableListBuilderReference.method
        before {
            Logger.printDebug { "Emptied AdPostSection" }
            it.args[4] = immutableListBuilder(null, arrayListOf<Any>())
        }
    }

    // endregion

    // region Filter comment ads

    CommentsViewModelAdLoaderFingerprint.hookMethod {
        before {
            it.result = Unit
        }
    }

    // As of Reddit 2026.04+, placeholders are not hidden unless 'adsLoadCompleted' is false.
    // Hide placeholders by overriding 'adsLoadCompleted' to true.
    if (is_2026_04_0_or_greater) {
        CommentsAdStateConstructorFingerprint.hookMethod {
            val adsLoadCompletedField = ::adsLoadCompletedField.field
            after {
                adsLoadCompletedField.set(it.thisObject, true)
            }
        }
    }

    // endregion
}