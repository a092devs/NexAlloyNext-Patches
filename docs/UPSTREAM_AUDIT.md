# Upstream Audit Report — NexFusion Patches / NexAlloyNext

**Audit date:** 2026-10-18  
**Repositories audited:**
- Patch repo: `a092devs/NexAlloyNext-Patches` @ `10155d9` (NexFusion 3.2.0)
- Manager repo: `a092devs/NexAlloyNext` @ `e59b9e4` (3.2.0)
- Morphe upstream: `MorpheApp/morphe-patches` (submodule `morphe-patches`, branch `nexalloy`, HEAD `477fbc0f` = v1.46.0)
- HushGram reference: `SysAdminDoc/HushGram` (GPL-3.0)
- Test device: POCO F7 `fb02aeb` — manager 3.2.0 (code 30), Instagram `com.instagram.android` 451.0.0.53.80. **No bundle installed yet.**

---

## 1. Executive summary

The NexFusion → NexAlloyNext 3.2 coordinated-versioning rename is **complete** on both sides:

| Component | State |
|---|---|
| Bundle module | `bundles/nexfusion`, entry `NexFusionPatches`, id `io.github.nexalloy.patches.nexfusion`, rpkg `0x4e` |
| Bundle version | `3.2.0` / code 13 |
| Compatibility series | `3.2`, min manager `3.2.0` |
| Manager | All default app entries map to `NexFusion Patches`; `a092devs → nexfusion` migration in `BundleManager.install` |
| Bundle sources | 10 apps: Discord, Facebook, Gboard, Instagram, LinkedIn, MiXplorer, Proton VPN, Reddit, Spotify, X |
| CI | `android.yml` (push/PR → test + assembleBundle), `release.yml` (tag/dispatch → build, verify, publish `.nxb`) |
| Tests | JVM unit tests + DexKit fingerprint tests against APKs in `bundles/*/binaries/` (untracked) |
| Stale references | issue template + CHANGELOG history + DEVELOPMENT tree + docs still say `a092devs` in places (intentional historical attribution; see §4) |

Build and tests were green before this audit. Device-side validation (bundle install + on-device patch verification) is still pending — **no `.nxb` is installed on `fb02aeb`**.

---

## 2. HushGram feature audit

### Source

`SysAdminDoc/HushGram` (GPL-3.0). All nine requested features exist in local git history:

- `46d1a32` — "feat(instagram): add 9 HushGram patches and bump a092devs to 1.8.3" (579 insertions, `InstagramFeaturePatches.kt` + request filter + test changes)
- `4237101` — "feat(instagram): fix 9 feature patches with reverse-engineered runtime anchors, bump a092devs to 1.8.4, release 2.15.0" (anchor fixes: `Window`/`PhoneWindow` class loading instead of `Window::class.java`, `FeedMediaTapHandlerV2-softError` instead of `onSingleTapConfirmed`, obfuscated `A2N`/`Cuj`/`02A5`/`A2F` fields, extra `profile_header_full_name` id fallback, story mentions pill, etc.)

Both commits were **reverted** (`12a876d`, `687573d`) and never tagged/released (`2.15.0` does not exist). The working tree therefore has **none** of the 9 HushGram feature patches — they must be re-ported into `bundles/nexfusion`.

### Feature-by-feature matrix

