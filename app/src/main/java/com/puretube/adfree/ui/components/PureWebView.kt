package com.puretube.adfree.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.puretube.adfree.data.AdBlocker
import com.puretube.adfree.ui.theme.DarkSurface
import com.puretube.adfree.ui.theme.PureBlack

import com.puretube.adfree.util.AppLogger

class PureTubeBridge(
    private val onAdBlockedCallback: (String) -> Unit,
    private val onVideoInfoCallback: (String, String) -> Unit,
    private val onVideoPlayingStateCallback: (Boolean, String?, String?, Long) -> Unit
) {
    @JavascriptInterface
    fun onAdBlocked(type: String?) {
        try {
            val safeType = type ?: "Ad Blocked"
            onAdBlockedCallback(safeType)
        } catch (e: Throwable) {
            AppLogger.e("PureTubeBridge", "Error in onAdBlocked JS interface: ${e.message}", e)
        }
    }

    @JavascriptInterface
    fun onVideoInfo(title: String?, url: String?) {
        try {
            val safeTitle = title ?: "YouTube"
            val safeUrl = url ?: ""
            AppLogger.d("PureTubeBridge", "JS onVideoInfo: $safeTitle ($safeUrl)")
            onVideoInfoCallback(safeTitle, safeUrl)
        } catch (e: Throwable) {
            AppLogger.e("PureTubeBridge", "Error in onVideoInfo JS interface: ${e.message}", e)
        }
    }

    @JavascriptInterface
    fun onVideoPlayingState(isPlaying: Boolean, videoId: String?, title: String?, duration: Long) {
        try {
            onVideoPlayingStateCallback(isPlaying, videoId, title, duration)
        } catch (e: Throwable) {
            AppLogger.e("PureTubeBridge", "Error in onVideoPlayingState JS interface: ${e.message}", e)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PureWebView(
    url: String,
    isInPipMode: Boolean,
    isAdBlockEnabled: Boolean,
    isDesktopMode: Boolean = false,
    playbackSpeed: Float,
    isAmoledMode: Boolean,
    onUrlChanged: (String) -> Unit,
    onPageTitleChanged: (String, String) -> Unit,
    onVideoPlayingStateChanged: (Boolean, String?, String?, Long) -> Unit,
    onLoadingChanged: (Boolean, Int) -> Unit,
    onNavigationStateChanged: (Boolean, Boolean) -> Unit,
    onAdBlocked: (String) -> Unit,
    onNetworkError: (Boolean, String?) -> Unit,
    onCustomViewShow: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onCustomViewHide: () -> Unit,
    webViewHolder: WebViewHolder,
    modifier: Modifier = Modifier
) {
    val mobileUserAgent = remember {
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    // Apply speed changes via script
    LaunchedEffect(playbackSpeed) {
        try {
            webViewHolder.webView?.evaluateJavascript(AdBlocker.setPlaybackSpeedScript(playbackSpeed), null)
        } catch (e: Exception) {}
    }

    // Handle PiP mode styling & video playback smoothly
    LaunchedEffect(isInPipMode) {
        webViewHolder.webView?.let { wv ->
            try {
                if (isInPipMode) {
                    wv.evaluateJavascript(AdBlocker.PIP_ENABLE_SCRIPT, null)
                } else {
                    wv.evaluateJavascript(AdBlocker.PIP_DISABLE_SCRIPT, null)
                }
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmoledMode) PureBlack else DarkSurface)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Reuse existing WebView if already created to prevent reloads
                webViewHolder.webView ?: WebView(ctx).apply {
                    webViewHolder.webView = this
                    setBackgroundColor(AndroidColor.BLACK)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    CookieManager.getInstance().let { cookieManager ->
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    }

                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        allowContentAccess = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = mobileUserAgent
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    addJavascriptInterface(
                        PureTubeBridge(
                            onAdBlockedCallback = { type ->
                                post { onAdBlocked(type) }
                            },
                            onVideoInfoCallback = { title, videoUrl ->
                                post { onPageTitleChanged(title, videoUrl) }
                            },
                            onVideoPlayingStateCallback = { isPlaying, videoId, title, duration ->
                                post { onVideoPlayingStateChanged(isPlaying, videoId, title, duration) }
                            }
                        ),
                        "PureTubeBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return false
                            return handleUrlNavigation(view, context, uri)
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?
                        ): Boolean {
                            val uri = url?.let { Uri.parse(it) } ?: return false
                            return handleUrlNavigation(view, context, uri)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val requestUrl = request?.url?.toString() ?: return null
                            if (isAdBlockEnabled && AdBlocker.isAdUrl(requestUrl)) {
                                post { onAdBlocked(request.url.host ?: "Ad Tracker") }
                                return AdBlocker.createEmptyResource()
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            onLoadingChanged(true, 10)
                            url?.let { onUrlChanged(it) }
                            onNavigationStateChanged(canGoBack(), canGoForward())
                            try {
                                view?.evaluateJavascript(AdBlocker.BACKGROUND_PLAY_SCRIPT, null)
                            } catch (e: Exception) {}
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            onLoadingChanged(false, 100)
                            onNavigationStateChanged(canGoBack(), canGoForward())

                            // Inject background play, ad blocking script and speed
                            try {
                                view?.evaluateJavascript(AdBlocker.BACKGROUND_PLAY_SCRIPT, null)
                                if (isAdBlockEnabled) {
                                    view?.evaluateJavascript(AdBlocker.AD_SKIP_SCRIPT, null)
                                }
                                view?.evaluateJavascript(AdBlocker.EXTRACT_VIDEO_INFO_SCRIPT, null)
                                view?.evaluateJavascript(AdBlocker.setPlaybackSpeedScript(playbackSpeed), null)
                                if (isInPipMode) {
                                    view?.evaluateJavascript(AdBlocker.PIP_ENABLE_SCRIPT, null)
                                }
                            } catch (e: Exception) {}
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            url?.let { onUrlChanged(it) }
                            onNavigationStateChanged(canGoBack(), canGoForward())
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                val errorDesc = error?.description?.toString() ?: "No internet connection"
                                onNetworkError(true, errorDesc)
                            }
                        }

                        @Suppress("DEPRECATION")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            onNetworkError(true, description ?: "No internet connection")
                        }

                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            view?.destroy()
                            webViewHolder.webView = null
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            onLoadingChanged(newProgress < 100, newProgress)
                            onNavigationStateChanged(canGoBack(), canGoForward())
                            if (newProgress > 60 && isAdBlockEnabled) {
                                try {
                                    view?.evaluateJavascript(AdBlocker.AD_SKIP_SCRIPT, null)
                                } catch (e: Exception) {}
                            }
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            val effectiveUrl = view?.url ?: url
                            title?.let { onPageTitleChanged(it, effectiveUrl) }
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view != null && callback != null) {
                                onCustomViewShow(view, callback)
                            }
                        }

                        override fun onHideCustomView() {
                            onCustomViewHide()
                        }
                    }

                    loadUrl(url)
                }
            },
            update = {
                // Stable instance
            }
        )
    }
}

