package io.github.nexalloy.patches

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class InstalledAppAuditTest {

    @Test
    fun `verify installed app patch audit matrix completeness and schema`() {
        // Locate audit file in docs/ or reports/
        val projectRoot = File(".").canonicalFile.let {
            if (it.name == "nexalloy") it.parentFile?.parentFile ?: it else it
        }
        val docsFile = File(projectRoot, "docs/installed-app-patch-audit.md")
        val auditFile = docsFile

        // The audit doc is kept locally but not published to GitHub; skip the
        // schema validation when it is absent rather than failing the build.
        if (!auditFile.exists()) return

        // Every Markdown table of the document, in order. The audit matrix is the first one;
        // later tables (catalog cross-reference, port attributions) have their own schemas.
        val tables = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in auditFile.readLines()) {
            if (line.startsWith("|")) {
                val block = current ?: mutableListOf<String>().also { tables.add(it) }
                current = block
                block.add(line)
            } else {
                current = null
            }
        }

        val tableLines = tables.firstOrNull().orEmpty().filter { !it.startsWith("|---") }
        assertTrue(tableLines.size >= 2, "Table must have header and rows")

        // First line is header
        val header = tableLines.first().split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val expectedHeaders = listOf(
            "Application",
            "Category",
            "Installed status",
            "Package ID",
            "Verification",
            "Sources searched",
            "Matching patches",
            "Feature summary",
            "Runtime feasibility",
            "Existing NexAlloy patch",
            "Next action",
        )
        assertEquals(expectedHeaders, header, "Headers must match required schema exactly")

        // Rows
        val rows = tableLines.drop(1).map { line ->
            line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        }

        assertTrue(rows.size >= 80, "Audit matrix must contain all required applications (found ${rows.size})")

        val validVerifications = setOf("runtime-verified", "source-verified", "unverified")
        val requiredApps = setOf(
            "Instagram", "Facebook", "WhatsApp", "Snapchat", "Discord", "LinkedIn", "X (Twitter)", "Reddit",
            "YouTube", "YouTube Music", "Spotify", "Google Photos (Media)", "Google Photos (Productivity)",
            "PhonePe", "Google Pay (GPay India)", "Paytm", "YONO SBI", "Bitwarden", "Termux", "Duolingo",
            "ChatGPT", "Claude", "CloudStream", "1DM+", "BGMI", "Clash Royale", "8 Ball Pool", "NexAlloyNext"
        )

        val auditedAppNames = mutableSetOf<String>()

        for (row in rows) {
            assertEquals(11, row.size, "Row must have exactly 11 columns: $row")
            val appName = row[0].replace("**", "").trim()
            val verification = row[4].replace("`", "").trim()

            auditedAppNames.add(appName)
            assertTrue(
                verification in validVerifications,
                "Verification status '$verification' for '$appName' must be one of $validVerifications"
            )
        }

        for (required in requiredApps) {
            assertTrue(
                auditedAppNames.contains(required),
                "Required application '$required' must be audited in matrix. Found: $auditedAppNames"
            )
        }

        // Every further table must stay well formed: each row keeps its header's column count.
        for (other in tables.drop(1)) {
            val dataLines = other.filter { !it.startsWith("|---") }
            val columns = dataLines.firstOrNull()?.split("|")?.map { it.trim() }?.count { it.isNotEmpty() }
                ?: continue
            for (line in dataLines) {
                assertEquals(
                    columns, line.split("|").map { it.trim() }.count { it.isNotEmpty() },
                    "Row must keep its table's column count: $line"
                )
            }
        }
    }
}
