package io.github.nexalloy.morphe.youtube.interaction.seekbar

import io.github.nexalloy.patch

@Suppress("unused")
val seekbarPatch = patch(
    name = "Seekbar",
    description = "Adds options to disable precise seeking when swiping up on the seekbar, " +
            "slide to seek instead of playing at 2x speed when pressing and holding, " +
            "and tapping the player seekbar to seek."
) {
    dependsOn(
        disablePreciseSeekingGesturePatch,
        enableSlideToSeekPatch,
        enableTapToSeekPatch,
    )
}
