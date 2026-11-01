package com.puretube.adfree.data.download

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.puretube.adfree.PureTubeApplication
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

object CookieHelper {
    private const val TAG = "CookieHelper"

    fun getExportedCookieFile(context: Context? = null): File? {
        return try {
            val ctx = context ?: try { PureTubeApplication.instance } catch (e: Exception) { null } ?: return null
            val cookieManager = CookieManager.getInstance()
            val urls = listOf("https://www.youtube.com", "https://m.youtube.com", "https://youtube.com")
            val cookieMap = mutableMapOf<String, String>()

            for (url in urls) {
                val raw = cookieManager.getCookie(url)
                if (!raw.isNullOrBlank()) {
                    raw.split(";").forEach { part ->
                        val kv = part.trim().split("=", limit = 2)
                        if (kv.size == 2 && kv[0].isNotBlank()) {
                            cookieMap[kv[0].trim()] = kv[1].trim()
                        }
                    }
                }
            }

            if (cookieMap.isEmpty()) return null

            val cookieFile = File(ctx.cacheDir, "yt_cookies.txt")
            val sb = StringBuilder()
            sb.append("# Netscape HTTP Cookie File\n")
            sb.append("# https://curl.haxx.se/rfc/cookie_spec.html\n")
            val expiry = (System.currentTimeMillis() / 1000) + (30L * 24 * 3600)

            for ((key, value) in cookieMap) {
                sb.append(".youtube.com\tTRUE\t/\tTRUE\t$expiry\t$key\t$value\n")
            }

            cookieFile.writeText(sb.toString())
            if (cookieFile.exists() && cookieFile.length() > 0) cookieFile else null
        } catch (e: Exception) {
            Log.w(TAG, "Error exporting cookies: ${e.message}")
            null
        }
    }
}

data class VideoFormatOption(
    val id: String,
    val qualityLabel: String, // e.g. "1080p Full HD", "720p HD", "480p", "360p", "Audio (HQ MP3/M4A)"
    val resolution: String,   // "1080p", "720p", "480p", "360p", "Audio"
    val format: String = "mp4",
    val mimeType: String = "video/mp4",
    val downloadUrl: String = "",
    val approximateSizeMb: Double = 0.0,
    val isAudioOnly: Boolean = false,
    val isHd: Boolean = false,
    val fps: Int = 30,
    val ytdlFormatSpec: String = "bestvideo+bestaudio/best"
)

data class VideoMetadata(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val durationText: String,
    val availableFormats: List<VideoFormatOption>
)

object StreamResolver {
    private const val TAG = "StreamResolver"

    // High performance in-memory LRU Cache (stores up to 100 resolved video metadata objects)
    private val metadataCache = android.util.LruCache<String, VideoMetadata>(100)

    /**
     * Synchronously returns cached VideoMetadata if available for instant 0ms sheet opening
     */
    fun getCachedMetadata(videoId: String): VideoMetadata? {
        if (videoId.isBlank()) return null
        return metadataCache.get(videoId)
    }

    /**
     * Returns instant optimistic metadata so user never sees a blank screen
     */
    fun getOptimisticMetadata(videoId: String, title: String, knownDurationSec: Long = 0L): VideoMetadata {
        val cached = metadataCache.get(videoId)
        if (cached != null) return cached

        return generateFallbackMetadata(
            videoId = videoId,
            title = if (title.isBlank() || title == "YouTube") "YouTube Video ($videoId)" else title,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            knownDurationSec = knownDurationSec
        )
    }