| # | Feature | HushGram upstream ref | Status in current tree | Evidence | Verdict |
|---|---|---|---|---|---|
| 1 | Anonymous DM/story viewing (visual seen marker) | `direct_v2/visual_threads/%s/item_seen/`, `raven_media` | **Missing** — story-seen rule exists (`Rule.STORY_SEEN`) but no `DM_SEEN` rule, no `visualSeenMethod` anchor | `InstagramRequestFilter.kt`, `InstagramPatches.kt` | **Port** — needs new rule + hook |
| 2 | HDR boost toggle | `Window.setColorMode`, `Display.isHdr`, `Display.HdrCapabilities` | **Missing** | reverted `46d1a32` | **Port** |
| 3 | Full-quality story uploads | `high_quality_media_upload` pref + getter | **Missing** | reverted `46d1a32` | **Port** |
| 4 | Feed video pause controls | `FeedMediaTapHandlerV2-softError` / gesture detector | **Missing** | reverted `46d1a32` + fixed in `4237101` | **Port** (use fixed anchors) |
| 5 | Follower/following indicator | `bindFullName`, `isFollowedBy`/`isFollowing` | **Missing** | reverted `46d1a32` + fixed in `4237101` | **Port** (use fixed anchors) |
| 6 | Story mentions badge | `ReelViewerItemBinder.bindMedia`, `reel_mentions` | **Missing** | reverted `46d1a32` | **Port** |
| 7 | Exact timestamps | relative-time formatter hook | **Missing** | reverted `46d1a32` | **Port** |
| 8 | Profile-picture view/download | download menu + profile picture button already exist | **Partial** — "Download" button below expanded profile picture works via `DownloadMedia`; no dedicated "view full-resolution" feature | `DownloadPatches.kt` (profile-picture menu) | **Already implemented** (download); viewing is a UI nicety, not required |
| 9 | Instagram developer options | `debug_options_error` + home long-press | **Missing** | reverted `46d1a32` + fixed in `4237101` | **Port** |

Supporting changes needed:
- `InstagramRequestFilter`: add `Rule.DM_SEEN` for `/api/v2/media/seen/` (the existing path map already classifies `/api/v2/media/seen/` as `STORY_SEEN`; the DM visual-seen endpoint needs its own rule or the filter reused) — verify exact DM path against the pulled APK.
- Register all new patches in `InstagramPatches` array.
- NOTICE: HushGram already credited (lines 90-91). Add the specific new files if they introduce upstream code not already covered.
- Tests: extend `InstagramPatchesTest` for new rules where deterministic.

### Already implemented and working (evidence-based)

| Feature | Evidence |
|---|---|
| Hide ads / hide reels ads | `InstagramAds.kt` + `InstagramRequestFilter.Rule.ADS`/`REELS_ADS`, injector guard `LX/05gS.A02` |
| View stories anonymously | `Rule.STORY_SEEN` → `/api/v2/media/seen/` |
| View live anonymously | `Rule.LIVE_VIEWER` → `/heartbeat_and_get_viewer_count/` |
| Allow screenshots | `FLAG_SECURE` clearing on `Window`/`PhoneWindow`/`WindowManagerImpl` |
| Block screenshot detection | `ScreenshotContentObserver.onChange` + `registerScreenCaptureCallback`/`addScreenRecordingCallback` |
| Sanitize share links | `InstagramLinks.sanitizeUrl` (igsh/utm/fbclid stripping), unit tested |
| Open links externally | in-app browser redirect + `IN_APP_HOSTS` allowlist + fbclid strip |
| Disable analytics | `Rule.ANALYTICS` |
| Stop story auto-advance | `ReelViewerFragment` bridge-method hook |
| Hide typing status | `IGDirectTypingStatusService`/`is_typing_indicator_enabled` DexKit finder |
| Turn off double-tap like | `onDoubleTapMedia` string-anchored finder |
| Download media + settings dialog | `DownloadPatches.kt`/`DownloadSettings.kt` (location, custom folder, per-username, timestamp), unit tested |
| Audio download (DASH/muxer fallback) | `DashManifest.bestAudioUrl` + MediaExtractor/MediaMuxer, unit tested |

### Blocked / not ported (documented reasons in `InstagramPatches.kt` header)

- "View DMs anonymously" and "Disable typing status" — upstream needs direct-message structural targets; typing status IS implemented via DexKit finder (the header text predates that implementation; keep as-is, it works).
- "Ask before like", "Ask before refresh", "Disable video autoplay", "Remove build expired popup", "Open developer options" — real upstream implementations exist in HushGram and are in scope for this port (items 2, 3, 4, 5, 6, 7, 9 above).

---

## 3. Morphe `dev` change inventory

Morphe submodule is checked out detached at `477fbc0f` ("Merge v1.46.0"), branch `nexalloy` tracking `origin/nexalloy`. `git log main..dev` could not be resolved because `main` is not a local ref in the submodule (HEAD is detached at `477fbc0f` on `nexalloy`).

