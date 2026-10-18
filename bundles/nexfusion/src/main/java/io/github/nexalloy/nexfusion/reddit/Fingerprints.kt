package io.github.nexalloy.nexfusion.reddit

import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.fieldAccess
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.newInstance
import io.github.nexalloy.morphe.string

// Ported from jkennethcarino/adobo (GPL-3.0),
// patches/src/main/kotlin/dev/jkcarino/adobo/patches/reddit/ (Reddit 2026.38.0).
//
// Adobo resolves these matchers against the exact APK at build time and rewrites the bytecode.
// The same matchers run here as DexKit queries at patch time on the device, and the patch files
// in this package install runtime hooks with the same results.

/** Class anchor for Reddit's `Link` model, matched by its `toString()` prefix. */
internal object LinkToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("Link(id="),
    )
)

private object AccountPreferencesToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("AccountPreferences(over18="),
    )
)

private object AccountToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("Account(id="),
        string(", outboundClickTracking="),
    )
)

/** Redirect / click-tracking getters behind out.reddit.com, stubbed to open links directly. */
internal object GetAllowClickTrackingFingerprint : Fingerprint(
    classFingerprint = AccountPreferencesToStringFingerprint,
    name = "getAllowClickTracking",
    returnType = "Z",
)

internal object GetOutboundClickTrackingFingerprint : Fingerprint(
    classFingerprint = AccountToStringFingerprint,
    name = "getOutboundClickTracking",
    returnType = "Z",
)

internal object GetOutboundLinkFingerprint : Fingerprint(
    classFingerprint = LinkToStringFingerprint,
    name = "getOutboundLink",
    returnType = "L",
    parameters = listOf(),
)

/** Score / view-count getters on the `Link` model. */
internal object GetScoreFingerprint : Fingerprint(
    classFingerprint = LinkToStringFingerprint,
    name = "getScore",
    returnType = "I",
    parameters = listOf(),
)

internal object GetHideScoreFingerprint : Fingerprint(
    classFingerprint = LinkToStringFingerprint,
    name = "getHideScore",
    returnType = "Z",
    parameters = listOf(),
)

internal object GetViewCountFingerprint : Fingerprint(
    classFingerprint = LinkToStringFingerprint,
    name = "getViewCount",
    returnType = "Ljava/lang/Long;",
    parameters = listOf(),
)

/** The small dispatcher that creates and emits an `OnAutoRefreshFeed` event. */
internal object OnAutoRefreshFeedFingerprint : Fingerprint(
    name = "invoke",
    filters = listOf(
        newInstance(type = "Lcom/reddit/feeds/ui/events/OnAutoRefreshFeed;"),
        methodCall(
            definingClass = "Lcom/reddit/feeds/ui/events/OnAutoRefreshFeed;",
            name = "<init>",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            name = "onEvent",
            location = MatchAfterImmediately(),
        ),
    )
)

/**
 * The dispatcher that decides an [com.reddit.screen.AppResumeAction;] on app resume; upstream
 * returns early from it so `REDIRECT_TO_HOME` never runs. Upstream also has a fallback that
 * splices a return into an `onResume` override; a runtime hook cannot splice instructions
 * mid-method, so only the dispatcher is ported and a miss is logged instead.
 */
internal object AppResumeRedirectFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            definingClass = "Lcom/reddit/screen/AppResumeAction;",
            name = "REDIRECT_TO_HOME",
        ),
        literal(0x10008000),
    )
)

/** Feed post header cell metadata, containing the post ID and media URL. */
internal object MetadataCellToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("MetadataCellFragment(id="),
        string(", mediaPath="),
    ),
)

/** Feed video element cell, containing the linkId and Reddit default video URL. */
internal object VideoElementToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("VideoElement(linkId="),
        string(", defaultUrl="),
    ),
)

/** Video player URLs, containing defaultUrl as first argument. */
internal object VideoUrlsToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("VideoUrls(defaultUrl="),
    ),
)

/** Media3 DataSpec constructor, created for every URL the video player loads. */
internal object DataSpecToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("DataSpec["),
    ),
)

/** Domain VideoUrls model of feed videos. */
internal object DomainVideoUrlsToStringFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/VideoUrls;",
    name = "toString",
    returnType = "Ljava/lang/String;",
)


