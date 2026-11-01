package com.puretube.adfree.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM video_downloads ORDER BY createdAt DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM video_downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt DESC")
    fun getActiveDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM video_downloads WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY createdAt DESC")
    fun getRunningDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM video_downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM video_downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM video_downloads WHERE videoId = :videoId AND qualityLabel = :qualityLabel LIMIT 1")
    suspend fun getDownloadByVideoAndQuality(videoId: String, qualityLabel: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("UPDATE video_downloads SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, speedBytesPerSec = :speed, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long, speed: Long, status: DownloadStatus)

    @Query("UPDATE video_downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String? = null)

    @Query("UPDATE video_downloads SET status = 'COMPLETED', localFilePath = :localFilePath, contentUri = :contentUri, totalBytes = :totalBytes, downloadedBytes = :totalBytes, speedBytesPerSec = 0, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: String, localFilePath: String, contentUri: String?, totalBytes: Long, completedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM video_downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM video_downloads WHERE status = 'COMPLETED'")
    suspend fun deleteAllCompleted()
}
