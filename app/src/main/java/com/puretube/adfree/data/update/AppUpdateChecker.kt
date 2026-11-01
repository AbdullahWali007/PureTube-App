package com.puretube.adfree.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.puretube.adfree.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface AppUpdateResult {
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : AppUpdateResult
    data class NoUpdateAvailable(val currentVersionName: String) : AppUpdateResult
    data class Error(val message: String) : AppUpdateResult
}

object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/AbdullahWali007/PureTube-App/main/update.json"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private var lastCheckTimestamp: Long = 0
    private const val CHECK_THROTTLE_MS = 6 * 60 * 60 * 1000L // Check at most once every 6 hours automatically

    suspend fun fetchUpdateInfo(
        currentVersionCode: Int,
        currentVersionName: String,
        force: Boolean = false
    ): AppUpdateResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastCheckTimestamp < CHECK_THROTTLE_MS)) {
            AppLogger.d(TAG, "Update check skipped due to throttling")
            return@withContext AppUpdateResult.NoUpdateAvailable(currentVersionName)
        }

        try {
            AppLogger.i(TAG, "Checking for updates from: $UPDATE_JSON_URL")
            val request = Request.Builder()
                .url(UPDATE_JSON_URL)
                .header("User-Agent", "PureTube-Android/$currentVersionName")
                .header("Cache-Control", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "HTTP error ${response.code}: ${response.message}"
                AppLogger.w(TAG, errorMsg)
                return@withContext AppUpdateResult.Error(errorMsg)
            }

            val bodyString = response.body?.string()
            if (bodyString.isNullOrBlank()) {
                AppLogger.w(TAG, "Empty response body from update.json")
                return@withContext AppUpdateResult.Error("Empty update response")
            }

            val json = JSONObject(bodyString)
            val latestVersionCode = json.optInt("latestVersionCode", currentVersionCode)
            val latestVersionName = json.optString("latestVersionName", currentVersionName)
            val minSupportedVersionCode = json.optInt("minSupportedVersionCode", 1)
            val isMandatory = json.optBoolean("isMandatory", false)
            val releaseDate = json.optString("releaseDate", "")
            val downloadUrl = json.optString("downloadUrl", "")
            val releaseNotes = json.optString("releaseNotes", "")

            lastCheckTimestamp = now

            if (latestVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                val updateInfo = AppUpdateInfo(
                    latestVersionCode = latestVersionCode,
                    latestVersionName = latestVersionName,
                    minSupportedVersionCode = minSupportedVersionCode,
                    isMandatory = isMandatory,
                    releaseDate = releaseDate,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes
                )
                AppLogger.i(TAG, "Update available: v$latestVersionName (code: $latestVersionCode)")
                return@withContext AppUpdateResult.UpdateAvailable(updateInfo)
            } else {
                AppLogger.i(TAG, "App is up to date: v$currentVersionName (code: $currentVersionCode)")
                return@withContext AppUpdateResult.NoUpdateAvailable(currentVersionName)
            }
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Failed to check for updates: ${e.message}", e)
            return@withContext AppUpdateResult.Error(e.message ?: "Unknown error while checking for updates")
        }
    }

    /**
     * Checks if a valid, complete APK matching expectedVersionCode already exists on disk
     */
    fun getDownloadedUpdateApk(context: Context, expectedVersionCode: Int): File? {
        return try {
            val updatesDir = File(context.getExternalFilesDir(null), "updates")
            if (!updatesDir.exists()) return null

            val targetFile = File(updatesDir, "puretube_v${expectedVersionCode}.apk")
            val legacyFile = File(updatesDir, "puretube_update.apk")
            val candidates = listOf(targetFile, legacyFile)

            for (file in candidates) {
                if (file.exists() && file.length() > 0) {
                    val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    if (pkgInfo != null && pkgInfo.packageName == context.packageName) {
                        val fileVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pkgInfo.longVersionCode.toInt()
                        } else {
                            @Suppress("DEPRECATION")
                            pkgInfo.versionCode
                        }
                        if (fileVersionCode == expectedVersionCode) {
                            AppLogger.i(TAG, "Reusing already downloaded APK: ${file.absolutePath} for version $expectedVersionCode")
                            return file
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking existing APK: ${e.message}")
            null
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetVersionCode: Int = 0,
        targetFileName: String = if (targetVersionCode > 0) "puretube_v${targetVersionCode}.apk" else "puretube_update.apk",
        onProgress: (downloadedBytes: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        // Fast-path: Check if already downloaded
        if (targetVersionCode > 0) {
            val existing = getDownloadedUpdateApk(context, targetVersionCode)
            if (existing != null) {
                onProgress(existing.length(), existing.length(), 100)
                return@withContext Result.success(existing)
            }
        }

        try {
            AppLogger.i(TAG, "Starting APK download from: $downloadUrl")
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "PureTube-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download APK: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Response body is null"))
            val totalBytes = body.contentLength()

            val updatesDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            val finalApkFile = File(updatesDir, targetFileName)
            val tempApkFile = File(updatesDir, "$targetFileName.tmp")

            if (tempApkFile.exists()) tempApkFile.delete()
            if (finalApkFile.exists()) finalApkFile.delete()

            body.byteStream().use { inputStream ->
                FileOutputStream(tempApkFile).use { outputStream ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        onProgress(totalDownloaded, totalBytes, percent)
                    }
                    outputStream.flush()
                }
            }

            // Rename .tmp to final target file
            if (!tempApkFile.renameTo(finalApkFile)) {
                tempApkFile.copyTo(finalApkFile, overwrite = true)
                tempApkFile.delete()
            }

            AppLogger.i(TAG, "APK download complete: ${finalApkFile.absolutePath} (${finalApkFile.length()} bytes)")
            return@withContext Result.success(finalApkFile)
        } catch (e: Throwable) {
            AppLogger.e(TAG, "APK download failed: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not open specific unknown sources settings: ${e.message}")
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) {
                AppLogger.e(TAG, "Cannot launch installer: File does not exist at ${apkFile.absolutePath}")
                return false
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            AppLogger.i(TAG, "Launching Android Package Installer for URI: $apkUri")
            context.startActivity(installIntent)
            true
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Failed to launch APK installer: ${e.message}", e)
            false
        }
    }
}
