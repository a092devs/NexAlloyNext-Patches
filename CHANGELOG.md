# Changelog

All notable changes to the NexAlloyNext patch bundles are documented here. Releases are dispatched
automatically on every push to `main` for the version of the first `X.Y.Z` section below when that
version has no release yet. The section matching the release tag becomes the release notes,
falling back to generated commit notes when no section exists.

## 3.2.0

- `NexFusion` 3.2.0:
  * Renamed the custom `a092devs` bundle to `NexFusion Patches` (`nexfusion.nxb`, `io.github.nexalloy.patches.nexfusion`).
  * Initiated coordinated versioning series `3.2` matching NexAlloyNext manager `3.2.0`.
  * Added compatibility series (`3.2`) and minimum manager version (`3.2.0`) metadata for semantic compatibility validation in the manager app.
  * Preserved full support for all existing patch collections and ported features:
    - Instagram (`com.instagram.android` 451.0.0.53.80, 450.0.0.46.79, 447.0.0.55.81): ad & suggestion blocking, ad injector guard, in-app download dialog with custom folders, per-username subfolders, timestamp filenames, DASH audio extraction, ghost story/live viewing, story auto-advance hold, typing status suppression, double-tap like suppression.
    - Spotify (`com.spotify.music`): audio ad muting, ad view & upsell banner suppression, Premium tab hiding, launcher widgets fix, link sanitizer, Material You theme, custom rounded corners.
    - Reddit (`com.reddit.frontpage`): screenshot banner disable, direct links, home-feed auto-refresh disable, transparent navigation bar, upvote score hiding, post view count hiding.
    - Discord (`com.discord` 348.10): crash reporting, telemetry and touch logging, install attribution, advertising identifiers blocking, and system typeface font forcing.
    - X / Twitter (`com.twitter.android` 12.35.0-prod.01): promoted post removal, video downloads unlock, update screen block, telemetry blackholing, premium upsell removal.
    - Gboard, LinkedIn, MiXplorer, and Proton VPN ports maintained with full functionality.

## 2.13.0


- `a092devs` 1.8.2:
  * Instagram (`com.instagram.android` 451.0.0.53.80): added custom download folder configuration,
    allowing users to set any custom base folder path (or reset to `NexAlloyNext/Instagram`).
  * Instagram: redesigned the Download Settings dialog into a smooth, in-place update interface,
    eliminating the flickering dismiss-and-reopen behavior on option toggles.
  * Instagram: added new patches ported from HushGram:
    - `Stop story auto-advance`: keeps stories on screen without advancing automatically until tapped or swiped.
    - `Hide typing status`: suppresses typing indicator packets so other users do not see when you are typing in chats.
    - `Turn off double tap to like`: stops double tapping posts, reels, and comments from liking them, preventing accidental likes while scrolling.
  * Instagram: expanded suggested content filter rules to intercept additional recommendation endpoints.

## 2.12.0

- `a092devs` 1.8.1:
  * Instagram (`com.instagram.android` 451.0.0.53.80): added "Download audio" option to the download
    dialog for reels and videos, saving the audio track as an `.m4a` file directly into
    `Download/NexAlloyNext/Instagram` using DASH manifest stream extraction with native media muxer fallback.
  * Instagram: default download folder path under Downloads is now `Download/NexAlloyNext/Instagram`
    on device storage (with optional poster subfolder when per-username folders are enabled).

## 2.11.0