    /**
     * Extracts YouTube Video ID from any YouTube URL format (watch?v=, youtu.be/, shorts/, embed/)
     */
    fun extractVideoId(url: String): String? {
        if (url.isBlank()) return null

        // Pattern 1: v=VIDEO_ID
        val vPattern = Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})")
        val vMatcher = vPattern.matcher(url)
        if (vMatcher.find()) return vMatcher.group(1)

        // Pattern 2: youtu.be/VIDEO_ID
        val shortPattern = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})")
        val shortMatcher = shortPattern.matcher(url)
        if (shortMatcher.find()) return shortMatcher.group(1)

        // Pattern 3: /shorts/VIDEO_ID or /embed/VIDEO_ID
        val pathPattern = Pattern.compile("/(?:shorts|embed)/([a-zA-Z0-9_-]{11})")
        val pathMatcher = pathPattern.matcher(url)
        if (pathMatcher.find()) return pathMatcher.group(1)

        // If it's already an 11-character video ID
        if (url.length == 11 && url.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return url
        }

        return null
    }

    /**
     * Resolves complete video metadata and all available download resolutions using native yt-dlp with caching
     */
    suspend fun resolveVideo(
        videoId: String,
        fallbackTitle: String = "YouTube Video",
        knownDurationSec: Long = 0L
    ): VideoMetadata = withContext(Dispatchers.IO) {
        // Fast-path: Check cache first
        val cached = metadataCache.get(videoId)
        if (cached != null && cached.durationSeconds > 0) {
            Log.d(TAG, "Returning cached video metadata for $videoId in 0ms")
            return@withContext cached
        }

        val videoUrl = "https://www.youtube.com/watch?v=$videoId"
        val thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        try {
            val request = YoutubeDLRequest(videoUrl).apply {
                addOption("--no-playlist")
                addOption("--no-check-certificates")
                addOption("--extractor-args", "youtube:player_client=android,ios,web")
                addOption("--user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                val cookieFile = CookieHelper.getExportedCookieFile()
                if (cookieFile != null && cookieFile.exists()) {
                    addOption("--cookies", cookieFile.absolutePath)
                }
            }

            val info = YoutubeDL.getInstance().getInfo(request)
            val title = info.title ?: fallbackTitle
            val author = info.uploader ?: "YouTube Creator"
            val duration = if (info.duration > 0) info.duration.toLong() else knownDurationSec
            val thumb = info.thumbnail ?: thumbnailUrl

            val resolvedFormats = extractRealQualityOptions(
                videoId = videoId,
                durationSeconds = duration,
                formats = info.formats
            )

            val resolved = VideoMetadata(
                videoId = videoId,
                title = if (title.isBlank() || title == "YouTube") "YouTube Video ($videoId)" else title,
                author = author,
                thumbnailUrl = thumb,
                durationSeconds = duration,
                durationText = formatDuration(duration),
                availableFormats = resolvedFormats
            )

            metadataCache.put(videoId, resolved)
            return@withContext resolved
        } catch (e: Throwable) {
            Log.w(TAG, "yt-dlp info extraction fallback for $videoId: ${e.message}")
            val fallback = generateFallbackMetadata(videoId, fallbackTitle, thumbnailUrl, knownDurationSec)
            return@withContext fallback
        }
    }

    /**
     * Extracts and computes accurate real-world format sizes and options from yt-dlp formats
     */
    fun extractRealQualityOptions(
        videoId: String,
        durationSeconds: Long,
        formats: List<com.yausername.youtubedl_android.mapper.VideoFormat>?
    ): List<VideoFormatOption> {
        val duration = if (durationSeconds > 0) durationSeconds else 180L

        // 1. Calculate best Audio stream size
        val audioFormat = formats?.filter {
            val vc = it.vcodec ?: ""
            val ac = it.acodec ?: ""
            val isAudioStream = (vc.isBlank() || vc == "none") && (ac.isNotBlank() && ac != "none" || it.abr > 0f)
            isAudioStream
        }?.maxByOrNull { it.abr.toDouble() } ?: formats?.find { it.ext == "m4a" }

        val audioBytes: Long = if (audioFormat != null) {
            val fs = audioFormat.fileSize
            val abr = audioFormat.abr.toDouble()
            if (fs > 0L) {
                fs
            } else if (abr > 0.0 && durationSeconds > 0) {
                ((abr * 1000.0 / 8.0) * durationSeconds).toLong()
            } else if (durationSeconds > 0) {
                ((128.0 * 1000.0 / 8.0) * durationSeconds).toLong()
            } else 0L
        } else if (durationSeconds > 0) {
            ((128.0 * 1000.0 / 8.0) * durationSeconds).toLong()
        } else 0L

        // Helper to compute realistic video format size from formats list or bitrate
        fun computeFormatSizeMb(maxHeight: Int, minHeight: Int, fallbackBitrateKbps: Long): Double {
            val matched = formats?.filter {
                val h = it.height
                val vc = it.vcodec ?: ""
                h in minHeight..maxHeight && (vc.isNotBlank() && vc != "none")
            }?.maxByOrNull { it.height } ?: formats?.find { it.height == maxHeight }

            val matchedAc = matched?.acodec ?: ""
            val isProgressive = matched != null && matchedAc.isNotBlank() && matchedAc != "none"

            val vidBytes: Long = if (matched != null) {
                val fs = matched.fileSize
                val tbr = matched.tbr.toDouble()
                if (fs > 0L) {
                    fs
                } else if (tbr > 0.0 && durationSeconds > 0) {
                    val audioAbr = audioFormat?.abr?.toDouble() ?: 128.0
                    val vBitrate = if (isProgressive) tbr else (tbr - audioAbr).coerceAtLeast(100.0)
                    ((vBitrate * 1000.0 / 8.0) * durationSeconds).toLong()
                } else if (durationSeconds > 0) {
                    ((fallbackBitrateKbps * 1000.0 / 8.0) * durationSeconds).toLong()
                } else 0L
            } else if (durationSeconds > 0) {
                ((fallbackBitrateKbps * 1000.0 / 8.0) * durationSeconds).toLong()
            } else 0L

            val totalBytes = if (isProgressive) vidBytes else (vidBytes + audioBytes)
            val mb = if (totalBytes > 0L) {
                Math.round((totalBytes.toDouble() / (1024.0 * 1024.0)) * 10.0) / 10.0
            } else if (duration > 0) {
                val est = ((fallbackBitrateKbps + 128.0) * 1000.0 / 8.0) * duration
                Math.round((est / (1024.0 * 1024.0)) * 10.0) / 10.0
            } else 0.0

            return mb.coerceAtLeast(0.1)
        }

        val size1080 = computeFormatSizeMb(1080, 721, 3500L)
        val size720 = computeFormatSizeMb(720, 481, 1800L)
        val size480 = computeFormatSizeMb(480, 361, 800L)
        val size360 = computeFormatSizeMb(360, 144, 450L)
        val sizeAudio = if (audioBytes > 0L) {
            Math.round((audioBytes.toDouble() / (1024.0 * 1024.0)) * 10.0) / 10.0
        } else if (duration > 0) {
            val est = (128L * 1000L / 8L) * duration
            Math.round((est.toDouble() / (1024.0 * 1024.0)) * 10.0) / 10.0
        } else 0.0.coerceAtLeast(0.1)

        return listOf(
            VideoFormatOption(
                id = "${videoId}_1080p",
                qualityLabel = "1080p Full HD",
                resolution = "1080p",
                format = "mp4",
                mimeType = "video/mp4",
                approximateSizeMb = size1080,
                isAudioOnly = false,
                isHd = true,
                ytdlFormatSpec = "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
            ),
            VideoFormatOption(
                id = "${videoId}_720p",
                qualityLabel = "720p HD (Recommended)",
                resolution = "720p",
                format = "mp4",
                mimeType = "video/mp4",
                approximateSizeMb = size720,
                isAudioOnly = false,
                isHd = true,
                ytdlFormatSpec = "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
            ),
            VideoFormatOption(
                id = "${videoId}_480p",
                qualityLabel = "480p Standard",
                resolution = "480p",
                format = "mp4",
                mimeType = "video/mp4",
                approximateSizeMb = size480,
                isAudioOnly = false,
                isHd = false,
                ytdlFormatSpec = "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
            ),
            VideoFormatOption(
                id = "${videoId}_360p",
                qualityLabel = "360p Compact",
                resolution = "360p",
                format = "mp4",
                mimeType = "video/mp4",
                approximateSizeMb = size360,
                isAudioOnly = false,
                isHd = false,
                ytdlFormatSpec = "bestvideo[height<=360]+bestaudio/best[height<=360]/best"
            ),
            VideoFormatOption(
                id = "${videoId}_audio",
                qualityLabel = "Audio Only (HQ M4A/MP3)",
                resolution = "Audio",
                format = "m4a",
                mimeType = "audio/mp4",
                approximateSizeMb = sizeAudio.coerceAtLeast(0.1),
                isAudioOnly = true,
                isHd = false,
                ytdlFormatSpec = "bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio/ba*"
            )
        )
    }

    private fun generateFallbackMetadata(
        videoId: String,
        title: String,
        thumbnailUrl: String,
        knownDurationSec: Long = 0L
    ): VideoMetadata {
        return VideoMetadata(
            videoId = videoId,
            title = if (title.isBlank() || title == "YouTube") "YouTube Video ($videoId)" else title,
            author = "YouTube Creator",
            thumbnailUrl = thumbnailUrl,
            durationSeconds = knownDurationSec,
            durationText = formatDuration(knownDurationSec),
            availableFormats = extractRealQualityOptions(videoId, knownDurationSec, null)
        )
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
}