### What I could not determine (blocker)

Whether `origin/dev` contains commits beyond `nexalloy` HEAD `477fbc0f`: `origin/dev` exists as a remote ref but `main..dev` failed because `main` is unknown locally. Required next step:

```bash
cd bundles/morphe-patches
git fetch origin
git log nexalloy..origin/dev --oneline   # commits on dev not yet on nexalloy
```

### Morphe usage in this repo

Morphe code is compiled only into `bundles/nexalloy` (YouTube/YouTube Music/Reddit/Gboard/Music extensions via `build.gradle.kts` sourceSets). `bundles/nexfusion` does **not** consume Morphe extensions — its Instagram patches use DexKit directly against the pulled APK. So Morphe `dev` changes affect only the `nexalloy` bundle and are **out of scope** for the Instagram/HushGram port unless a shared finder/utility changes.

### Finding: no build-breaking Morphe drift detected

The `nexalloy` bundle's `GenerateMorpheTargetsTask` derives `MorpheTargets.java` from the checked-out submodule at build time, so target lists stay in sync with the checked-out revision. No action required unless `origin/dev` ahead-of-`nexalloy` reveals a shared utility change.

---

## 4. Stale-reference inventory (intentional vs. fixable)

| File | Reference | Kind | Action |
|---|---|---|---|
| `.github/ISSUE_TEMPLATE/bug_report.yaml:33` | `Example: NexAlloy Patches 1.0.7, a092devs Patches 1.8.0.` | example only | Update to NexFusion 3.2.0 (cosmetic) |
| `CHANGELOG.md:25+` | `a092devs 1.8.x` sections | historical release notes | Keep (historical truth) |
| `CHANGELOG.md:68-70` | `2eno` bundle merged into `a092devs` | historical | Keep |
| `DEVELOPMENT.md:12` | `a092devs/` tree line | module tree | Update to `nexfusion/` |
| `DEVELOPMENT.md:167-169` | a092devs Spotify extension paragraph | extension text | Update path/bundle name |
| `docs/installed-app-patch-audit.md` | "11 patches", "Maintained in a092devs" rows | audit matrix | Update row: 15 patches, NexFusion |
| `bundles/README.md:149-151` | `a092devs` attribution example | authoring guide | Update to NexFusion |

---

## 5. Immediate next steps (implementation plan)

Priority 1 — build-breaking / compatibility: none found.

Priority 2 — HushGram feature port (into `bundles/nexfusion/.../instagram/`):
1. Create `InstagramFeaturePatches.kt` with the 7 missing features (use the `4237101` fixed-anchor versions): HDR boosts, DM media anonymity, full-quality story uploads, tap-feed-video-to-pause, profile follows-you, story mentions, exact timestamps, developer options.
2. Add `Rule.DM_SEEN` to `InstagramRequestFilter` (verify DM endpoint path on the pulled 451 APK via DexKit).
3. Register all new patches in `InstagramPatches` array.
4. Extend `InstagramPatchesTest` for new rules.
5. Verify anchors against the pulled `instagram-451.apk` with a local `FingerprintsKtTest` run (DexKit string/number matchers).
6. Update NOTICE if any new upstream file is introduced (HushGram already credited).

Priority 3 — docs: `docs/installed-app-patch-audit.md` Instagram row, `DEVELOPMENT.md` tree/paragraph, issue template example, `bundles/README.md` attribution example.

Priority 4 — build + test: `./gradlew :bundles:nexfusion:testDebugUnitTest :bundles:nexfusion:assembleBundle`.

Priority 5 — device: install `nexfusion-3.2.0.nxb` on `fb02aeb`, apply Instagram patches, spot-check ad hiding + download settings + one HushGram feature.

Priority 6 — release: bump NexFusion to `3.2.1` (patch-only, manager unchanged), add CHANGELOG `3.2.1` section, tag + push; manager stays `3.2.0`.

---

## 6. Versioning decision

No manager change is required for the HushGram port (it is bundle-only code). Release **NexFusion `3.2.1`** under series `3.2`, min manager `3.2.0` — patch-only, compatible with installed manager 3.2.0 on `fb02aeb`.
