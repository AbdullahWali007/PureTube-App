package com.puretube.adfree.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadEngine(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val TAG = "DownloadEngine"

        fun formatEta(etaInSeconds: Long): String {
            if (etaInSeconds <= 0) return ""
            val minutes = etaInSeconds / 60
            val seconds = etaInSeconds % 60
            return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
        }
    }

    /**
     * Directory for temporary and completed downloads in internal/scoped storage
     */
    val downloadDir: File by lazy {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Updates the underlying yt-dlp native binary over the air.
     * This auto-heals any extraction issues when YouTube alters internal player code.
     */
    suspend fun updateEngine(context: Context): Result<YoutubeDL.UpdateStatus> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context) ?: YoutubeDL.UpdateStatus.DONE
            Log.d(TAG, "yt-dlp engine update completed with status: $status")
            Result.success(status)
        } catch (e: Throwable) {
            Log.e(TAG, "yt-dlp engine update failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads a video or audio stream via native yt-dlp to the specified output directory.
     */
    suspend fun executeDownload(
        videoUrl: String,
        outputDir: File,
        onProgress: (progress: Float, etaStr: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val request = YoutubeDLRequest(videoUrl).apply {
                addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")
                addOption("-f", "bestvideo+bestaudio/best")
                addOption("--merge-output-format", "mp4")
                addOption("--no-mtime")
                addOption("--no-playlist")
                addOption("--no-check-certificates")
            }

            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, _ ->
                val etaStr = if (etaInSeconds > 0) formatEta(etaInSeconds) else ""
                onProgress(progress, etaStr)
            }

            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "executeDownload failed for $videoUrl: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Start or queue a new download entity
     */
    fun enqueueDownload(
        videoId: String,
        title: String,
        author: String,
        thumbnailUrl: String,
        durationText: String,
        qualityOption: VideoFormatOption
    ) {
        val downloadId = "${videoId}_${qualityOption.resolution.replace(" ", "_")}"
        val sanitizedTitle = sanitizeFilename(title)
        val ext = if (qualityOption.isAudioOnly) "m4a" else "mp4"
        val fileName = "${sanitizedTitle}_${qualityOption.resolution}.$ext"
        val targetFile = File(downloadDir, fileName)

        val entity = DownloadEntity(
            id = downloadId,
            videoId = videoId,
            title = title,
            author = author,
            thumbnailUrl = thumbnailUrl,
            durationText = durationText,
            qualityLabel = qualityOption.qualityLabel,
            resolution = qualityOption.resolution,
            format = qualityOption.format,
            mimeType = qualityOption.mimeType,
            downloadUrl = "https://www.youtube.com/watch?v=$videoId",
            localFilePath = targetFile.absolutePath,
            totalBytes = (qualityOption.approximateSizeMb * 1024 * 1024).toLong(),
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            createdAt = System.currentTimeMillis()
        )

        scope.launch {
            downloadDao.insertOrUpdate(entity)
            startOrResumeDownload(entity, qualityOption.ytdlFormatSpec)
        }
    }

    /**
     * Resumes a paused or queued download
     */
    fun resumeDownload(id: String) {
        scope.launch {
            val entity = downloadDao.getDownloadById(id) ?: return@launch
            if (entity.status == DownloadStatus.COMPLETED) return@launch
            startOrResumeDownload(entity)
        }
    }

    /**
     * Pauses an active download, safely stopping the native yt-dlp process
     */
    fun pauseDownload(id: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Exception) {
            Log.w(TAG, "destroyProcessById error: ${e.message}")
        }
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            downloadDao.updateStatus(id, DownloadStatus.PAUSED)
        }
    }

    /**
     * Cancels an ongoing download and deletes temporary files
     */
    fun cancelDownload(id: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Exception) {
            Log.w(TAG, "destroyProcessById error: ${e.message}")
        }
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            val entity = downloadDao.getDownloadById(id)
            if (entity != null) {
                try {
                    val sanitized = sanitizeFilename(entity.title)
                    val prefix = "$sanitized-${entity.id}"
                    downloadDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach {
                        try { it.delete() } catch (e: Exception) {}
                    }
                    val file = File(entity.localFilePath)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting temp file: ${e.message}")
                }
            }
            downloadDao.deleteById(id)
        }
    }

    /**
     * Deletes a download from database, and optionally removes the physical file from storage
     */
    fun deleteDownload(id: String, deleteFromStorage: Boolean = true) {
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Exception) {
            Log.w(TAG, "destroyProcessById error: ${e.message}")
        }
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            if (deleteFromStorage) {
                val entity = downloadDao.getDownloadById(id)
                if (entity != null) {
                    try {
                        val sanitized = sanitizeFilename(entity.title)
                        val prefix = "$sanitized-${entity.id}"
                        downloadDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach {
                            try { it.delete() } catch (e: Exception) {}
                        }
                        if (entity.localFilePath.isNotBlank()) {
                            val file = File(entity.localFilePath)
                            if (file.exists()) file.delete()
                        }
                        entity.contentUri?.let { uriStr ->
                            try {
                                context.contentResolver.delete(Uri.parse(uriStr), null, null)
                            } catch (e: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting download file: ${e.message}")
                    }
                }
            }
            downloadDao.deleteById(id)
        }
    }

    private fun getDiskBytesForDownload(prefix: String): Long {
        return try {
            downloadDir.listFiles()
                ?.filter { it.name.startsWith(prefix) }
                ?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private data class ParsedYtdlProgress(
        val percent: Float? = null,
        val totalBytes: Long? = null,
        val speedBytes: Long? = null
    )

    private fun parseYtdlLine(line: String?): ParsedYtdlProgress? {
        if (line.isNullOrBlank()) return null
        var percent: Float? = null
        var totalBytes: Long? = null
        var speedBytes: Long? = null

        try {
            // Match percentage e.g. " 45.2%"
            val percentMatch = Regex("""([0-9.]+)%""").find(line)
            if (percentMatch != null) {
                percent = percentMatch.groupValues[1].toFloatOrNull()
            }

            // Match total size e.g. "of ~? 42.50MiB" or "of 100.00MB" or "of 500.20KiB"
            val sizeMatch = Regex("""of\s+~?\s*([0-9.]+)\s*([KMGT]?i?B)""", RegexOption.IGNORE_CASE).find(line)
            if (sizeMatch != null) {
                val num = sizeMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val unit = sizeMatch.groupValues[2].uppercase()
                totalBytes = when {
                    unit.startsWith("G") -> (num * 1024 * 1024 * 1024).toLong()
                    unit.startsWith("M") -> (num * 1024 * 1024).toLong()
                    unit.startsWith("K") -> (num * 1024).toLong()
                    else -> num.toLong()
                }
            }

            // Match speed e.g. "at 3.21MiB/s"
            val speedMatch = Regex("""at\s+([0-9.]+)\s*([KMGT]?i?B)/s""", RegexOption.IGNORE_CASE).find(line)
            if (speedMatch != null) {
                val num = speedMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val unit = speedMatch.groupValues[2].uppercase()
                speedBytes = when {
                    unit.startsWith("G") -> (num * 1024 * 1024 * 1024).toLong()
                    unit.startsWith("M") -> (num * 1024 * 1024).toLong()
                    unit.startsWith("K") -> (num * 1024).toLong()
                    else -> num.toLong()
                }
            }
        } catch (e: Exception) {
            // ignore parse failure
        }
        return ParsedYtdlProgress(percent, totalBytes, speedBytes)
    }

    private fun startOrResumeDownload(entity: DownloadEntity, customFormatSpec: String? = null) {
        activeJobs[entity.id]?.cancel()

        // Ensure Foreground Service is active to keep download alive in background
        DownloadForegroundService.start(context)

        val job = scope.launch(Dispatchers.IO) {
            downloadDao.updateStatus(entity.id, DownloadStatus.DOWNLOADING)

            val isAudioOnly = entity.mimeType.startsWith("audio") ||
                entity.resolution.contains("Audio", ignoreCase = true) ||
                entity.format == "m4a" || entity.format == "mp3"

            val sanitized = sanitizeFilename(entity.title)
            val prefix = "$sanitized-${entity.id}"
            val ext = if (isAudioOnly) "m4a" else "mp4"
            val outputTemplate = "${downloadDir.absolutePath}/$sanitized-${entity.id}.%(ext)s"

            val initialDiskBytes = getDiskBytesForDownload(prefix)
            val initialBytes = maxOf(entity.downloadedBytes, initialDiskBytes)
            var currentTotalBytes = if (entity.totalBytes > 0) entity.totalBytes else 20 * 1024 * 1024L
            var peakProgressPercent = if (currentTotalBytes > 0) ((initialBytes.toFloat() / currentTotalBytes) * 100f).coerceIn(0f, 99f) else 0f

            var lastReportTime = System.currentTimeMillis()
            var lastReportBytes = initialBytes

            try {
                val videoUrl = if (entity.downloadUrl.startsWith("http")) {
                    entity.downloadUrl
                } else {
                    "https://www.youtube.com/watch?v=${entity.videoId}"
                }

                val formatFilter = customFormatSpec?.ifBlank { null } ?: if (isAudioOnly) {
                    "bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio/ba*"
                } else when {
                    entity.resolution.contains("1080") -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                    entity.resolution.contains("720") -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                    entity.resolution.contains("480") -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                    entity.resolution.contains("360") -> "bestvideo[height<=360]+bestaudio/best[height<=360]/best"
                    else -> "bestvideo+bestaudio/best"
                }

                val request = YoutubeDLRequest(videoUrl).apply {
                    addOption("-o", outputTemplate)
                    addOption("-f", formatFilter)
                    addOption("-c") // continue partial downloads
                    addOption("--part") // download to .part files for resumption
                    addOption("--no-mtime")
                    addOption("--no-playlist")
                    addOption("--no-check-certificates")
                    addOption("--extractor-args", "youtube:player_client=android,ios,web")
                    addOption("--user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    val cookieFile = CookieHelper.getExportedCookieFile(context)
                    if (cookieFile != null && cookieFile.exists()) {
                        addOption("--cookies", cookieFile.absolutePath)
                    }
                    addOption("--retries", "10")
                    addOption("--fragment-retries", "10")
                    if (isAudioOnly) {
                        addOption("-x") // Extract audio track only
                        addOption("--audio-format", "m4a") // Save pure m4a audio
                        addOption("--audio-quality", "0") // Highest audio quality
                    } else {
                        addOption("--merge-output-format", "mp4")
                    }
                }

                YoutubeDL.getInstance().execute(request, entity.id) { progress, _, line ->
                    if (!isActive) return@execute
                    val now = System.currentTimeMillis()
                    val timeDelta = now - lastReportTime
                    if (timeDelta >= 250) {
                        val parsed = parseYtdlLine(line)

                        // Dynamically update totalBytes if yt-dlp extracted exact stream size
                        if (parsed?.totalBytes != null && parsed.totalBytes > 0L) {
                            currentTotalBytes = maxOf(currentTotalBytes, parsed.totalBytes)
                        }

                        val rawProgress = parsed?.percent ?: progress.coerceIn(0f, 100f)
                        val effectiveProgress = maxOf(peakProgressPercent, rawProgress).coerceIn(0f, 100f)
                        peakProgressPercent = effectiveProgress

                        val currentDisk = getDiskBytesForDownload(prefix)
                        val calculatedBytes = ((effectiveProgress / 100f) * currentTotalBytes).toLong()
                        val currentDownloaded = maxOf(initialBytes, currentDisk, calculatedBytes).coerceAtMost(currentTotalBytes)

                        val parsedSpeed = parsed?.speedBytes
                        val speed = if (parsedSpeed != null && parsedSpeed > 0L) {
                            parsedSpeed
                        } else if (timeDelta > 0 && currentDownloaded > lastReportBytes) {
                            ((currentDownloaded - lastReportBytes) * 1000) / timeDelta
                        } else 0L

                        lastReportTime = now
                        lastReportBytes = currentDownloaded

                        scope.launch {
                            downloadDao.updateProgress(
                                id = entity.id,
                                downloadedBytes = currentDownloaded,
                                totalBytes = currentTotalBytes,
                                speed = speed.coerceAtLeast(0L),
                                status = DownloadStatus.DOWNLOADING
                            )
                        }
                    }
                }

                if (!isActive) {
                    downloadDao.updateStatus(entity.id, DownloadStatus.PAUSED)
                    return@launch
                }

                // Locate the output file matching prefix in downloadDir
                val downloadedFile = downloadDir.listFiles()?.find {
                    it.name.startsWith(prefix) && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp")
                } ?: File(downloadDir, "$prefix.$ext")

                val actualExt = if (downloadedFile.exists() && downloadedFile.extension.isNotBlank()) {
                    downloadedFile.extension.lowercase()
                } else {
                    ext
                }

                val actualMimeType = when (actualExt) {
                    "m4a", "aac" -> "audio/mp4"
                    "mp3" -> "audio/mpeg"
                    "opus", "ogg" -> "audio/ogg"
                    "webm" -> if (isAudioOnly) "audio/webm" else "video/webm"
                    "mp4" -> if (isAudioOnly) "audio/mp4" else "video/mp4"
                    else -> if (isAudioOnly) "audio/mp4" else entity.mimeType
                }

                val finalSize = if (downloadedFile.exists() && downloadedFile.length() > 0) downloadedFile.length() else maxOf(currentTotalBytes, lastReportBytes)

                // Publish to public MediaStore and Movies/PureTube or Music/PureTube folder
                val finalFile = saveToGallery(
                    context = context,
                    tempFile = downloadedFile,
                    title = entity.title,
                    format = actualExt,
                    mimeType = actualMimeType
                )

                downloadDao.markCompleted(
                    id = entity.id,
                    localFilePath = finalFile.first,
                    contentUri = finalFile.second?.toString(),
                    totalBytes = finalSize
                )

                Log.d(TAG, "Download completed successfully: ${finalFile.first} ($finalSize bytes)")

            } catch (e: Throwable) {
                if (isActive) {
                    Log.e(TAG, "Download failed for ${entity.title}: ${e.message}", e)
                    downloadDao.updateStatus(entity.id, DownloadStatus.FAILED, e.message ?: "Download failed")
                } else {
                    downloadDao.updateStatus(entity.id, DownloadStatus.PAUSED)
                }
            } finally {
                activeJobs.remove(entity.id)
            }
        }

        activeJobs[entity.id] = job
    }

    /**
     * Publishes completed video to device's public Movies/PureTube or Music/PureTube folder
     * and indexes into MediaStore so Gallery apps display it.
     */
    private fun saveToGallery(
        context: Context,
        tempFile: File,
        title: String,
        format: String,
        mimeType: String
    ): Pair<String, Uri?> {
        val sanitized = sanitizeFilename(title)
        val ext = if (format.isNotBlank()) format else "mp4"
        val fileName = "$sanitized.$ext"
        val isAudio = mimeType.startsWith("audio")

        var finalPath = tempFile.absolutePath
        var mediaUri: Uri? = null

        if (!tempFile.exists() || tempFile.length() == 0L) {
            return Pair(finalPath, null)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isAudio) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (isAudio) "Music/PureTube" else "Movies/PureTube"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                    if (isAudio) {
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, "PureTube")
                        put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    mediaUri = uri
                }

            } else {
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                    ),
                    "PureTube"
                )
                if (!publicDir.exists()) publicDir.mkdirs()
                val targetFile = File(publicDir, fileName)
                tempFile.copyTo(targetFile, overwrite = true)
                finalPath = targetFile.absolutePath

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalPath),
                    arrayOf(mimeType)
                ) { path, uri ->
                    Log.d(TAG, "MediaScanner scanned: $path -> $uri")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to export to gallery: ${e.message}", e)
        }

        return Pair(finalPath, mediaUri)
    }

    private fun sanitizeFilename(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(60)
        // Strip trailing extension if the video title already had one (e.g. .mp4, .m4a, .mp3, .webm)
        return clean.replace(Regex("(?i)\\.(mp4|m4a|mp3|webm|opus|ogg|mkv|avi|mov|flv)$"), "")
    }
}
