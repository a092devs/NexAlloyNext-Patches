package io.github.nexalloy.morphe.youtube.misc.verticalscroll

import app.morphe.extension.shared.Logger
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_18_or_greater
import io.github.nexalloy.patch

val FixVerticalScroll = patch(
    description = "Fixes issues with refreshing the feed when the first component is of type EmptyComponent."
) {
    dependsOn(VersionCheck)

    if (is_21_18_or_greater) {
        // Can cause issues with scrolling.
        insertLiteralOverride(45782902L)
    }

    try {
        ::canScrollVerticallyFingerprint.hookMethod(returnConstant(false))
    } catch (e: Throwable) {
        Logger.printException({ "canScrollVerticallyFingerprint hook failed" }, e)
    }
}
