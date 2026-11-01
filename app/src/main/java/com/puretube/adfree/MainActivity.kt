package com.puretube.adfree

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import com.puretube.adfree.data.download.StreamResolver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.puretube.adfree.data.AdBlocker
import com.puretube.adfree.data.download.DownloadForegroundService
import com.puretube.adfree.ui.components.AdBlockStatsDialog
import com.puretube.adfree.ui.components.AppUpdateDialog
import com.puretube.adfree.ui.components.DefaultLinksGuideSheet
import com.puretube.adfree.ui.components.DownloadQualitySheet
import com.puretube.adfree.ui.components.FloatingDownloadButton
import com.puretube.adfree.ui.components.NoInternetScreen
import com.puretube.adfree.ui.components.PlaybackSpeedSheet
import com.puretube.adfree.ui.components.PrivacyPolicySheet
import com.puretube.adfree.ui.components.PureBottomBar
import com.puretube.adfree.ui.components.PureTopBar
import com.puretube.adfree.ui.components.PureWebView
import com.puretube.adfree.ui.components.SettingsSheet
import com.puretube.adfree.ui.components.WebViewHolder
import com.puretube.adfree.ui.screens.DownloadsScreen
import com.puretube.adfree.ui.theme.DarkSurface
import com.puretube.adfree.ui.theme.MyApplicationTheme
import com.puretube.adfree.ui.theme.PureBlack
import com.puretube.adfree.util.AppLogger
import com.puretube.adfree.util.DefaultAppHelper
import com.puretube.adfree.util.FeedbackHelper
import com.puretube.adfree.util.NetworkUtils
import com.puretube.adfree.viewmodel.PureTubeViewModel
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private val viewModel: PureTubeViewModel by viewModels()
    private val webViewHolder = WebViewHolder()

    private var customVideoView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    private var isInPipModeState by mutableStateOf(false)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i("MainActivity", "onCreate started")
        enableEdgeToEdge()

        fullscreenContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            visibility = View.GONE
        }

        syncDefaultLinkHandlerStatus()
        handleIncomingIntent(intent)

        if (!NetworkUtils.isNetworkAvailable(this)) {
            AppLogger.w("MainActivity", "Initial network check: offline")
            viewModel.setNetworkError(true, "No internet connection")
        } else {
            AppLogger.i("MainActivity", "Initial network check: connected")
        }
        setupNetworkMonitoring()
        viewModel.checkForUpdates(force = false)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val allDownloads by viewModel.allDownloads.collectAsState()
            val activeDownloadsCount by viewModel.activeDownloadsCount.collectAsState()

            var showStatsDialog by remember { mutableStateOf(false) }
            var showSpeedSheet by remember { mutableStateOf(false) }
            var showSettingsSheet by remember { mutableStateOf(false) }
            var showGuideSheet by remember { mutableStateOf(false) }
            var showPrivacyPolicySheet by remember { mutableStateOf(false) }

            val qualitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            // Back handler
            BackHandler(enabled = true) {
                if (customVideoView != null) {
                    hideCustomView()
                } else if (uiState.isDownloadsScreenOpen) {
                    viewModel.closeDownloadsScreen()
                } else if (uiState.isQualitySheetOpen) {
                    viewModel.closeQualitySheet()
                } else if (showStatsDialog) {
                    showStatsDialog = false
                } else if (showSpeedSheet) {
                    showSpeedSheet = false
                } else if (showGuideSheet) {
                    showGuideSheet = false
                } else if (showPrivacyPolicySheet) {
                    showPrivacyPolicySheet = false
                } else if (showSettingsSheet) {
                    showSettingsSheet = false
                } else if (webViewHolder.goBack()) {
                    // Handled webview go back
                    webViewHolder.webView?.postDelayed({
                        webViewHolder.webView?.url?.let { currentUrl ->
                            viewModel.updateUrl(currentUrl)
                        }
                    }, 150)
                } else {
                    finish()
                }
            }

            MyApplicationTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize().background(PureBlack)) {
                    if (uiState.isDownloadsScreenOpen && !isInPipModeState) {
                        // Fullscreen dedicated Downloads Manager Page
                        DownloadsScreen(
                            downloads = allDownloads,
                            onBack = { viewModel.closeDownloadsScreen() },
                            onPauseDownload = { viewModel.pauseDownload(it) },
                            onResumeDownload = { viewModel.resumeDownload(it) },
                            onCancelDownload = { viewModel.cancelDownload(it) },
                            onDeleteDownload = { id, deleteFromStorage -> viewModel.deleteDownload(id, deleteFromStorage) },
                            isAmoledMode = uiState.isAmoledMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Main Browser & YouTube Experience
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = if (uiState.isAmoledMode) PureBlack else DarkSurface,
                            topBar = {
                                if (!isInPipModeState) {
                                    Column(
                                        modifier = Modifier.windowInsetsPadding(
                                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                                        )
                                    ) {
                                        PureTopBar(
                                            currentTitle = uiState.pageTitle,
                                            currentUrl = uiState.currentUrl,
                                            isLoading = uiState.isLoading,
                                            progress = uiState.progress,
                                            totalAdsBlocked = uiState.totalAdsBlocked,
                                            isAdBlockEnabled = uiState.isAdBlockEnabled,
                                            activeDownloadsCount = activeDownloadsCount,
                                            onOpenDownloads = { viewModel.openDownloadsScreen() },
                                            onOpenStats = { showStatsDialog = true },
                                            onOpenSpeed = { showSpeedSheet = true },
                                            onOpenSettings = { showSettingsSheet = true },
                                            onEnterPiP = { enterPictureInPicture() },
                                            onRefresh = {
                                                viewModel.clearNetworkError()
                                                webViewHolder.reload()
                                            }
                                        )
                                    }
                                }
                            },
                            bottomBar = {
                                if (!isInPipModeState) {
                                    PureBottomBar(
                                        canGoBack = uiState.canGoBack,
                                        canGoForward = uiState.canGoForward,
                                        playbackSpeed = uiState.playbackSpeed,
                                        isAdBlockEnabled = uiState.isAdBlockEnabled,
                                        totalAdsBlocked = uiState.totalAdsBlocked,
                                        onGoBack = {
                                            webViewHolder.goBack()
                                            webViewHolder.webView?.postDelayed({
                                                webViewHolder.webView?.url?.let { currentUrl ->
                                                    viewModel.updateUrl(currentUrl)
                                                }
                                            }, 150)
                                        },
                                        onGoForward = { webViewHolder.goForward() },
                                        onGoHome = {
                                            viewModel.clearNetworkError()
                                            viewModel.updateUrl("https://m.youtube.com")
                                            webViewHolder.loadUrl("https://m.youtube.com")
                                        },
                                        onOpenSpeed = { showSpeedSheet = true },
                                        onOpenStats = { showStatsDialog = true }
                                    )
                                }
                            }
                        ) { paddingValues ->
                            // Continuous stable PureWebView mounted in single Composable hierarchy
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(if (isInPipModeState) PaddingValues(0.dp) else paddingValues)
                            ) {
                                PureWebView(
                                    url = uiState.currentUrl,
                                    isInPipMode = isInPipModeState,
                                    isAdBlockEnabled = uiState.isAdBlockEnabled,
                                    isDesktopMode = false,
                                    playbackSpeed = uiState.playbackSpeed,
                                    isAmoledMode = uiState.isAmoledMode,
                                    onUrlChanged = { viewModel.updateUrl(it) },
                                    onPageTitleChanged = { title, url -> viewModel.updatePageInfo(title, url) },
                                    onVideoPlayingStateChanged = { isPlaying, videoId, title, duration ->
                                        viewModel.updateVideoPlayingState(isPlaying, videoId, title, duration)
                                    },
                                    onLoadingChanged = { loading, progress -> viewModel.updateLoadingState(loading, progress) },
                                    onNavigationStateChanged = { canBack, canForward -> viewModel.updateNavigationState(canBack, canForward) },
                                    onAdBlocked = { source ->
                                        if (source.contains("Video") || source.contains("Skip") || source.contains("Overlay")) {
                                            viewModel.recordScriptAdBlocked(source)
                                        } else {
                                            viewModel.recordNetworkAdBlocked(source)
                                        }
                                    },
                                    onNetworkError = { isError, message ->
                                        viewModel.setNetworkError(isError, message)
                                    },
                                    onCustomViewShow = { view, callback -> showCustomView(view, callback) },
                                    onCustomViewHide = { hideCustomView() },
                                    webViewHolder = webViewHolder,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Custom No Internet UI replacement over default robotic error screen
                                if (uiState.isNetworkError && !isInPipModeState) {
                                    NoInternetScreen(
                                        onRetry = {
                                            viewModel.clearNetworkError()
                                            webViewHolder.reload()
                                        },
                                        isAmoledMode = uiState.isAmoledMode,
                                        errorMessage = uiState.networkErrorMessage,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Round download button with download icon that stays visible only when a video is open/playing
                                val currentUrl = uiState.currentUrl
                                val isHomeOrFeed = currentUrl.equals("https://m.youtube.com", ignoreCase = true) ||
                                        currentUrl.equals("https://m.youtube.com/", ignoreCase = true) ||
                                        currentUrl.equals("https://youtube.com", ignoreCase = true) ||
                                        currentUrl.equals("https://youtube.com/", ignoreCase = true) ||
                                        currentUrl.startsWith("https://m.youtube.com/?") ||
                                        currentUrl.startsWith("https://m.youtube.com/#") ||
                                        currentUrl.startsWith("https://m.youtube.com/feed/")

                                val isVideoActive = !isHomeOrFeed && (uiState.activeVideoId != null ||
                                        StreamResolver.extractVideoId(currentUrl) != null ||
                                        currentUrl.contains("watch?v=") ||
                                        currentUrl.contains("/shorts/"))

                                FloatingDownloadButton(
                                    visible = isVideoActive && !isInPipModeState && customVideoView == null && !uiState.isNetworkError,
                                    onClick = { viewModel.openQualitySheet() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 16.dp, bottom = 80.dp)
                                )
                            }
                        }
                    }

                    // Native HTML5 Fullscreen Video Container Overlay
                    if (!isInPipModeState) {
                        AndroidView(
                            factory = { fullscreenContainer!! },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Quality Selection Modal Bottom Sheet
                    if (uiState.isQualitySheetOpen && !isInPipModeState) {
                        DownloadQualitySheet(
                            sheetState = qualitySheetState,
                            videoMetadata = uiState.currentVideoMetadata,
                            isLoading = uiState.isLoadingDownloadFormats,
                            onDismiss = { viewModel.closeQualitySheet() },
                            onStartDownload = { formatOption ->
                                viewModel.startDownload(formatOption)
                                Toast.makeText(this@MainActivity, "Download started: ${formatOption.qualityLabel}", Toast.LENGTH_SHORT).show()
                            },
                            onOpenDownloadsPage = { viewModel.openDownloadsScreen() },
                            isAmoledMode = uiState.isAmoledMode
                        )
                    }

                    // Sheets / Dialogs
                    if (showStatsDialog && !isInPipModeState) {
                        AdBlockStatsDialog(
                            isAdBlockEnabled = uiState.isAdBlockEnabled,
                            totalBlocked = uiState.totalAdsBlocked,
                            adsSkipped = uiState.adsSkipped,
                            trackersBlocked = uiState.trackersBlocked,
                            blockedHistory = uiState.blockedHistory,
                            onToggleAdBlock = { viewModel.toggleAdBlock() },
                            onResetStats = { viewModel.resetStats() },
                            onDismiss = { showStatsDialog = false }
                        )
                    }

                    if (showSpeedSheet && !isInPipModeState) {
                        PlaybackSpeedSheet(
                            currentSpeed = uiState.playbackSpeed,
                            onSpeedSelected = { speed -> viewModel.setPlaybackSpeed(speed) },
                            onDismiss = { showSpeedSheet = false }
                        )
                    }

                    if (showSettingsSheet && !isInPipModeState) {
                        SettingsSheet(
                            isAdBlockEnabled = uiState.isAdBlockEnabled,
                            isAmoledMode = uiState.isAmoledMode,
                            isDefaultLinkHandler = uiState.isDefaultLinkHandler,
                            isDownloadNotificationEnabled = uiState.isDownloadNotificationEnabled,
                            onToggleAdBlock = { viewModel.toggleAdBlock() },
                            onToggleAmoledMode = { viewModel.toggleAmoledMode() },
                            onToggleDownloadNotifications = { viewModel.toggleDownloadNotifications() },
                            onConfigureDefaultLinks = {
                                DefaultAppHelper.openDefaultAppSettings(this@MainActivity)
                            },
                            onOpenOfficialYouTubeSettings = {
                                DefaultAppHelper.openOfficialYouTubeAppSettings(this@MainActivity)
                            },
                            onOpenGuide = {
                                showGuideSheet = true
                            },
                            onClearCache = {
                                webViewHolder.clearCache()
                                Toast.makeText(this@MainActivity, "Cache & Cookies Cleared", Toast.LENGTH_SHORT).show()
                            },
                            onOpenFeedback = {
                                FeedbackHelper.sendFeedback(this@MainActivity)
                            },
                            onOpenPrivacyPolicy = {
                                showPrivacyPolicySheet = true
                            },
                            onCheckForUpdates = {
                                Toast.makeText(this@MainActivity, "Checking for updates...", Toast.LENGTH_SHORT).show()
                                viewModel.checkForUpdates(force = true) { hasUpdate ->
                                    if (!hasUpdate) {
                                        Toast.makeText(this@MainActivity, "PureTube is up to date!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDismiss = { showSettingsSheet = false }
                        )
                    }

                    if (showPrivacyPolicySheet && !isInPipModeState) {
                        PrivacyPolicySheet(
                            onDismiss = { showPrivacyPolicySheet = false },
                            isAmoledMode = uiState.isAmoledMode
                        )
                    }

                    if (showGuideSheet && !isInPipModeState) {
                        DefaultLinksGuideSheet(
                            isDefaultLinkHandler = uiState.isDefaultLinkHandler,
                            onConfigureDefaultLinks = {
                                DefaultAppHelper.openDefaultAppSettings(this@MainActivity)
                            },
                            onOpenOfficialYouTubeSettings = {
                                DefaultAppHelper.openOfficialYouTubeAppSettings(this@MainActivity)
                            },
                            onDismiss = { showGuideSheet = false }
                        )
                    }

                    // In-App Update Dialog
                    if (uiState.isUpdateDialogOpen && uiState.appUpdateInfo != null && !isInPipModeState) {
                        val packageInfo = try {
                            packageManager.getPackageInfo(packageName, 0)
                        } catch (e: Exception) { null }
                        val currentVersionName = packageInfo?.versionName ?: "1.0"

                        AppUpdateDialog(
                            updateInfo = uiState.appUpdateInfo!!,
                            currentVersionName = currentVersionName,
                            isDownloading = uiState.isDownloadingUpdate,
                            downloadProgress = uiState.updateDownloadProgress,
                            downloadedBytes = uiState.updateDownloadedBytes,
                            totalBytes = uiState.updateTotalBytes,
                            downloadError = uiState.updateDownloadError,
                            needsInstallPermission = uiState.updateNeedsInstallPermission,
                            onStartDownload = { viewModel.startUpdateDownload(this@MainActivity) },
                            onCancelDownload = { viewModel.cancelUpdateDownload() },
                            onGrantPermissionAndInstall = { viewModel.grantPermissionAndInstall(this@MainActivity) },
                            onDismiss = { viewModel.dismissUpdateDialog() },
                            isAmoledMode = uiState.isAmoledMode
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (!inPip && !isInPipModeState) {
            // Keep background media playing continuously
        }
    }

    override fun onResume() {
        super.onResume()
        syncDefaultLinkHandlerStatus()
        webViewHolder.resume()
        viewModel.checkAndInstallIfPermissionGranted(this)
    }

    override fun onDestroy() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {}
        webViewHolder.destroy()
        super.onDestroy()
    }

    private fun setupNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(ConnectivityManager::class.java)
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread {
                        if (viewModel.uiState.value.isNetworkError) {
                            viewModel.clearNetworkError()
                            webViewHolder.reload()
                        }
                    }
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            networkCallback?.let {
                connectivityManager?.registerNetworkCallback(request, it)
            }
        } catch (e: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun syncDefaultLinkHandlerStatus() {
        val isDefault = DefaultAppHelper.isDefaultYouTubeHandler(this)
        viewModel.setDefaultLinkHandlerState(isDefault)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(DownloadForegroundService.EXTRA_OPEN_DOWNLOADS, false)) {
            viewModel.openDownloadsScreen()
        }

        var urlString: String? = intent.data?.toString()

        if (urlString == null && intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val regex = "(https?://[\\w-]+(\\.[\\w-]+)+(/[^\\s]*)?)".toRegex()
            val match = regex.find(sharedText)
            if (match != null) {
                urlString = match.value
            }
        }

        if (urlString != null && (urlString.contains("youtube.com") || urlString.contains("youtu.be"))) {
            if (urlString.contains("youtu.be/")) {
                val videoId = urlString.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                val query = if (urlString.contains("?")) "?" + urlString.substringAfter("?") else ""
                if (videoId.isNotEmpty()) {
                    urlString = "https://m.youtube.com/watch?v=$videoId" + (if (query.isNotEmpty() && !query.contains("v=")) query.replace("?", "&") else "")
                }
            } else if (urlString.startsWith("http://")) {
                urlString = urlString.replaceFirst("http://", "https://")
            }
            viewModel.updateUrl(urlString)
            webViewHolder.loadUrl(urlString)
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                webViewHolder.webView?.evaluateJavascript(AdBlocker.PIP_ENABLE_SCRIPT, null)

                val aspectRatio = Rational(16, 9)
                val paramsBuilder = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    paramsBuilder.setSeamlessResizeEnabled(true)
                }

                enterPictureInPictureMode(paramsBuilder.build())
            } catch (e: Exception) {
                Toast.makeText(this, "Picture-in-Picture mode not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
        webViewHolder.webView?.let { wv ->
            if (isInPictureInPictureMode) {
                wv.evaluateJavascript(AdBlocker.PIP_ENABLE_SCRIPT, null)
            } else {
                wv.evaluateJavascript(AdBlocker.PIP_DISABLE_SCRIPT, null)
            }
        }
    }

    private fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customVideoView != null) {
            callback.onCustomViewHidden()
            return
        }

        customVideoView = view
        customViewCallback = callback
        fullscreenContainer?.addView(view)
        fullscreenContainer?.visibility = View.VISIBLE

        viewModel.setFullscreen(true)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun hideCustomView() {
        if (customVideoView == null) return

        customVideoView?.let { fullscreenContainer?.removeView(it) }
        fullscreenContainer?.visibility = View.GONE
        customViewCallback?.onCustomViewHidden()

        customVideoView = null
        customViewCallback = null

        viewModel.setFullscreen(false)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
