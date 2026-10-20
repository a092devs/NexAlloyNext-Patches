package io.github.nexalloy.patches

import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.AppTarget
import io.github.nexalloy.Patch
import io.github.nexalloy.PatchBundle
import io.github.nexalloy.morphe.music.YTMusicPatches
import io.github.nexalloy.morphe.youtube.YouTubePatches
import io.github.nexalloy.revanced.googlephotos.GooglePhotosPatches

/** Entry point of the NexAlloy Patches bundle, named in its manifest. */
object NexAlloyPatches : PatchBundle {
    private fun app(
        appName: String,
        packageName: String,
        patches: Array<Patch>,
        targets: List<AppTarget> = emptyList(),
        appIconColor: Int? = null,
    ) = AppPatchInfo(appName, packageName, arrayOf(ExtensionContextHook, *patches), targets, appIconColor)

    override val apps = listOf(
        app("YouTube", "com.google.android.youtube", YouTubePatches, MorpheTargets.YOUTUBE, 0xFF0033),
        app("YT Music", "com.google.android.apps.youtube.music", YTMusicPatches, MorpheTargets.MUSIC, 0xFF0000),
        app("Google Photos", "com.google.android.apps.photos", GooglePhotosPatches, appIconColor = 0x4285F4),
    )
}
