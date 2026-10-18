# Bundle Development Guide

A NexAlloy patch bundle (`*.nxb`) is a **signed APK** produced by the `nexalloy.bundle` Gradle
plugin. NexAlloyNext loads the bundle's code and resources into the target app's process at
startup through LSPosed; the target APK on disk is **never modified, rebuilt, or re-signed**.

## Bundle manifest contract

Every bundle declares four `meta-data` entries in its `AndroidManifest.xml`. The plugin fills
them in from the `nexalloyBundle { … }` block:

| `meta-data` key | Filled from | Purpose |
|---|---|---|
| `nexalloy.bundle.entry` | `entry` | Fully-qualified class implementing `io.github.nexalloy.PatchBundle` |
| `nexalloy.bundle.apiVersion` | `patcherApiVersion` (default `1`) | Patcher API version the bundle was built against |
| `nexalloy.bundle.author` | `author` | Author shown in the NexAlloy manager |
| `nexalloy.bundle.description` | `descriptionResource` | String resource describing the bundle |
| `nexalloy.bundle.compatibilitySeries` | `compatibilitySeries` | Compatible manager compatibility series (e.g. `3.2`) |
| `nexalloy.bundle.minManagerVersion` | `minManagerVersion` | Minimum required manager version (e.g. `3.2.0`) |
| `nexalloy.bundle.maxManagerVersion` | `maxManagerVersion` | Maximum supported manager version |


## Creating a bundle

1. Create `bundles/<name>/build.gradle.kts`:

```kotlin
plugins {
    id("nexalloy.bundle")
}

android {
    namespace = "io.github.nexalloy.patches.example"
    defaultConfig {
        applicationId = "io.github.nexalloy.patches.example"
        versionCode = 1
        versionName = "1.0.0"
    }
}

nexalloyBundle {
    entry = "io.github.nexalloy.example.ExamplePatches"
    author = "Example"
    descriptionResource = "bundle_description"
    resourcePackageId = 0x51
}

dependencies {
    compileOnly(libs.nexalloy.patcher)
}
```

2. Register the module in `settings.gradle.kts`:

```kotlin
include(":bundles:example")
```

3. Add an entry class implementing `PatchBundle`. `targets` are **the versions the patches
were last verified against**; the manager's compatibility engine compares the installed version
against them (an empty list means "no version recommendation"):

```kotlin
object ExamplePatches : PatchBundle {
    override val apps = listOf(
        AppPatchInfo(
            "Example App", "com.example.app", ExampleAppPatches,
            targets = listOf(AppTarget("1.2.3")),
            appIconColor = 0x3B82F6,
        ),
    )
}
```

### Resource package ids

`resourcePackageId` must be between `0x02` and `0x7e`, distinct from every other bundle that is
loaded into the same app, and must never use the app id `0x7f`. NexAlloyNext refuses to install
a bundle whose resource package id collides with an already installed bundle.

## Hook conventions

Patches are declared with the `patch { … }` DSL from the patcher API and resolve target members
at runtime, so no build-time knowledge of the target APK is required:

```kotlin
val HideExampleBanner = patch(
    name = "Hide example banner",
    description = "Removes the example banner from the home screen.",
) {
    runCatching {
        // XposedHelpers / patcher API calls that install the hook
    }.onFailure {
        PatcherLog.info { "example banner hook skipped: $it" }
    }
}
```

Conventions used across this repository:

- **Feature detection over version checks.** Locate classes and methods by structure (DexKit
  fingerprints or `XposedHelpers.find*`), never by hardcoded version strings.
- **Fail safe.** Anything that may throw is wrapped in `runCatching`; a missing method logs and
  skips only its own patch instead of crashing the target app.
- **Respect the toggle and register once.** A patch body runs only when the user enabled it in
  the manager, and no member may be hooked twice.
- **Honest compatibility.** Only list a `target` version after verifying the hooks against that
  version; never advertise functionality the hooks do not install.
- **No APK-level work.** No resource edits, no manifest rewrites, no code written to disk.

The `nexalloy.bundle` plugin disables minification and excludes the Kotlin stdlib and AndroidX
annotations from packaging: those classes are provided by NexAlloyNext at runtime, so the bundle
must not carry its own copies.

## Building

```bash
# All bundles
./gradlew assembleBundle

# A single bundle
./gradlew :bundles:example:assembleBundle
```

The plugin writes `build/outputs/bundle/<module>-<version>.nxb` (release builds are
debug-signed; bundles need not be signed by anyone in particular, but a valid APK is required).

## Testing

```bash
./gradlew testDebugUnitTest
```

Bundles use two kinds of tests:

- **Plain JVM unit tests** for the independent logic of a patch (configuration parsing, URL
  sanitizers, state machines), using `kotlin("test-junit5")`.
- **Fingerprint tests.** The shared sources in `bundles/testing/java` run against reference APKs
  placed in the bundle's `binaries/` directory. Name each file `<package name>…apk`
  (for example `com.example.app.1.2.3.apk`); the test arguments provider discovers them
  automatically. DexKit is loaded from the shared native libraries in `bundles/testing/jniLibs`,
  so the same tests run on Windows, macOS, and Linux. The `binaries/` directories are untracked —
  without APKs, the fingerprint tests simply have no parameters to run against.

## Attribution

Bundles that port or adapt upstream code must record the upstream repository and keep its
license notices. Code derived from Morphe, ReVanced, Piko, or other GPL-3.0 projects remains
GPL-3.0 and is credited in the repository README. A worked example is the `nexfusion` bundle,
which lists every ported source, its license and the ported files in
`bundles/nexfusion/src/main/java/io/github/nexalloy/nexfusion/NOTICE` and documents the
cross-reference in `docs/installed-app-patch-audit.md` (Section 4).
