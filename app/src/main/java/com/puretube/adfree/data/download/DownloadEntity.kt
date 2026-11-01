package com.puretube.adfree.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(tableName = "video_downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String, // e.g. UUID or videoId_quality
    val videoId: String,
    val title: String,
    val author: String = "",
    val thumbnailUrl: String = "",
    val durationText: String = "",
    val qualityLabel: String = "720p", // 1080p, 720p, 480p, 360p, Audio
    val resolution: String = "1280x720",
    val format: String = "mp4",
    val mimeType: String = "video/mp4",
    val downloadUrl: String,
    val localFilePath: String = "",
    val contentUri: String? = null,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val isFinished: Boolean
        get() = status == DownloadStatus.COMPLETED

    val isActive: Boolean
        get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED

    val isPaused: Boolean
        get() = status == DownloadStatus.PAUSED
}
