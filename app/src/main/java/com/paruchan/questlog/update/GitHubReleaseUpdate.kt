package com.paruchan.questlog.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
)

data class UpdateCandidate(
    val release: ReleaseInfo,
    val apk: ReleaseAsset,
    val checksum: ReleaseAsset?,
)

sealed interface UpdateCheckResult {
    data class Available(val candidate: UpdateCandidate) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestVersion: String) : UpdateCheckResult
    data class NoInstallableAsset(val latestVersion: String) : UpdateCheckResult
}

sealed interface InstallResult {
    data class Started(val apk: File) : InstallResult
    data object NeedsUnknownSourcesPermission : InstallResult
}

object VersionComparator {
    fun isNewer(remoteTag: String, currentVersion: String): Boolean =
        compare(remoteTag, currentVersion) > 0

    fun compare(left: String, right: String): Int {
        val leftParts = normalizedParts(left)
        val rightParts = normalizedParts(right)
        val max = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until max) {
            val l = leftParts.getOrElse(index) { 0 }
            val r = rightParts.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun normalizedParts(value: String): List<Int> =
        value.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .split('.', '_')
            .mapNotNull { part -> part.toIntOrNull() }
}

object GitHubReleaseParser {
    fun parseRelease(json: String): ReleaseInfo {
        val obj = JsonParser.parseString(json).asJsonObject
        val assets = obj["assets"]?.asJsonArray?.mapNotNull { element ->
            val asset = element.asJsonObject
            val name = asset["name"]?.asString.orEmpty()
            val url = asset["browser_download_url"]?.asString.orEmpty()
            if (name.isBlank() || url.isBlank()) null else ReleaseAsset(name = name, downloadUrl = url)
        }.orEmpty()

        return ReleaseInfo(
            tagName = obj["tag_name"]?.asString.orEmpty(),
            htmlUrl = obj["html_url"]?.asString.orEmpty(),
            assets = assets,
        )
    }

    fun selectUpdateCandidate(release: ReleaseInfo): UpdateCandidate? {
        val apk = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .sortedWith(
                compareBy<ReleaseAsset> { it.name.contains("debug", ignoreCase = true) }
                    .thenByDescending { it.name.contains("release", ignoreCase = true) }
                    .thenBy { it.name },
            )
            .firstOrNull()
            ?: return null

        val checksum = release.assets.firstOrNull { asset ->
            asset.name.equals("${apk.name}.sha256", ignoreCase = true) ||
                asset.name.equals(apk.name.removeSuffix(".apk") + ".sha256", ignoreCase = true)
        }

        return UpdateCandidate(release = release, apk = apk, checksum = checksum)
    }
}

class GitHubReleaseUpdater(
    private val repository: String,
    private val currentVersion: String,
) {
    suspend fun checkLatest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val release = GitHubReleaseParser.parseRelease(
            getText("https://api.github.com/repos/$repository/releases/latest"),
        )

        if (!VersionComparator.isNewer(release.tagName, currentVersion)) {
            return@withContext UpdateCheckResult.UpToDate(
                currentVersion = currentVersion,
                latestVersion = release.tagName,
            )
        }

        val candidate = GitHubReleaseParser.selectUpdateCandidate(release)
            ?: return@withContext UpdateCheckResult.NoInstallableAsset(release.tagName)

        UpdateCheckResult.Available(candidate)
    }

    suspend fun download(candidate: UpdateCandidate, destinationDir: File): File = withContext(Dispatchers.IO) {
        destinationDir.mkdirs()
        val apkFile = File(destinationDir, candidate.apk.name)
        downloadTo(candidate.apk.downloadUrl, apkFile)

        candidate.checksum?.let { checksumAsset ->
            val expected = getText(checksumAsset.downloadUrl).trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            val actual = sha256(apkFile)
            require(expected.equals(actual, ignoreCase = true)) {
                "Downloaded APK checksum mismatch"
            }
        }

        apkFile
    }

    fun install(context: Context, apkFile: File): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallResult.NeedsUnknownSourcesPermission
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return InstallResult.Started(apkFile)
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadTo(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
