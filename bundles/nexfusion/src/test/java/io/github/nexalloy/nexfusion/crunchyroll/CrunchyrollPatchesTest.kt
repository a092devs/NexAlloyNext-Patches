package io.github.nexalloy.nexfusion.crunchyroll

import io.github.nexalloy.nexfusion.NexFusionPatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CrunchyrollPatchesTest {

    @Test
    fun `bundle includes all expected crunchyroll patches`() {
        assertEquals(2, CrunchyrollPatches.size)
        val names = CrunchyrollPatches.map { it.name }.toSet()
        assertTrue(names.contains("Enable Picture-in-Picture"))
        assertTrue(names.contains("Remove from Continue Watching"))
    }

    @Test
    fun `NexFusion bundle declares crunchyroll with targets and icon color`() {
        val crApp = NexFusionPatches.apps.firstOrNull { it.packageName == "com.crunchyroll.crunchyroid" }
        assertNotNull(crApp)
        assertEquals("Crunchyroll", crApp.appName)
        assertEquals(0xFF5C00, crApp.appIconColor)
        assertEquals(2, crApp.patches.size)

        val targetVersions = crApp.targets.map { it.version }.toSet()
        assertTrue(targetVersions.contains("3.126.0"), "Must support installed device version 3.126.0")
        assertTrue(targetVersions.contains("3.117.0"), "Must support 3.117.0")
    }
}