- `a092devs` 1.8.0:
  * Instagram (`com.instagram.android` 451.0.0.53.80): `Hide ads` and `Hide reels ads` now stop the
    ads that kept showing between reels and in stories. Instagram builds some sponsored items itself
    and inserts them into the feed, where the request filter can never see them; both patches now
    guard that insert (the "ad injector" Piko answers false: the method holding both
    "cross_surface_duplicate_ad" and "Is ad pod", `LX/05gS.A02` on 451) and it reports that nothing
    went in, so no gap is left behind.
  * Instagram: `Hide reels ads` previously blocked nothing, as `/feed/injected_reels_media/` was
    matched by the ADS rule which the patch never enables. The endpoint now matches the REELS_ADS
    rule, `Hide ads` enables both rules, and the filter test pins the mapping.
  * Instagram: the download options have settings now, reachable from the new "Download settings" row
    of the download dialog: the save location (Download, or Pictures and Movies for the gallery), a
    folder per username, and timestamp names (`yyyyMMdd_HHmmss`). They live in the app's own
    preferences and are read once at the start of a download, so nothing polls and nothing reaches
    back to the manager. The poster's username comes from the media's `user` and the user's
    `username` getters pinned by the hash of each key (`Media.A3R` and `User.A8D` on 451); a build
    that renames them falls back to the plain folder and downloads keep working.
  * Spotify (`com.spotify.music`): new app entry carrying the Spotify patches merged from the `2eno`
    bundle — ad muting and hiding (audio, sections, views, player video, playlist, popups and
    context-menu upsells), Premium tab hiding, third-party launcher widgets fix, sharing link
    sanitizer, Dynamic Material You theme and custom rounded corners.
- The `2eno` bundle is merged into `a092devs` and is not published anymore; its extension code is
  vendored in-tree (the `2eno-patches` submodule and its Dependabot updates are gone). Attribution
  is retained in the `a092devs` NOTICE.

## 2.10.0

- The `piko` bundle is removed: it carried only the X hooks for `12.19.1-release.0`, which no longer
  match the maintained X builds, and its Instagram side had already moved to `a092devs` in 2.6.0.
  X coverage is superseded by the kveld9 port in `a092devs` 1.7.0 (`12.35.0-prod.01`). The Piko
  NOTICE is retained verbatim as
  `bundles/a092devs/src/main/java/io/github/nexalloy/a092devs/NOTICE-piko` (the `a092devs` NOTICE
  now points there), so the upstream attribution requirement survives the removal.
- Docs updated for the removal: the README bundle list, the DEVELOPMENT module tree and the
  installed-app audit now credit `a092devs` for Instagram and X instead of the removed bundle.

## 2.9.0

- `a092devs` 1.7.0:
  * X (`com.twitter.android` 12.35.0-prod.01): new app entry with nine runtime ports of
    `kveld9/kveld-extra-morphe-patches` — `Remove Ads`, `Unlock Video Downloads`,
    `Block Update Screen`, `Block Telemetry & Trackers`, `Remove Premium Upsell`,
    `Control Video Auto Scroll`, `DM Debloat`, `Pause Search Suggestions` and
    `Show Sensitive Media`. Per-patch defaults mirror upstream (first five enabled, last four off).
    The upstream manifest and resource edits (permission strips, component disables, Sentry metadata
    removal) have no runtime equivalent and are not ported.
  * NOTICE: adds the `kveld9/kveld-extra-morphe-patches` (GPL-3.0) section covering the X port.

## 2.8.1

- `a092devs` 1.6.1:
  * JioHotstar (`in.startv.hotstar`): the 2.8.0 runtime port has been withdrawn — the patches did not take effect when applied on the device, so the whole port is removed from the bundle: the patch and fingerprint sources, the tests, the app entry and the `durgesh0505/chiggi_morphe_patches` NOTICE section. The 2.8.0 release notes keep the record of what was attempted.

## 2.8.0

