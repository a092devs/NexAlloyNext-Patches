package io.github.nexalloy.nexfusion.twitter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitterPatchesTest {

    @Test
    fun `patch list keeps the upstream defaults`() {
        assertEquals(9, TwitterPatches.size)
        val byName = TwitterPatches.associateBy { it.name }
        for (name in listOf(
            "Remove Ads",
            "Unlock Video Downloads",
            "Block Update Screen",
            "Block Telemetry & Trackers",
            "Remove Premium Upsell",
        )) {
            assertEquals(true, byName.getValue(name).use, name)
        }
        for (name in listOf(
            "Control Video Auto Scroll",
            "DM Debloat",
            "Pause Search Suggestions",
            "Show Sensitive Media",
        )) {
            assertEquals(false, byName.getValue(name).use, name)
        }
    }

    @Test
    fun `tracker host matching covers every upstream endpoint`() {
        for (host in BLOCKED_TRACKER_HOSTS) {
            assertTrue(TwitterTelemetryHosts.isBlocked("https://$host/api/v3/thing?x=1"), host)
            assertTrue(TwitterTelemetryHosts.isBlocked(host.uppercase()), host)
        }
        assertFalse(TwitterTelemetryHosts.isBlocked(""))
        assertFalse(TwitterTelemetryHosts.isBlocked("https://api.x.com/1.1/statuses/home_timeline.json"))
        assertFalse(TwitterTelemetryHosts.isBlocked("https://cdn.example.com/img.png"))
    }

    @Test
    fun `download gate scopes overrides to the marked call sites`() {
        assertFalse(TwitterDownloadGate.isInsideSite())
        TwitterDownloadGate.enterSite()
        TwitterDownloadGate.enterSite()
        assertTrue(TwitterDownloadGate.isInsideSite())
        TwitterDownloadGate.exitSite()
        assertTrue(TwitterDownloadGate.isInsideSite())
        TwitterDownloadGate.exitSite()
        assertFalse(TwitterDownloadGate.isInsideSite())
        TwitterDownloadGate.exitSite()
        assertFalse(TwitterDownloadGate.isInsideSite())
    }

    @Test
    fun `promoted-item probes mirror the upstream test sites`() {
        assertEquals(
            listOf(
                "com.x.models.timelines.items.l1",
                "com.x.models.timelines.items.r1",
                "com.x.models.timelines.items.f2",
                "com.x.models.timelines.items.j1",
                "com.x.models.timelines.items.p1",
                "com.x.models.timelines.items.m0",
                "com.x.models.timelines.items.y1",
                "com.x.models.timelines.items.b2",
            ),
            PROMOTED_ITEM_PROBES.map { it.modelClass },
        )
        assertEquals(listOf("e", "j", "h", "e", "j", "a", "a", "h"), PROMOTED_ITEM_PROBES.map { it.field })
        assertEquals(listOf(null, null, null, null, null, "a", "b", null), PROMOTED_ITEM_PROBES.map { it.call })
    }
}
