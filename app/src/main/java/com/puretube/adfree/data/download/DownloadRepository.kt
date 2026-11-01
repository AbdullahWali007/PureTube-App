package com.puretube.adfree.data.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.Flow
import java.io.File

class DownloadRepository(private val context: Context) {
    private val database = DownloadDatabase.getInstance(context)
    private val dao = database.downloadDao()
    val engine = DownloadEngine(context, dao)

    val allDownloads: Flow<List<DownloadEntity>> = dao.getAllDownloadsFlow()
    val activeDownloads: Flow<List<DownloadEntity>> = dao.getActiveDownloadsFlow()
    val completedDownloads: Flow<List<DownloadEntity>> = dao.getCompletedDownloadsFlow()

    suspend fun updateEngine(): Result<YoutubeDL.UpdateStatus> {
        return engine.updateEngine(context)
    }

    suspend fun executeDownload(
        videoUrl: String,
        outputDir: File,
        onProgress: (progress: Float, etaStr: String) -> Unit
    ): Result<Unit> {
        return engine.executeDownload(videoUrl, outputDir, onProgress)
    }

    fun enqueueDownload(
        videoId: String,
        title: String,
        author: String,
        thumbnailUrl: String,
        durationText: String,
        qualityOption: VideoFormatOption
    ) {
        engine.enqueueDownload(
            videoId = videoId,
            title = title,
            author = author,
            thumbnailUrl = thumbnailUrl,
            durationText = durationText,
            qualityOption = qualityOption
        )
    }

    fun pauseDownload(id: String) {
        engine.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        engine.resumeDownload(id)
    }

    fun cancelDownload(id: String) {
        engine.cancelDownload(id)
    }

    fun deleteDownload(id: String, deleteFromStorage: Boolean = true) {
        engine.deleteDownload(id, deleteFromStorage)
    }

    suspend fun deleteAllCompleted() {
        dao.deleteAllCompleted()
    }
}
