# NexAlloyNext-Patches

Runtime bytecode patch bundles (`.nxb`) for [NexAlloyNext](https://github.com/a092devs/NexAlloyNext), the root + LSPosed dynamic patching ecosystem for Android.

>[!IMPORTANT]
> This is an independent runtime patch repository. Patches are compiled into `.nxb` bundles — signed APK containers loaded in-memory at process startup via LSPosed (API 101) and DexKit. Target APK files are **never modified, re-signed, or rebuilt**.

---

## Architecture Overview

NexAlloy patches run entirely in-process using dynamic bytecode interception:
- **No APK Modifications:** Original APKs and their signatures remain completely untouched on disk.
- **Dynamic DexKit Discovery:** Methods, fields, and obfuscated signatures are resolved at runtime via DexKit opcode and string pattern matching, providing robust version resilience.
- **Fail-Safe Isolation:** Patches execute in isolated runtime contexts with `runCatching` safeguards, preventing target app crashes when non-critical hooks fail.
- **Format:** Patches are packaged as signed `.nxb` APK containers holding compiled Dex bytecode and a small manifest contract; NexAlloyNext loads them in-memory at process startup.

---

## Bundle Structure & Contract

A `.nxb` file is a regular signed APK renamed at packaging time. NexAlloyNext reads four
`meta-data` entries from its manifest:

| `meta-data` key | Content |
|---|---|
| `nexalloy.bundle.entry` | Class implementing `io.github.nexalloy.PatchBundle` (the bundle's entry point) |
| `nexalloy.bundle.apiVersion` | Patcher API version the bundle was built against |
| `nexalloy.bundle.author` | Author shown in the NexAlloy manager |
| `nexalloy.bundle.description` | String resource describing the bundle |
| `nexalloy.bundle.compatibilitySeries` | Compatible manager compatibility series (e.g. `3.2`) |
| `nexalloy.bundle.minManagerVersion` | Minimum required manager version (e.g. `3.2.0`) |
| `nexalloy.bundle.maxManagerVersion` | Maximum supported manager version (or empty) |

The entry point lists every supported app with its package identifier and the versions the
patches were last verified against (`AppPatchInfo.targets`). The manager's compatibility engine
compares those targets against the installed app version to drive compatibility badges and
update recommendations — a bundle that declares no targets simply gets no version
recommendation.

Resource package ids are assigned per bundle (`0x02`–`0x7e`, never the app id `0x7f`) and must
be distinct for bundles loaded into the same app; NexAlloyNext refuses to install a bundle whose
resource package id collides with an already installed one. See
[bundles/README.md](bundles/README.md) for the full authoring guide.

---

## Curated Patch Bundles

### NexFusion Patches (`nexfusion-*.nxb`)
Unified runtime bytecode patches across all 15 supported applications, combining core streaming, media, productivity, privacy, and sharing customizations into a single distribution.

| Target Application | Package Identifier | Key Patch Capabilities |
|---|---|---|
| **YouTube** | `com.google.android.youtube` | Remove video/feed ads, SponsorBlock, background playback, link sanitization, custom playback speeds & quality dialog, swipe controls, layout filtering, DeArrow, Litho node bypass, Shorts resuming, Ambient mode, Miniplayer, PiP seek buttons, Disable playlist autoplay |
| **YouTube Music** | `com.google.android.apps.youtube.music` | Background playback, audio ad removal, exclusive audio playback, hide upgrade buttons |
| **Google Photos** | `com.google.android.apps.photos` | Spoof Pixel XL features (enables unlimited original-quality cloud storage), DCIM folder backup control |
| **Discord** | `com.discord` | Disable crash reporting, telemetry and touch logging, install attribution, advertising identifiers (native entry points from Venus-Patches) |
| **Facebook** | `com.facebook.katana` | Media downloader for photos, videos, Reels, and Stories with DASH and audio extraction; hide sponsored posts, reels, and stories; allow screenshots (Hushfacebook ports) |
| **Gboard** | `com.google.android.inputmethod.latin` | Dynamic Phenotype flag detection + SharedPreferences hook for rounded keycaps, AI writing tools, scan text OCR, screenshot chips, emoji palette v2, smart compose, spellchecker suggestion chips, cursor trackpad |
| **Gmail** | `com.google.android.gm` | Hide sponsored promotional ads in the inbox (NexAlloy-XES port) |
| **Google App** | `com.google.android.googlequicksearchbox` | Filter sponsored cards and ads in the Discover feed (NexAlloy-XES port) |
| **Instagram** | `com.instagram.android` | Download menu options (post, reels, story, highlight, profile picture) with save location, per-username folder and timestamp name settings; sponsored-post, reels and story ad blocking; anonymous story/live viewing; link sanitizer (HushGram & Piko ports) |
| **LinkedIn** | `com.linkedin.android` | Sanitize share links (copy/share paths), open `safety/go` links directly, block tracking |
| **MiXplorer** | `com.mixplorer` | Fix scoped-storage file sharing: hide unreadable `_data` paths from other apps on Android 11+ |
| **Proton VPN** | `ch.protonvpn.android` | Disable telemetry and observability workers, suppress upgrade prompts, unlock NetShield UI availability (Paresh-Patches port) |
| **Reddit** | `com.reddit.frontpage` | Feed and comment ad removal, start as guest, sanitize sharing links, screenshot banner, custom feed sorting, Redgifs audio unmuting |
| **Spotify** | `com.spotify.music` | Audio ad muting, hide ad views/video banners, hide Premium navigation tab, Dynamic Material You theme, custom rounded corners, tracking link cleaner |
| **X** | `com.twitter.android` | Promoted-post removal, video/GIF download unlock, outdated-screen and in-app update block, telemetry/tracker blackholing, premium upsell removal |


---

## Development

Each bundle is a Gradle module under `bundles/`, registered in `settings.gradle.kts` and built
with the version-pinned `nexalloy.bundle` plugin from the NexAlloyNext Maven repository. To build one bundle:

```bash
./gradlew :bundles:<name>:assembleBundle
```

A new bundle needs a `build.gradle.kts` declaring its entry class, author, description resource,
and resource package id; the entry class implements `io.github.nexalloy.PatchBundle` and lists
its supported apps. Patch hooks resolve target members at runtime and follow these conventions:

- **Feature detection over version checks** — locate members with DexKit fingerprints or
  `XposedHelpers.find*`, never by hardcoded versions.
- **Fail safe** — wrap risky reflection in `runCatching` so a missing method skips its own patch
  instead of crashing the target app.
- **Register once** — never hook the same member twice; respect the user's enable/disable state.
- **Honest targets** — only list versions the hooks were actually verified against.

The full guide, including the manifest contract and resource package id rules, lives in
[bundles/README.md](bundles/README.md).

---

## Building and Testing

### Prerequisites
- JDK 21 or JDK 25 LTS
- Android SDK installed (`ANDROID_HOME` or `ANDROID_SDK_ROOT` configured)

### Commands
```bash
# Run unit tests across all bundles
./gradlew testDebugUnitTest

# Assemble all .nxb release bundles
./gradlew assembleBundle
```

The compiled bundles will be generated in `bundles/<bundle-name>/build/outputs/bundle/*.nxb`.

### Testing

Bundles are covered by plain JVM unit tests plus shared fingerprint tests that run against
reference APKs dropped into each bundle's `binaries/` directory (named `<package name>…apk`,
e.g. `com.example.app.1.2.3.apk`). Without those APKs the fingerprint test parameters are empty
and only the JVM tests run. The shared test sources and DexKit native libraries live in
`bundles/testing/`.

---

## Discovery & Release

NexAlloyNext discovers bundles through **patch sources**. A source is either:

- a GitHub repository (`owner/repo` or its URL) whose **latest release** provides the bundles as
  `*.nxb` assets — pre-releases are only considered when the source opts in; or
- the direct URL of a bundle JSON file:
  ```json
  { "version": "1.2.0", "download_url": "https://…/bundle.nxb", "description": "changelog" }
  ```

The curated default source is [`a092devs/NexAlloyNext-Patches`](https://github.com/a092devs/NexAlloyNext-Patches);
further known sources can be added with one tap in the manager's Patches tab.

[`.github/workflows/android.yml`](.github/workflows/android.yml) runs unit tests and bundle
verification on every push and pull request. After a successful push to `main`, it checks the first
versioned section in `CHANGELOG.md` and dispatches
[`.github/workflows/release.yml`](.github/workflows/release.yml) when that release does not exist.
The release workflow runs the tests again, performs a clean release bundle build, verifies every
`.nxb`, and publishes the release. Hyphenated versions are published as pre-releases. Manual
dispatch is also available for controlled releases.

---

## Credits & Acknowledgments

- [2eno](https://github.com/2eno): The upstream project this repository is built upon ([2eno/NexAlloyNext-Patches](https://github.com/2eno/NexAlloyNext-Patches), whose consolidated history forms the base of this repository), plus the Spotify and Google Ads runtime hook extensions
- [NexAlloy](https://github.com/NexAlloy/NexAlloy): The original LSPosed patcher concept by a092devs
- [Morphe](https://morphe.software): Bytecode runtime hook patterns and extensions
- [ReVanced](https://revanced.app): Feature specifications and research
- [Piko](https://github.com/crimera/piko): Instagram and Twitter runtime hook logic
- [VenusIsJaded/Venus-Patches](https://github.com/VenusIsJaded/Venus-Patches): Discord native privacy target tables and "Use your phone's font" (a092devs bundle)
- [heyymichii/michii-patches](https://github.com/heyymichii/michii-patches): LinkedIn share-link, open-links and tracking patches (a092devs bundle)
- [ak800i/mixplorer-patches-for-morphe](https://github.com/ak800i/mixplorer-patches-for-morphe): MiXplorer scoped-storage file sharing fix (a092devs bundle)
- [jkennethcarino/adobo](https://github.com/jkennethcarino/adobo): Reddit screenshot-banner, link, refresh, redirect, navigation-bar and score/view-count ports (a092devs bundle)
- [SysAdminDoc/Hushfacebook](https://github.com/SysAdminDoc/Hushfacebook): Facebook media download, sponsored stories/reels filtering, and ad telemetry blocking
- [gnadgnaoh/NexAlloy-XES](https://github.com/gnadgnaoh/NexAlloy-XES): Gmail inbox ad removal and Google Discover feed ad filtering
- [morphe-patches.software](https://morphe-patches.software): Community patch catalog used for source discovery

**Attribution policy:** ports and adaptations record the upstream repository URL and keep the
original license notices. Code derived from Morphe, ReVanced, Piko, 2eno, or other GPL-3.0
projects remains GPL-3.0 (see [LICENSE](LICENSE)).
