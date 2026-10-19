package io.github.nexalloy.nexfusion.spotify

import io.github.nexalloy.nexfusion.spotify.ads.BlockPopupAds
import io.github.nexalloy.nexfusion.spotify.ads.HideAdSections
import io.github.nexalloy.nexfusion.spotify.ads.HideAdViews
import io.github.nexalloy.nexfusion.spotify.ads.HideContextMenuUpsells
import io.github.nexalloy.nexfusion.spotify.ads.HidePlaylistAds
import io.github.nexalloy.nexfusion.spotify.ads.HideVideoAds
import io.github.nexalloy.nexfusion.spotify.ads.MuteAudioAds
import io.github.nexalloy.nexfusion.spotify.layout.HidePremiumTab
import io.github.nexalloy.nexfusion.spotify.misc.FixThirdPartyLaunchersWidgets
import io.github.nexalloy.nexfusion.spotify.privacy.SanitizeSharingLinks
import io.github.nexalloy.nexfusion.spotify.theme.MaterialYouTheme
import io.github.nexalloy.nexfusion.spotify.theme.RoundedCorners

val SpotifyPatches = arrayOf(
    MuteAudioAds,
    HideAdSections,
    HideAdViews,
    HideContextMenuUpsells,
    BlockPopupAds,
    HidePlaylistAds,
    HideVideoAds,
    HidePremiumTab,
    FixThirdPartyLaunchersWidgets,
    SanitizeSharingLinks,
    MaterialYouTheme,
    RoundedCorners,
)
