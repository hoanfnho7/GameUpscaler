package com.nho.upscaler

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AppReleaseInfo(
    val tagName: String,
    val title: String,
    val changelog: String,
    val publishedDate: String,
    val apkUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object AppUpdater {
    private const val REPO_OWNER = "hoanfnho7"
    private const val REPO_NAME = "GameUpscaler"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val USER_AGENT = "GameUpscaler-Android-Updater"

    fun getCurrentVersionName(context: Context): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName?.trim()?.ifEmpty { "1.0.0" } ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    /**
     * Compares two semantic version strings (e.g. "v1.0.97" vs "1.0.96").
     * Returns > 0 if [remote] is newer than [local], 0 if equal, < 0 if older.
     */
    fun compareVersions(remote: String, local: String): Int {
        val rParts = extractVersionNumbers(remote)
        val lParts = extractVersionNumbers(local)
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val rv = rParts.getOrElse(i) { 0 }
            val lv = lParts.getOrElse(i) { 0 }
            if (rv != lv) return rv.compareTo(lv)
        }
        return 0
    }

    private fun extractVersionNumbers(ver: String): List<Int> {
        val cleaned = ver.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        return cleaned.split(".").map { part ->
            part.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    fun cleanupOldApks(context: Context) {
        try {
            val dir = File(context.cacheDir, "updates")
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun checkForUpdate(context: Context): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("GitHub API HTTP $code"))
            }

            val bodyText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(bodyText)

            if (json.optBoolean("draft", false)) {
                return@withContext Result.success(null)
            }

            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                return@withContext Result.success(null)
            }

            val currentVer = getCurrentVersionName(context)
            if (compareVersions(tagName, currentVer) <= 0) {
                return@withContext Result.success(null)
            }

            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            var apkName = "GameUpscaler-$tagName.apk"
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }
            if (apkUrl.isEmpty()) {
                return@withContext Result.success(null)
            }

            val title = json.optString("name", "").trim().ifEmpty { "GameUpscaler $tagName" }
            val rawNotes = json.optString("body", "").trim()
            val formattedNotes = formatReleaseNotes(rawNotes)
            val publishedRaw = json.optString("published_at", "")
            val publishedDate = formatPublishedDate(publishedRaw)
            val htmlUrl = json.optString(
                "html_url",
                "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$tagName"
            )

            Result.success(
                AppReleaseInfo(
                    tagName = tagName,
                    title = title,
                    changelog = formattedNotes,
                    publishedDate = publishedDate,
                    apkUrl = apkUrl,
                    apkFileName = apkName,
                    apkSizeBytes = apkSize,
                    htmlUrl = htmlUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun formatPublishedDate(iso: String): String {
        // Converts "2026-11-27T18:20:10Z" -> "27/11/2026"
        return try {
            val datePart = iso.substringBefore("T")
            val parts = datePart.split("-")
            if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatReleaseNotes(raw: String): String {
        if (raw.isBlank()) return ""
        val lines = raw.replace("\r\n", "\n").split("\n")
        val cleaned = ArrayList<String>(lines.size)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (cleaned.isNotEmpty() && cleaned.last().isNotEmpty()) cleaned.add("")
                continue
            }
            // Strip noisy auto-generated compare URL line
            if (trimmed.startsWith("**Full Changelog**:", ignoreCase = true)) continue
            // Make markdown headers & bullets cleaner in plain Compose Text
            val pretty = when {
                trimmed.startsWith("### ") -> trimmed.removePrefix("### ").trim()
                trimmed.startsWith("## ") -> trimmed.removePrefix("## ").trim()
                trimmed.startsWith("# ") -> trimmed.removePrefix("# ").trim()
                trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ").trim()}"
                trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("* ").trim()}"
                else -> trimmed
            }.replace("**", "")
            cleaned.add(pretty)
        }
        return cleaned.joinToString("\n").trim()
    }

    suspend fun downloadApk(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val updatesDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            val safeTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(updatesDir, "GameUpscaler-$safeTag.apk")
            val tempFile = File(updatesDir, "GameUpscaler-$safeTag.apk.part")

            // If exact APK was already downloaded and matches expected size, reuse immediately
            if (targetFile.exists() && release.apkSizeBytes > 0L && targetFile.length() == release.apkSizeBytes) {
                withContext(Dispatchers.Main) {
                    onProgress(1f, release.apkSizeBytes, release.apkSizeBytes)
                }
                return@withContext Result.success(targetFile)
            }
            if (tempFile.exists()) tempFile.delete()

            // Manually follow redirects (GitHub releases 302 -> objects.githubusercontent.com)
            var currentUrl = release.apkUrl
            var redirectCount = 0
            while (redirectCount < 8) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("Redirect location empty (HTTP $code)"))
                    }
                    currentUrl = URL(URL(currentUrl), loc).toString()
                    redirectCount++
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("Download failed (HTTP $code)"))
                }
                break
            }

            val activeConn = conn ?: return@withContext Result.failure(Exception("Connection failed"))
            val contentLength = activeConn.contentLengthLong.let {
                if (it > 0L) it else release.apkSizeBytes
            }

            var downloaded = 0L
            var lastEmitTime = 0L
            BufferedInputStream(activeConn.inputStream, 16 * 1024).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= 80L || (contentLength > 0L && downloaded >= contentLength)) {
                            lastEmitTime = now
                            val prog = if (contentLength > 0L) {
                                (downloaded.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            val dlSnap = downloaded
                            withContext(Dispatchers.Main) {
                                onProgress(prog, dlSnap, contentLength)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded < 100_000L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("APK file incomplete ($downloaded bytes)"))
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val finalTotal = if (contentLength > 0L) contentLength else downloaded
            withContext(Dispatchers.Main) {
                onProgress(1f, downloaded, finalTotal)
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Launches Android's native package installer for [apkFile].
     * Returns true if the installer activity started, or false if the user was redirected
     * to grant "Install unknown apps" permission first.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return false
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        return true
    }

    fun formatSizeMb(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