class WebViewHolder {
    var webView: WebView? = null

    fun loadUrl(url: String) {
        try {
            webView?.loadUrl(url)
        } catch (e: Exception) {}
    }

    fun goBack(): Boolean {
        return try {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun goForward(): Boolean {
        return try {
            if (webView?.canGoForward() == true) {
                webView?.goForward()
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun reload() {
        try {
            webView?.reload()
        } catch (e: Exception) {}
    }

    fun clearCache() {
        try {
            webView?.clearCache(true)
            webView?.clearHistory()
        } catch (e: Exception) {}
    }

    fun pause() {
        try {
            webView?.onPause()
        } catch (e: Exception) {}
    }

    fun resume() {
        try {
            webView?.onResume()
        } catch (e: Exception) {}
    }

    fun destroy() {
        try {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        } catch (e: Exception) {}
    }
}

/**
 * Intercepts URLs clicked inside WebView:
 * - Preserves internal YouTube playback, channels, playlists, search, and Google Sign-in inside PureTube
 * - Directs external links (social media, patreon, discord, etc.) and external /redirect URLs to default system browser
 */
private fun handleUrlNavigation(view: WebView?, context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    val host = uri.host?.lowercase() ?: ""
    val path = uri.path?.lowercase() ?: ""

    // 1. Handle intent:// schemes
    if (scheme == "intent") {
        return try {
            val parsedIntent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
            if (!fallbackUrl.isNullOrBlank()) {
                val fallbackUri = Uri.parse(fallbackUrl)
                if (isInternalYouTubeOrGoogleUrl(fallbackUri)) {
                    view?.loadUrl(fallbackUrl)
                    return true
                } else {
                    openInExternalBrowser(context, fallbackUri)
                    return true
                }
            }

            val dataUri = parsedIntent.data
            if (dataUri != null && isInternalYouTubeOrGoogleUrl(dataUri)) {
                view?.loadUrl(dataUri.toString())
                return true
            }

            parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(parsedIntent)
            true
        } catch (e: Exception) {
            AppLogger.w("PureWebView", "Failed to resolve intent scheme: ${e.message}")
            true
        }
    }

    // 2. Non-http/https custom schemes (mailto:, tel:, market:, whatsapp:, tg:, etc.)
    if (scheme != "http" && scheme != "https") {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLogger.w("PureWebView", "Cannot open custom scheme $scheme: ${e.message}")
            true
        }
    }

    // 3. YouTube redirect wrappers (e.g. https://www.youtube.com/redirect?q=https%3A%2F%2Finstagram.com...)
    if ((host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be") && path.startsWith("/redirect")) {
        val targetUrl = uri.getQueryParameter("q")
        if (!targetUrl.isNullOrBlank()) {
            val targetUri = try { Uri.parse(targetUrl) } catch (e: Exception) { null }
            if (targetUri != null) {
                if (isInternalYouTubeOrGoogleUrl(targetUri)) {
                    // Internal target (e.g. YouTube or Google Auth redirect) -> load internally
                    return false
                } else {
                    // External target (socials, patreon, discord, etc.) -> open in default browser
                    openInExternalBrowser(context, targetUri)
                    return true
                }
            }
        }
        return false
    }

    // 4. Google URL redirect wrappers (e.g. https://www.google.com/url?q=...)
    if (isInternalYouTubeOrGoogleUrl(uri) && path.startsWith("/url")) {
        val targetUrl = uri.getQueryParameter("q") ?: uri.getQueryParameter("url")
        if (!targetUrl.isNullOrBlank()) {
            val targetUri = try { Uri.parse(targetUrl) } catch (e: Exception) { null }
            if (targetUri != null) {
                if (isInternalYouTubeOrGoogleUrl(targetUri)) {
                    return false
                } else {
                    openInExternalBrowser(context, targetUri)
                    return true
                }
            }
        }
        return false
    }

    // 5. Internal YouTube & Google ecosystem URLs (Auth, login, videos, channels, playlists, policies, captchas, etc.)
    if (isInternalYouTubeOrGoogleUrl(uri)) {
        return false // Let PureTube WebView load internally
    }

    // 6. External web link -> Open in user's default browser
    openInExternalBrowser(context, uri)
    return true
}

private fun isInternalYouTubeOrGoogleUrl(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false

    // YouTube core domains
    if (host == "youtube.com" || host.endsWith(".youtube.com")) return true
    if (host == "youtu.be" || host == "yt.be") return true
    if (host == "googlevideo.com" || host.endsWith(".googlevideo.com")) return true
    if (host == "ytimg.com" || host.endsWith(".ytimg.com")) return true
    if (host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")) return true
    if (host == "youtubeeducation.com" || host.endsWith(".youtubeeducation.com")) return true

    // Google Services, Accounts, Auth, Policies, Captchas & Infrastructure
    if (host == "google.com" || host.endsWith(".google.com")) return true
    if (host == "gstatic.com" || host.endsWith(".gstatic.com")) return true
    if (host == "googleusercontent.com" || host.endsWith(".googleusercontent.com")) return true
    if (host == "googleapis.com" || host.endsWith(".googleapis.com")) return true
    if (host == "gvt1.com" || host.endsWith(".gvt1.com")) return true
    if (host == "recaptcha.net" || host.endsWith(".recaptcha.net")) return true

    // Regional Google Domains (e.g. google.co.uk, google.ca, accounts.google.de, google.com.pk)
    if (isRegionalGoogleHost(host)) return true

    return false
}

private fun isRegionalGoogleHost(host: String): Boolean {
    val parts = host.split(".")
    for (i in parts.indices) {
        if (parts[i] == "google" && i < parts.size - 1) {
            return true
        }
    }
    return false
}

private fun openInExternalBrowser(context: Context, uri: Uri) {
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    } catch (e: Exception) {
        AppLogger.w("PureWebView", "Failed to launch external browser for $uri: ${e.message}")
    }
}
