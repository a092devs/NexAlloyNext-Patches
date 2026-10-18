package io.github.nexalloy.morphe.youtube.misc.playertype

import android.annotation.SuppressLint
import android.view.View
import app.morphe.extension.youtube.patches.PlayerTypeHookPatch
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_41_or_greater
import io.github.nexalloy.patch

@SuppressLint("NonUniqueDexKitData")
val PlayerTypeHook = patch(
    description = "Hook to get the current player type and video playback state.",
) {
    dependsOn(VersionCheck)

    ::videoStateFingerprint.hookMethod {
        val field = ::videoStateParameterField.field
        before { param ->
            PlayerTypeHookPatch.setVideoState(field.get(param.args[0]) as Enum<*>)
        }
    }
}