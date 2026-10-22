# Security policy

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability or an accidentally
published credential. Report it privately through GitHub's security
advisory workflow, or contact the repository maintainers through the contact
method configured for the repository.

If a credential may have been exposed, revoke or rotate it first where
possible, then include only the minimum evidence needed to investigate.

## Credentials and client keys

Do not commit passwords, signing keys, access tokens, private keys, or local
machine configuration. The Google API keys used by some upstream Android
client flows are client-side keys, not server credentials; they must still be
restricted to the intended APIs and quotas in Google Cloud. Do not copy this
pattern for secrets that grant privileged backend access.

The `nexalloy.bundle` Gradle plugin is resolved from the pinned Maven
repository and version in `gradle/libs.versions.toml`. Do not replace it with
an unreviewed local build or an unpinned dynamic version in release builds.