- `a092devs` 1.6.0:
  * Facebook (`com.facebook.katana`): the feed funnel now survives the 583 wrapper/body split. The finder pins the two kept parameter types instead of the renamed third one, and follows the wrapper to the static body it delegates to, so "Hide sponsored posts" no longer fails at every app start with 583 installed — which was the source of the recurring "Error while applying the following patches" toast. The hook reads the edge from whichever argument holds it.
  * JioHotstar (`in.startv.hotstar`): new runtime ports of `durgesh0505/chiggi_morphe_patches` for the phone build (26.09.28.2, keeping the upstream TV build 26.06.22.3 as a target) — premium UI unlock (subscribed state, download buttons, playback nudge suppression), video ad removal (SSAI tag cleared in the AdMetadata constructor so the player requests a clean stream, plus the home-feed banner render stopped), screenshot restriction removal, all-codecs/4K checks and the Dolby Vision/HDR capability check forced on, and the device-attestation blob forced to report a clean device (anchored by its 34-String constructor, which survives the class renames; the blob's own flags were verified against the phone build's parameter names). Every finder carries upstream's access flags and was verified against all thirteen dex entries of the phone build. Not ported, with reasons in the bundle sources: Firebase analytics flags (read before runtime hooks exist), the proxy/VPN verdict bypass (renamed lambda anchors), the device-id spoof and the security-state self-report (renamed anchors), and manifest/resource edits.
  * NOTICE: adds the `durgesh0505/chiggi_morphe_patches` entry (GPL-3.0, adapting Paresh-Maheshwari's patches).

## 2.7.0

- `a092devs` 1.5.0:
  * Facebook (`com.facebook.katana`): the feed funnel, the Reels and Watch ad-pool vends, the ad fetches and the telemetry senders are now resolved at runtime with DexKit instead of hooking names that do not exist in release builds. Sponsored reels also cancel the ad-break queries with a failed future, block the anchorable client-side splices, and stop the deferred-card ad-break poller.
  * Instagram (`com.instagram.android`): the patches now ride on the Tigon request filter and real framework anchors; the direct-message stubs, the log-only patches and the old download stub are gone. The open-links-externally patch anchors on Instagram's own in-app browser, keeps Instagram-family pages and ad links in the app, follows redirect shims and strips only the click id. The download options of the post, sheet, story, highlight and profile-picture menus are restored from this repository's Piko bundle and registered.
  * NOTICE: adds the piko entry (GPL-3.0, NOTICE retained) and corrects the Hushfacebook and HushGram entries.
- `2eno`: repaired the Spotify hide-ad-views patch against 9.1.90.2270, which previously hooked names absent from the release build and silently did nothing.

## 2.6.0

- `a092devs` 1.4.0:
  * Integrated comprehensive Instagram (`com.instagram.android`) patch suite ported from `SysAdminDoc/HushGram`, targeting `451.0.0.53.80`, `450.0.0.46.79`, and `447.0.0.55.81`:
    - Hide ads: suppresses sponsored posts, feed ad units (`INTENT_AWARE_AD_PIVOT`, `STAND_ALONE_MULTI_AD_PIVOT`), injected reels, and profile ads.
    - Hide suggested posts & reels: filters explore chaining, suggestions in feed, and recommended accounts.
    - Ghost mode: anonymous story viewing (blocks `media/seen/` receipts), anonymous live watching (blocks heartbeat presence pings), anonymous DMs (blocks seen/read receipts), and disables typing indicators.
    - Privacy: removes `FLAG_SECURE` for screenshots and screen recordings in disappearing chats, blocks screenshot detection callbacks and observers, sanitizes tracking tags (`igsh`, `utm_*`, `fbclid`) from share URLs, bypasses in-app browser to open links externally, and disables analytics/telemetry.
    - Download any media without watermarks, ask before like, ask before refresh, and unlocked developer options.
  * Expanded Facebook (`com.facebook.katana`) patches ported from `SysAdminDoc/Hushfacebook` targeting `582.0.0.50.54` and `581.0.0.45.54`:
    - Hide sponsored posts, feed ads, comment sheet ad pills, sponsored reels, and marketplace ads.
    - Block ad telemetry, background ad prefetch jobs, and advertising ID tracking sinks.
    - Bypass in-app browser to open links in default system browser, and download media.
- `piko`:
  * Migrated Instagram patches into `a092devs.nxb` to focus solely on X (Twitter).
- Repository maintenance:
  * Removed untracked/redundant `reports/` directory from commit tree and updated `.gitignore`.

## 2.5.0

- `a092devs` 1.4.0:
  * Expanded Proton VPN (`ch.protonvpn.android`) runtime patches ported from `hxreborn/morphe-patches` and `hoo-dles/morphe-patches`:
    - Remove server change delay: eliminates cooldown wait periods between server switching on free plans (`AppConfigResponse`).
    - Unlock advanced settings & NetShield: unlocks NetShield ad/tracker blocking, split tunneling (app & IP routing), custom DNS, and allow LAN connections.
    - Suppress upgrade promotions: eliminates onboarding upgrade dialogs, promotional alerts, and account upgrade banners.
    - Updated verified compatibility targets to include `5.20.74.0`, `5.20.57.0`, and `5.20.39.0` alongside `5.19.x`.

## 2.4.0

- `nexalloy` 1.0.7: ported four runtime player and layout patches from Morphe/yollAmeF:
  * Ambient Mode: options to bypass power-saving restrictions and disable Ambient mode dimming in fullscreen.
  * Miniplayer: modern miniplayer flag overrides, horizontal drag support, and offscreen repositioning hooks.
  * Picture-in-picture seek buttons: rewind and forward (±5s) controls injected directly into Android Picture-in-Picture window actions.
  * Disable playlist autoplay: suppresses automatic queue advancement to the next playlist video.
- `a092devs` 1.3.0:
  * Proton VPN (`ch.protonvpn.android`): runtime patches ported from Paresh-Patches to disable telemetry/observability workers, suppress upgrade onboarding dialogs, and unlock NetShield UI feature availability.
  * Gboard (`com.google.android.inputmethod.latin`): expanded with dynamic Phenotype flag class detection and SharedPreferences hook for Belka rounded keycaps, Gemini AI writing tools, Scan Text OCR, floating screenshot chips, Emoji Palette V2, Smart Compose predictions, and spellchecker suggestion chips from PixelBoard.
- `2eno`: Spotify runtime updates for APS banner handling, upsell suppression, and fingerprint stability.

## 2.3.0

- `nexalloy`: version-gated fingerprint updates for YouTube 21.41 (background playback settings,
  player type target and the shorts-resume constructor split).
- `a092devs`: Discord "Use your phone's font" option (ReactFontManager system-typeface hook ported
  from Venus Patches; off by default) and seven Reddit runtime ports from Adobo — screenshot-banner
  disable, external links without `out.reddit.com`, home-feed auto-refresh disable, home-screen
  redirect disable, transparent navigation bar, upvote-score hiding and post view-count hiding.
  The Reddit matchers were verified against 2026.41.0 fingerprints; score/view-count/nav-bar and
  auto-refresh ports are opt-in, and the score/view-count ports stub getters only (upstream also
  rewrites toString fields, which runtime hooks cannot).
- `a092devs` 1.2.0: three new app tables ported as runtime hooks. Gboard 18.4.1 gets 13 flag
  patches (cursor trackpad, quick insert, emoji size, access points menu redesign, Bluetooth
  microphone, close proactive suggestions, access point count, clipboard character limit, flow
  mode, advanced voice typing, grammar checker, inline suggestions, key shapes) from
  jasonwu1994/Gboard-patches; all of them share one flag-getter hook, and the access point count
  and advanced voice patches are off by default. Facebook 582 gets allow-screenshots and
  block-screenshot-detection from SysAdminDoc/Hushfacebook. LinkedIn gets hide sponsored
  messages, ghost mode typing indicator and read receipts, disable double-tap like and hide feed
  ads from heyymichii/michii-patches. The parts that need patch-time resources are not ported
  (Gboard OCR plumbing and AI writing tools, LinkedIn DownloadMedia, the Facebook feed patches);
  each bundle's NOTICE lists the ported material per source.
- Repository tooling: release notes now come from this changelog, and a push to `main` dispatches
  the release for the first version section automatically.

## 2.1.0 and earlier

See the GitHub Releases page for earlier release notes.
