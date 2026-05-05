package com.paruchan.questlog.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
        val obj = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (error: JsonParseException) {
            throw IllegalArgumentException("GitHub release response is not valid JSON", error)
        } ?: throw IllegalArgumentException("GitHub release response is not an object")

        val assets = obj["assets"]
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { element ->
                val asset = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val name = asset.optionalString("name")
                val url = asset.optionalString("browser_download_url")
                if (name.isBlank() || url.isBlank()) null else ReleaseAsset(name = name, downloadUrl = url)
            }
            .orEmpty()

        val tagName = obj.optionalString("tag_name")
        require(tagName.isNotBlank()) { "GitHub release response is missing tag_name" }

        return ReleaseInfo(
            tagName = tagName,
            htmlUrl = obj.optionalString("html_url"),
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

    private fun com.google.gson.JsonObject.optionalString(name: String): String =
        runCatching { this[name]?.takeUnless { it.isJsonNull }?.asString.orEmpty().trim() }.getOrDefault("")
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
        return try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requireSuccess()
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.requireSuccess()
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
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

    private fun HttpURLConnection.requireSuccess() {
        val code = responseCode
        if (code !in 200..299) {
            val responseMessage = errorStream
                ?.bufferedReader()
                ?.use { it.readText().trim() }
                ?.takeIf { it.isNotBlank() }
                ?: this.responseMessage
                ?: "HTTP $code"
            throw IOException("GitHub request failed with HTTP $code: $responseMessage")
        }
    }
}
