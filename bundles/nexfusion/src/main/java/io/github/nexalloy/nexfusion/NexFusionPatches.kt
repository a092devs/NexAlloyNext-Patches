package io.github.nexalloy.nexfusion

import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.AppTarget
import io.github.nexalloy.Patch
import io.github.nexalloy.PatchBundle
import io.github.nexalloy.morphe.music.YTMusicPatches
import io.github.nexalloy.morphe.youtube.YouTubePatches
import io.github.nexalloy.patches.ExtensionContextHook
import io.github.nexalloy.patches.MorpheTargets
import io.github.nexalloy.revanced.googlephotos.GooglePhotosPatches
import io.github.nexalloy.nexfusion.discord.DiscordPatches
import io.github.nexalloy.nexfusion.facebook.FacebookPatches
import io.github.nexalloy.nexfusion.gboard.GboardPatches
import io.github.nexalloy.nexfusion.gmail.GmailPatches
import io.github.nexalloy.nexfusion.google.GoogleDiscoverPatches
import io.github.nexalloy.nexfusion.instagram.InstagramPatches
import io.github.nexalloy.nexfusion.linkedin.LinkedInPatches
import io.github.nexalloy.nexfusion.mixplorer.MixplorerPatches
import io.github.nexalloy.nexfusion.protonvpn.ProtonVpnPatches
import io.github.nexalloy.nexfusion.reddit.RedditPatches
import io.github.nexalloy.nexfusion.spotify.SpotifyPatches
import io.github.nexalloy.nexfusion.telegram.TelegramPatches
import io.github.nexalloy.nexfusion.twitter.TwitterPatches

/** Entry point of the NexFusion Patches bundle, named in its manifest. */
object NexFusionPatches : PatchBundle {
    private fun morpheApp(
        appName: String,
        packageName: String,
        patches: Array<Patch>,
        targets: List<AppTarget> = emptyList(),
        appIconColor: Int? = null,
    ) = AppPatchInfo(appName, packageName, arrayOf(ExtensionContextHook, *patches), targets, appIconColor)

    override val apps = listOf(
        morpheApp("YouTube", "com.google.android.youtube", YouTubePatches, MorpheTargets.YOUTUBE, 0xFF0033),
        morpheApp("YT Music", "com.google.android.apps.youtube.music", YTMusicPatches, MorpheTargets.MUSIC, 0xFF0000),
        morpheApp("Google Photos", "com.google.android.apps.photos", GooglePhotosPatches, appIconColor = 0x4285F4),
        AppPatchInfo(
            appName = "Discord",
            packageName = "com.discord",
            patches = DiscordPatches,
            targets = listOf(
                AppTarget("350.8 - Stable"),
                AppTarget("350.8"),
                AppTarget("349.9 - Stable"),
                AppTarget("349.9"),
                AppTarget("348.10"),
            ),
            appIconColor = 0x5865F2,
        ),
        AppPatchInfo(
            appName = "Facebook",
            packageName = "com.facebook.katana",
            patches = FacebookPatches,
            targets = listOf(
                AppTarget("584.0.0.51.56"),
                AppTarget("583.0.0.39.55"),
                AppTarget("582.0.0.50.54"),
                AppTarget("581.0.0.45.54"),
            ),
            appIconColor = 0x0866FF,
        ),
        AppPatchInfo(
            appName = "Instagram",
            packageName = "com.instagram.android",
            patches = InstagramPatches,
            targets = listOf(
                AppTarget("452.1.0.53.64"),
                AppTarget("452.0.0.48.64"),
                AppTarget("451.0.0.53.80"),
                AppTarget("450.0.0.46.79"),
                AppTarget("447.0.0.55.81"),
            ),
            appIconColor = 0xE1306C,
        ),
        AppPatchInfo(
            appName = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            patches = GboardPatches,
            targets = listOf(
                AppTarget("18.5.4.993608717-release-arm64-v8a"),
                AppTarget("18.5.3.993608717-release-arm64-v8a"),
                AppTarget("18.4.1.985164140-release-arm64-v8a"),
                AppTarget("18.4.1.985164140-beta-arm64-v8a"),
                AppTarget("18.3.1.977415014-release-arm64-v8a"),
            ),
            appIconColor = 0x4285F4,
        ),
        AppPatchInfo(
            appName = "Gmail",
            packageName = "com.google.android.gm",
            patches = GmailPatches,
            appIconColor = 0xEA4335,
        ),
        AppPatchInfo(
            appName = "Google (Discover)",
            packageName = "com.google.android.googlequicksearchbox",
            patches = GoogleDiscoverPatches,
            appIconColor = 0x4285F4,
        ),
        AppPatchInfo(
            appName = "LinkedIn",
            packageName = "com.linkedin.android",
            patches = LinkedInPatches,
            targets = listOf(
                AppTarget("4.1.1264"),
                AppTarget("4.1.1263"),
                AppTarget("4.1.1261"),
                AppTarget("4.1.1259"),
                AppTarget("4.1.1255.1"),
            ),
            appIconColor = 0x0A66C2,
        ),
        AppPatchInfo(
            appName = "MiXplorer",
            packageName = "com.mixplorer",
            patches = MixplorerPatches,
        ),
        AppPatchInfo(
            appName = "Proton VPN",
            packageName = "ch.protonvpn.android",
            patches = ProtonVpnPatches,
            targets = listOf(
                AppTarget("5.20.98.0"),
                AppTarget("5.20.74.0"),
                AppTarget("5.20.57.0"),
                AppTarget("5.20.39.0"),
                AppTarget("5.19.72.0"),
                AppTarget("5.19.43.0"),
            ),
            appIconColor = 0x6D4AFF,
        ),
        AppPatchInfo(
            appName = "Reddit",
            packageName = "com.reddit.frontpage",
            patches = RedditPatches,
            targets = listOf(
                AppTarget("2026.42.0"),
                AppTarget("2026.41.0"),
                AppTarget("2026.38.0"),
                AppTarget("2026.37.0"),
                AppTarget("2026.36.0"),
                AppTarget("2026.24.0"),
                AppTarget("2026.14.0"),
            ),
            appIconColor = 0xFF4500,
        ),
        AppPatchInfo(
            appName = "Spotify",
            packageName = "com.spotify.music",
            patches = SpotifyPatches,
            appIconColor = 0x1ED760,
        ),
        AppPatchInfo(
            appName = "X",
            packageName = "com.twitter.android",
            patches = TwitterPatches,
            targets = listOf(
                AppTarget("12.37.2-prod.01"),
                AppTarget("12.36.1-prod.01"),
                AppTarget("12.35.0-prod.01"),
            ),
            appIconColor = 0x000000,
        ),
        AppPatchInfo(
            appName = "Telegram",
            packageName = "org.telegram.messenger",
            patches = TelegramPatches,
            targets = listOf(
                AppTarget("13.1.0"),
                AppTarget("12.10.6"),
            ),
            appIconColor = 0x2AABEE,
        ),
        AppPatchInfo(
            appName = "Telegram (Web)",
            packageName = "org.telegram.messenger.web",
            patches = TelegramPatches,
            targets = listOf(
                AppTarget("13.1.0"),
                AppTarget("12.10.6"),
            ),
            appIconColor = 0x2AABEE,
        ),
    )
}
