package com.paruchan.questlog

import com.paruchan.questlog.update.GitHubReleaseParser
import com.paruchan.questlog.update.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdateTest {
    @Test
    fun `version comparison handles v-prefixed semver tags`() {
        assertTrue(VersionComparator.isNewer("v0.1.1", "0.1.0"))
        assertTrue(VersionComparator.isNewer("v0.2.0", "0.1.9"))
        assertFalse(VersionComparator.isNewer("v0.1.0", "0.1.0"))
        assertFalse(VersionComparator.isNewer("v0.0.9", "0.1.0"))
    }

    @Test
    fun `release parser selects apk and checksum sidecar`() {
        val release = GitHubReleaseParser.parseRelease(
            """
            {
              "tag_name": "v0.2.0",
              "html_url": "https://github.com/bee-san/paru_quests/releases/tag/v0.2.0",
              "assets": [
                {
                  "name": "notes.txt",
                  "browser_download_url": "https://example.test/notes.txt"
                },
                {
                  "name": "paruchan-quest-log-release.apk",
                  "browser_download_url": "https://example.test/app.apk"
                },
                {
                  "name": "paruchan-quest-log-release.apk.sha256",
                  "browser_download_url": "https://example.test/app.apk.sha256"
                }
              ]
            }
            """.trimIndent(),
        )

        val candidate = GitHubReleaseParser.selectUpdateCandidate(release)

        assertNotNull(candidate)
        assertEquals("paruchan-quest-log-release.apk", candidate?.apk?.name)
        assertEquals("paruchan-quest-log-release.apk.sha256", candidate?.checksum?.name)
    }
}
