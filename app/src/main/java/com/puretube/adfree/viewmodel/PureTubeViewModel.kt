package com.puretube.adfree.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puretube.adfree.data.download.DownloadEntity
import com.puretube.adfree.data.download.DownloadForegroundService
import com.puretube.adfree.data.download.DownloadRepository
import com.puretube.adfree.data.download.StreamResolver
import com.puretube.adfree.data.download.VideoFormatOption
import com.puretube.adfree.data.download.VideoMetadata
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.content.Context
import com.puretube.adfree.data.update.AppUpdateChecker
import com.puretube.adfree.data.update.AppUpdateInfo
import com.puretube.adfree.data.update.AppUpdateResult
import com.puretube.adfree.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class BlockedEvent(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    val timeFormatted: String = AppLogger.formatTimeOnly()
)

data class BrowserUiState(
    val currentUrl: String = "https://m.youtube.com",
    val pageTitle: String = "YouTube",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isFullscreen: Boolean = false,
    val isAdBlockEnabled: Boolean = true,
    val isDefaultLinkHandler: Boolean = false,
    val isAmoledMode: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isNetworkError: Boolean = false,
    val networkErrorMessage: String? = null,
    val totalAdsBlocked: Int = 0,
    val adsSkipped: Int = 0,
    val trackersBlocked: Int = 0,
    val blockedHistory: List<BlockedEvent> = emptyList(),
    // Video Playback & Downloading State
    val isVideoPlaying: Boolean = false,
    val activeVideoId: String? = null,
    val activeVideoTitle: String? = null,
    val activeVideoDuration: Long = 0L,
    val isLoadingDownloadFormats: Boolean = false,
    val currentVideoMetadata: VideoMetadata? = null,
    val isDownloadsScreenOpen: Boolean = false,
    val isQualitySheetOpen: Boolean = false,
    val isEngineUpdating: Boolean = false,
    val engineUpdateStatus: String? = null,
    // App Update State
    val appUpdateInfo: com.puretube.adfree.data.update.AppUpdateInfo? = null,
    val isUpdateDialogOpen: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Int = 0,
    val updateDownloadedBytes: Long = 0L,
    val updateTotalBytes: Long = 0L,
    val updateDownloadError: String? = null,
    val updateNeedsInstallPermission: Boolean = false,
    // Notification & Background Preferences
    val isDownloadNotificationEnabled: Boolean = true
)

class PureTubeViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadRepository = DownloadRepository(application)

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    // Real-time downloads from Room
    val allDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeDownloadsCount: StateFlow<Int> = downloadRepository.activeDownloads
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    init {
        // Load persistent settings
        val prefs = getApplication<Application>().getSharedPreferences(
            DownloadForegroundService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val notifEnabled = prefs.getBoolean(DownloadForegroundService.PREF_DOWNLOAD_NOTIFICATIONS, true)
        _uiState.update { it.copy(isDownloadNotificationEnabled = notifEnabled) }

        // Auto-heal yt-dlp binary on startup to stay synchronized with YouTube internal changes
        refreshEngine()
    }

    /**
     * Triggers asynchronous OTA update for yt-dlp decryption signatures
     */
    fun refreshEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isEngineUpdating = true) }
            val result = downloadRepository.updateEngine()
            result.fold(
                onSuccess = { status ->
                    Log.d("PureTubeViewModel", "Engine update completed: $status")
                    _uiState.update {
                        it.copy(
                            isEngineUpdating = false,
                            engineUpdateStatus = "Engine updated: $status"
                        )
                    }
                },
                onFailure = { error ->
                    Log.w("PureTubeViewModel", "Engine update warning: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isEngineUpdating = false,
                            engineUpdateStatus = "Engine update check complete"
                        )
                    }
                }
            )
        }
    }

    fun updateUrl(url: String) {
        if (url.isBlank()) return
        val videoId = StreamResolver.extractVideoId(url)
        val isVideoUrl = videoId != null || url.contains("/watch") || url.contains("/shorts/")
        val isHomeOrFeed = url.equals("https://m.youtube.com", ignoreCase = true) ||
                url.equals("https://m.youtube.com/", ignoreCase = true) ||
                url.equals("https://youtube.com", ignoreCase = true) ||
                url.equals("https://youtube.com/", ignoreCase = true) ||
                url.startsWith("https://m.youtube.com/?") ||
                url.startsWith("https://m.youtube.com/#") ||
                url.startsWith("https://m.youtube.com/feed/")

        _uiState.update {
            it.copy(
                currentUrl = url,
                activeVideoId = if (isVideoUrl) (videoId ?: it.activeVideoId) else if (isHomeOrFeed) null else it.activeVideoId,
                activeVideoTitle = if (isVideoUrl) it.activeVideoTitle else if (isHomeOrFeed) null else it.activeVideoTitle,
                isVideoPlaying = if (isHomeOrFeed) false else it.isVideoPlaying
            )
        }
    }

    fun updatePageInfo(title: String, url: String) {
        val effectiveUrl = if (url.isNotBlank()) url else _uiState.value.currentUrl
        val videoId = StreamResolver.extractVideoId(effectiveUrl)
        val isVideoUrl = videoId != null || effectiveUrl.contains("/watch") || effectiveUrl.contains("/shorts/")
        val cleanTitle = title.replace(" - YouTube", "").trim()
        val isHomeOrFeed = effectiveUrl.equals("https://m.youtube.com", ignoreCase = true) ||
                effectiveUrl.equals("https://m.youtube.com/", ignoreCase = true) ||
                effectiveUrl.equals("https://youtube.com", ignoreCase = true) ||
                effectiveUrl.equals("https://youtube.com/", ignoreCase = true) ||
                effectiveUrl.startsWith("https://m.youtube.com/?") ||
                effectiveUrl.startsWith("https://m.youtube.com/#") ||
                effectiveUrl.startsWith("https://m.youtube.com/feed/")

        _uiState.update {
            it.copy(
                pageTitle = if (cleanTitle.isNotBlank()) cleanTitle else "YouTube",
                currentUrl = effectiveUrl,
                activeVideoId = if (isVideoUrl) (videoId ?: it.activeVideoId) else if (isHomeOrFeed) null else it.activeVideoId,
                activeVideoTitle = if (isVideoUrl && cleanTitle.isNotBlank() && cleanTitle != "YouTube") cleanTitle else if (isHomeOrFeed) null else it.activeVideoTitle
            )
        }
    }

    fun updateVideoPlayingState(isPlaying: Boolean, videoId: String?, title: String?, duration: Long) {
        val url = _uiState.value.currentUrl
        val urlVideoId = StreamResolver.extractVideoId(url)
        val resolvedVideoId = (videoId?.ifBlank { null }) ?: urlVideoId ?: _uiState.value.activeVideoId
        val isVideoUrl = resolvedVideoId != null || url.contains("/watch") || url.contains("/shorts/") || !videoId.isNullOrBlank()

        _uiState.update {
            if (!isVideoUrl) {
                it.copy(
                    isVideoPlaying = false,
                    activeVideoId = null,
                    activeVideoTitle = null,
                    activeVideoDuration = 0
                )
            } else {
                it.copy(
                    isVideoPlaying = isPlaying,
                    activeVideoId = resolvedVideoId ?: it.activeVideoId,
                    activeVideoTitle = if (!title.isNullOrBlank() && title != "YouTube") title.replace(" - YouTube", "").trim() else it.activeVideoTitle,
                    activeVideoDuration = if (duration > 0) duration else it.activeVideoDuration
                )
            }
        }
    }

    fun openQualitySheet() {
        val videoId = _uiState.value.activeVideoId
            ?: StreamResolver.extractVideoId(_uiState.value.currentUrl)

        if (videoId != null) {
            val title = _uiState.value.activeVideoTitle ?: _uiState.value.pageTitle
            val knownDurationSec = (_uiState.value.activeVideoDuration / 1000L).coerceAtLeast(0L)

            // 1. Instant Cache Check: If real metadata is already cached, show it immediately
            val cached = StreamResolver.getCachedMetadata(videoId)
            if (cached != null && cached.durationSeconds > 0) {
                _uiState.update {
                    it.copy(
                        activeVideoId = videoId,
                        currentVideoMetadata = cached,
                        isQualitySheetOpen = true,
                        isLoadingDownloadFormats = false
                    )
                }
                return
            }

            // 2. Open sheet in loading state while extracting real video sizes and formats
            val optimistic = StreamResolver.getOptimisticMetadata(videoId, title, knownDurationSec)
            _uiState.update {
                it.copy(
                    activeVideoId = videoId,
                    currentVideoMetadata = optimistic,
                    isQualitySheetOpen = true,
                    isLoadingDownloadFormats = true
                )
            }

            // Asynchronously resolve detailed metadata with exact format sizes
            fetchVideoMetadata(videoId, title, knownDurationSec)
        }
    }

    fun closeQualitySheet() {
        _uiState.update { it.copy(isQualitySheetOpen = false) }
    }

    fun openDownloadsScreen() {
        _uiState.update { it.copy(isDownloadsScreenOpen = true, isQualitySheetOpen = false) }
    }

    fun closeDownloadsScreen() {
        _uiState.update { it.copy(isDownloadsScreenOpen = false) }
    }

    private fun fetchVideoMetadata(videoId: String, title: String, knownDurationSec: Long = 0L) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val metadata = StreamResolver.resolveVideo(videoId, title, knownDurationSec)
                _uiState.update {
                    if (it.activeVideoId == videoId || it.currentVideoMetadata?.videoId == videoId) {
                        it.copy(
                            currentVideoMetadata = metadata,
                            isLoadingDownloadFormats = false
                        )
                    } else it
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingDownloadFormats = false)
                }
            }
        }
    }

    fun startDownload(qualityOption: VideoFormatOption) {
        val metadata = _uiState.value.currentVideoMetadata
        val videoId = metadata?.videoId ?: _uiState.value.activeVideoId ?: return
        val title = metadata?.title ?: _uiState.value.activeVideoTitle ?: "YouTube Video"
        val author = metadata?.author ?: "YouTube"
        val thumbnail = metadata?.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val durationText = metadata?.durationText ?: ""

        downloadRepository.enqueueDownload(
            videoId = videoId,
            title = title,
            author = author,
            thumbnailUrl = thumbnail,
            durationText = durationText,
            qualityOption = qualityOption
        )
    }

    fun pauseDownload(id: String) {
        downloadRepository.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        downloadRepository.resumeDownload(id)
    }

    fun cancelDownload(id: String) {
        downloadRepository.cancelDownload(id)
    }

    fun deleteDownload(id: String, deleteFromStorage: Boolean = true) {
        downloadRepository.deleteDownload(id, deleteFromStorage)
    }

    fun updateLoadingState(loading: Boolean, progress: Int = 0) {
        _uiState.update {
            if (it.isLoading == loading && it.progress == progress) it
            else it.copy(isLoading = loading, progress = progress)
        }
    }

    fun updateNavigationState(canBack: Boolean, canForward: Boolean) {
        _uiState.update {
            if (it.canGoBack == canBack && it.canGoForward == canForward) it
            else it.copy(canGoBack = canBack, canGoForward = canForward)
        }
    }

    fun setDefaultLinkHandlerState(isDefault: Boolean) {
        _uiState.update {
            if (it.isDefaultLinkHandler == isDefault) it
            else it.copy(isDefaultLinkHandler = isDefault)
        }
    }

    fun recordNetworkAdBlocked(hostOrUrl: String) {
        if (!_uiState.value.isAdBlockEnabled) return
        AppLogger.d("AdBlocker", "Blocked Network Request / Tracker: $hostOrUrl")
        val event = BlockedEvent(
            source = hostOrUrl,
            timeFormatted = AppLogger.formatTimeOnly()
        )
        _uiState.update {
            val updatedList = (listOf(event) + it.blockedHistory).take(30)
            it.copy(
                totalAdsBlocked = it.totalAdsBlocked + 1,
                trackersBlocked = it.trackersBlocked + 1,
                blockedHistory = updatedList
            )
        }
    }

    fun recordScriptAdBlocked(type: String) {
        if (!_uiState.value.isAdBlockEnabled) return
        AppLogger.d("AdBlocker", "Blocked Script Ad / Skipped: $type")
        val event = BlockedEvent(
            source = type,
            timeFormatted = AppLogger.formatTimeOnly()
        )
        _uiState.update {
            val updatedList = (listOf(event) + it.blockedHistory).take(30)
            it.copy(
                totalAdsBlocked = it.totalAdsBlocked + 1,
                adsSkipped = it.adsSkipped + 1,
                blockedHistory = updatedList
            )
        }
    }

    fun toggleAdBlock() {
        val newState = !_uiState.value.isAdBlockEnabled
        AppLogger.i("Settings", "AdBlock toggled: $newState")
        _uiState.update { it.copy(isAdBlockEnabled = newState) }
    }

    fun toggleAmoledMode() {
        val newState = !_uiState.value.isAmoledMode
        AppLogger.i("Settings", "AmoledMode toggled: $newState")
        _uiState.update { it.copy(isAmoledMode = newState) }
    }

    fun toggleDownloadNotifications() {
        val newState = !_uiState.value.isDownloadNotificationEnabled
        val prefs = getApplication<Application>().getSharedPreferences(
            DownloadForegroundService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        prefs.edit().putBoolean(DownloadForegroundService.PREF_DOWNLOAD_NOTIFICATIONS, newState).apply()
        AppLogger.i("Settings", "Download notifications toggled: $newState")
        _uiState.update { it.copy(isDownloadNotificationEnabled = newState) }
    }

    fun setPlaybackSpeed(speed: Float) {
        AppLogger.i("Playback", "Playback speed changed to: ${speed}x")
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun setNetworkError(isError: Boolean, message: String? = null) {
        if (isError) {
            AppLogger.w("Network", "Network error encountered: $message")
        }
        _uiState.update {
            if (it.isNetworkError == isError && it.networkErrorMessage == message) it
            else it.copy(isNetworkError = isError, networkErrorMessage = message)
        }
    }

    fun clearNetworkError() {
        AppLogger.i("Network", "Network error cleared")
        _uiState.update {
            if (!it.isNetworkError && it.networkErrorMessage == null) it
            else it.copy(isNetworkError = false, networkErrorMessage = null)
        }
    }

    fun resetStats() {
        AppLogger.i("AdBlocker", "Ad blocking stats reset by user.")
        _uiState.update {
            it.copy(
                totalAdsBlocked = 0,
                adsSkipped = 0,
                trackersBlocked = 0,
                blockedHistory = emptyList()
            )
        }
    }

    // --- App Update Management ---
    private var downloadedApkFile: File? = null
    private var updateDownloadJob: Job? = null

    fun checkForUpdates(force: Boolean = false, onCheckCompleted: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val packageInfo = try {
                val pm = app.packageManager
                pm.getPackageInfo(app.packageName, 0)
            } catch (e: Exception) {
                null
            }

            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode?.toInt() ?: 1
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode ?: 1
            }
            val currentVersionName = packageInfo?.versionName ?: "1.0"

            val result = AppUpdateChecker.fetchUpdateInfo(
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                force = force
            )

            when (result) {
                is AppUpdateResult.UpdateAvailable -> {
                    val existingApk = AppUpdateChecker.getDownloadedUpdateApk(
                        app,
                        result.updateInfo.latestVersionCode
                    )
                    val isAlreadyDownloaded = existingApk != null
                    val canInstall = AppUpdateChecker.canInstallPackages(app)

                    if (isAlreadyDownloaded) {
                        downloadedApkFile = existingApk
                    }

                    _uiState.update {
                        it.copy(
                            appUpdateInfo = result.updateInfo,
                            isUpdateDialogOpen = true,
                            updateDownloadError = null,
                            isDownloadingUpdate = false,
                            updateDownloadProgress = if (isAlreadyDownloaded) 100 else 0,
                            updateDownloadedBytes = existingApk?.length() ?: 0L,
                            updateTotalBytes = existingApk?.length() ?: 0L,
                            updateNeedsInstallPermission = isAlreadyDownloaded && !canInstall
                        )
                    }
                    onCheckCompleted?.invoke(true)
                }
                is AppUpdateResult.NoUpdateAvailable -> {
                    onCheckCompleted?.invoke(false)
                }
                is AppUpdateResult.Error -> {
                    AppLogger.w("PureTubeViewModel", "App update check error: ${result.message}")
                    onCheckCompleted?.invoke(false)
                }
            }
        }
    }

    fun startUpdateDownload(context: Context) {
        val updateInfo = _uiState.value.appUpdateInfo ?: return
        if (_uiState.value.isDownloadingUpdate) return

        // Check if already downloaded on disk and valid
        val existingApk = downloadedApkFile?.takeIf { it.exists() }
            ?: AppUpdateChecker.getDownloadedUpdateApk(context, updateInfo.latestVersionCode)

        if (existingApk != null) {
            downloadedApkFile = existingApk
            _uiState.update {
                it.copy(
                    isDownloadingUpdate = false,
                    updateDownloadProgress = 100,
                    updateDownloadedBytes = existingApk.length(),
                    updateTotalBytes = existingApk.length(),
                    updateDownloadError = null
                )
            }

            if (!AppUpdateChecker.canInstallPackages(context)) {
                _uiState.update { it.copy(updateNeedsInstallPermission = true) }
            } else {
                val launched = AppUpdateChecker.launchApkInstaller(context, existingApk)
                if (launched) {
                    _uiState.update { it.copy(isUpdateDialogOpen = false) }
                }
            }
            return
        }

        updateDownloadJob?.cancel()
        updateDownloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingUpdate = true,
                    updateDownloadProgress = 0,
                    updateDownloadedBytes = 0L,
                    updateTotalBytes = 0L,
                    updateDownloadError = null,
                    updateNeedsInstallPermission = false
                )
            }

            val result = AppUpdateChecker.downloadApk(
                context = context,
                downloadUrl = updateInfo.downloadUrl,
                targetVersionCode = updateInfo.latestVersionCode,
                onProgress = { downloaded, total, percent ->
                    _uiState.update {
                        it.copy(
                            updateDownloadedBytes = downloaded,
                            updateTotalBytes = total,
                            updateDownloadProgress = percent
                        )
                    }
                }
            )

            result.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadProgress = 100
                    )
                }

                if (!AppUpdateChecker.canInstallPackages(context)) {
                    _uiState.update { it.copy(updateNeedsInstallPermission = true) }
                } else {
                    val launched = AppUpdateChecker.launchApkInstaller(context, apkFile)
                    if (launched) {
                        _uiState.update { it.copy(isUpdateDialogOpen = false) }
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadError = err.message ?: "Failed to download update APK"
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        _uiState.update {
            it.copy(
                isDownloadingUpdate = false,
                updateDownloadProgress = 0,
                updateDownloadError = null
            )
        }
    }

    fun grantPermissionAndInstall(context: Context) {
        val updateInfo = _uiState.value.appUpdateInfo
        val apk = downloadedApkFile?.takeIf { it.exists() }
            ?: (updateInfo?.latestVersionCode?.let { AppUpdateChecker.getDownloadedUpdateApk(context, it) })

        if (apk != null && apk.exists()) {
            downloadedApkFile = apk
            if (!AppUpdateChecker.canInstallPackages(context)) {
                AppUpdateChecker.openInstallPermissionSettings(context)
            } else {
                AppUpdateChecker.launchApkInstaller(context, apk)
                _uiState.update {
                    it.copy(
                        isUpdateDialogOpen = false,
                        updateNeedsInstallPermission = false
                    )
                }
            }
        } else {
            startUpdateDownload(context)
        }
    }

    fun checkAndInstallIfPermissionGranted(context: Context) {
        val updateInfo = _uiState.value.appUpdateInfo
        val apk = downloadedApkFile?.takeIf { it.exists() }
            ?: (updateInfo?.latestVersionCode?.let { AppUpdateChecker.getDownloadedUpdateApk(context, it) })

        if (apk != null && apk.exists()) {
            downloadedApkFile = apk
            if (AppUpdateChecker.canInstallPackages(context)) {
                if (_uiState.value.updateNeedsInstallPermission) {
                    _uiState.update { it.copy(updateNeedsInstallPermission = false, isUpdateDialogOpen = false) }
                    AppUpdateChecker.launchApkInstaller(context, apk)
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        if (_uiState.value.appUpdateInfo?.isMandatory != true && !_uiState.value.isDownloadingUpdate) {
            _uiState.update { it.copy(isUpdateDialogOpen = false) }
        }
    }
}
