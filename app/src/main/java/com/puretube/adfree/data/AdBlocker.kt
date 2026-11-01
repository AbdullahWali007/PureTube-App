package com.puretube.adfree.data

import android.net.Uri
import android.util.LruCache
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Collections

object AdBlocker {

    // Fast O(1) Cache for evaluated URLs to minimize regex and string parsing overhead
    private val urlFilterCache = LruCache<String, Boolean>(1024)

    // Pre-computed Set of known ad & tracker domain suffixes for instant lookup
    private val AD_DOMAINS: Set<String> = Collections.unmodifiableSet(
        setOf(
            "doubleclick.net",
            "googleads.g.doubleclick.net",
            "googlesyndication.com",
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "googleadservices.com",
            "securepubads.g.doubleclick.net",
            "static.doubleclick.net",
            "ad.doubleclick.net",
            "pubads.g.doubleclick.net",
            "ads.youtube.com",
            "2mdn.net",
            "s0.2mdn.net",
            "fls-na.amazon.com",
            "adnxs.com",
            "adroll.com",
            "scorecardresearch.com",
            "quantserve.com",
            "admob.com",
            "moatads.com",
            "adform.net",
            "taboola.com",
            "outbrain.com"
        )
    )

    // Specific ad path tokens
    private val AD_PATH_SIGNATURES = arrayOf(
        "/pagead/",
        "/api/stats/ads",
        "/api/stats/qoe?adformat",
        "/ptracking",
        "/pagead/lvz",
        "/pagead/gen_204",
        "yt3.ggpht.com/ads",
        "/get_midroll_info",
        "adformat=",
        "ad_type=",
        "ad_tag="
    )

    private val EMPTY_BYTE_ARRAY = ByteArray(0)

    fun isAdUrl(url: String?): Boolean {
        if (url == null || url.length < 8) return false

        // Check LRU cache first for fast-path lookup
        val cached = urlFilterCache.get(url)
        if (cached != null) {
            return cached
        }

        val isAd = evaluateAdUrl(url)
        urlFilterCache.put(url, isAd)
        return isAd
    }

    private fun evaluateAdUrl(url: String): Boolean {
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""

            // Domain matching
            if (host.isNotEmpty()) {
                if (AD_DOMAINS.contains(host)) return true
                for (domain in AD_DOMAINS) {
                    if (host.endsWith(".$domain")) {
                        return true
                    }
                }
            }

            // Path & query signature checking
            val lowerUrl = url.lowercase()
            for (sig in AD_PATH_SIGNATURES) {
                if (lowerUrl.contains(sig)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Defensive: fail safe without crashing
            return false
        }
        return false
    }

    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-cache"
            ),
            ByteArrayInputStream(EMPTY_BYTE_ARRAY)
        )
    }

    // High-efficiency declarative CSS rules evaluated in native engine with 0ms JS runtime overhead
    val AD_HIDE_CSS = """
        * {
            -webkit-tap-highlight-color: transparent !important;
            -webkit-tap-highlight-color: rgba(0, 0, 0, 0) !important;
            -webkit-focus-ring-color: rgba(0, 0, 0, 0) !important;
            outline: none !important;
        }
        *:focus, *:active, *:hover {
            outline: none !important;
            -webkit-tap-highlight-color: transparent !important;
            -webkit-tap-highlight-color: rgba(0, 0, 0, 0) !important;
        }
        .video-ads,
        .ytp-ad-module,
        .ytp-ad-player-overlay,
        .ytp-ad-overlay-container,
        .ytp-ad-message-container,
        .ytp-ad-progress-list,
        .ytp-ad-image-overlay,
        .ytp-ad-text,
        .ytp-ad-preview-container,
        #player-ads,
        #offer-module,
        #masthead-ad,
        .ytd-banner-promo-renderer,
        .ytd-statement-banner-renderer,
        .ytd-in-feed-ad-layout-renderer,
        .ytd-ad-slot-renderer,
        .ytd-promoted-video-renderer,
        .ytd-promoted-sparkles-web-renderer,
        .ytm-promoted-sparkles-web-renderer,
        .ytm-companion-ad-renderer,
        .ytm-promoted-sparkles-text-search-web-renderer,
        .ytm-paid-content-overlay-renderer,
        .ytm-mealbar-promo-renderer,
        .upsell-dialog-renderer,
        ytm-promoted-video-renderer,
        ytm-ad-slot-renderer,
        ytd-rich-item-renderer:has(ytd-ad-slot-renderer),
        ytd-rich-item-renderer:has(.ytd-ad-slot-renderer),
        ytd-display-ad-renderer,
        ytd-statement-banner-renderer,
        ytd-compact-promoted-item-renderer,
        ytd-action-companion-ad-renderer,
        #top-ad,
        #header-ad,
        .ad-container,
        .companion-ad,
        ytm-statement-banner-renderer,
        ytm-mobile-topbar-renderer [aria-label*="Get YouTube Premium"],
        yt-upsell-dialog-renderer,
        ytm-open-app-button-renderer,
        .ytm-open-app-button-renderer,
        ytm-app-banner-renderer,
        .ytm-app-banner-renderer,
        .open-app-button,
        .topbar-open-app-button,
        [aria-label*="Open App"],
        [aria-label*="Open app"],
        [aria-label*="open app"],
        [aria-label*="Open in App"],
        [aria-label*="Open in app"],
        [title*="Open App"],
        [title*="Open in App"],
        .mobile-topbar-header-open-app,
        ytm-promoted-app-banner-renderer,
        .ytm-banner-promo-renderer,
        .ytm-app-promo-renderer {
            display: none !important;
            visibility: hidden !important;
            height: 0px !important;
            min-height: 0px !important;
            max-height: 0px !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }

        /* Ensure YouTube video player settings button, quality/captions menu, and bottom sheets are always visible and interactive in horizontal fullscreen */
        ytm-menu-renderer,
        ytm-bottom-sheet-renderer,
        .ytm-bottom-sheet-renderer,
        .bottom-sheet-container,
        .bottom-sheet-layout,
        ytm-dialog-renderer,
        yt-dialog-container,
        #dialog,
        .ytp-popup,
        .ytp-settings-menu,
        .ytp-panel,
        .ytp-panel-menu,
        .ytp-quality-menu,
        .ytm-settings-menu,
        .ytm-menu-item-renderer,
        .ytm-menu-item-container {
            z-index: 2147483647 !important;
            pointer-events: auto !important;
        }

        .ytp-settings-button,
        .player-control-button,
        .ytm-player-control-button,
        button[aria-label*="Settings"],
        button[aria-label*="settings"],
        button[aria-label*="Playback settings"],
        button[aria-label*="More options"],
        button[aria-label*="Quality and captions"],
        [title*="Settings"],
        [title*="settings"] {
            pointer-events: auto !important;
            cursor: pointer !important;
        }
    """.trimIndent()

    // Seamless background audio and visibility spoofing to prevent YouTube auto-pausing & jitters
    val BACKGROUND_PLAY_SCRIPT = """
        (function() {
            if (window._pureTubeBackgroundInitialized) return;
            window._pureTubeBackgroundInitialized = true;

            // 1. Spoof Page Visibility API
            try {
                Object.defineProperty(document, 'hidden', {
                    get: function() { return false; },
                    configurable: true
                });
                Object.defineProperty(document, 'visibilityState', {
                    get: function() { return 'visible'; },
                    configurable: true
                });
                Object.defineProperty(document, 'webkitVisibilityState', {
                    get: function() { return 'visible'; },
                    configurable: true
                });
                Object.defineProperty(document, 'webkitHidden', {
                    get: function() { return false; },
                    configurable: true
                });
                document.hasFocus = function() { return true; };
            } catch(e) {}

            // 2. Swallow visibility change & blur events in capture phase
            const swallowEvents = ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide'];
            swallowEvents.forEach(evt => {
                window.addEventListener(evt, e => e.stopImmediatePropagation(), true);
                document.addEventListener(evt, e => e.stopImmediatePropagation(), true);
            });

            // 3. Prevent programmatic background pause during PiP
            const origPause = HTMLMediaElement.prototype.pause;
            HTMLMediaElement.prototype.pause = function() {
                if (window._isPureTubePiP && !this.ended && (this.currentTime > 0)) {
                    return;
                }
                return origPause.apply(this, arguments);
            };
        })();
    """.trimIndent()

    // Highly optimized event-driven ad skipper and element suppressor
    val AD_SKIP_SCRIPT = """
        (function() {
            if (window._pureTubeInitialized) return;
            window._pureTubeInitialized = true;

            function notifyAdBlocked(type) {
                if (window.PureTubeBridge && window.PureTubeBridge.onAdBlocked) {
                    try {
                        window.PureTubeBridge.onAdBlocked(type || 'Ad Blocked');
                    } catch(e) {}
                }
            }

            // Fast single-pass native CSS injection
            function injectAdCss() {
                if (document.getElementById('puretube-ad-css')) return;
                const style = document.createElement('style');
                style.id = 'puretube-ad-css';
                style.textContent = `
                    * {
                        -webkit-tap-highlight-color: transparent !important;
                        -webkit-tap-highlight-color: rgba(0, 0, 0, 0) !important;
                        -webkit-focus-ring-color: rgba(0, 0, 0, 0) !important;
                        outline: none !important;
                    }
                    *:focus, *:active, *:hover {
                        outline: none !important;
                        -webkit-tap-highlight-color: transparent !important;
                        -webkit-tap-highlight-color: rgba(0, 0, 0, 0) !important;
                    }
                    .video-ads, .ytp-ad-module, .ytp-ad-player-overlay,
                    .ytp-ad-overlay-container, .ytp-ad-message-container,
                    #player-ads, #offer-module, #masthead-ad,
                    ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer,
                    ytm-promoted-sparkles-text-search-web-renderer,
                    .ytd-banner-promo-renderer, .ytd-in-feed-ad-layout-renderer,
                    .ytd-ad-slot-renderer, .ytd-promoted-video-renderer,
                    ytm-mealbar-promo-renderer, .upsell-dialog-renderer,
                    ytm-paid-content-overlay-renderer, ytm-promoted-video-renderer,
                    ytm-open-app-button-renderer, .ytm-open-app-button-renderer,
                    ytm-app-banner-renderer, .ytm-app-banner-renderer,
                    .open-app-button, .topbar-open-app-button,
                    [aria-label*="Open App"], [aria-label*="Open app"],
                    [aria-label*="Open in App"], [aria-label*="Open in app"],
                    [title*="Open App"], [title*="Open in App"],
                    .mobile-topbar-header-open-app, ytm-promoted-app-banner-renderer {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0px !important;
                        min-height: 0px !important;
                        max-height: 0px !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }

                    /* Ensure YouTube video player settings button and its bottom-sheet / popups open smoothly above video player in horizontal fullscreen */
                    ytm-menu-renderer,
                    ytm-bottom-sheet-renderer,
                    .ytm-bottom-sheet-renderer,
                    .bottom-sheet-container,
                    .bottom-sheet-layout,
                    ytm-dialog-renderer,
                    yt-dialog-container,
                    #dialog,
                    .ytp-popup,
                    .ytp-settings-menu,
                    .ytp-panel,
                    .ytp-panel-menu,
                    .ytp-quality-menu,
                    .ytm-settings-menu,
                    .ytm-menu-item-renderer,
                    .ytm-menu-item-container {
                        z-index: 2147483647 !important;
                        pointer-events: auto !important;
                    }

                    .ytp-settings-button,
                    .player-control-button,
                    .ytm-player-control-button,
                    button[aria-label*="Settings"],
                    button[aria-label*="settings"],
                    button[aria-label*="Playback settings"],
                    button[aria-label*="More options"],
                    button[aria-label*="Quality and captions"],
                    [title*="Settings"],
                    [title*="settings"] {
                        pointer-events: auto !important;
                        cursor: pointer !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            }
            injectAdCss();

            // Ensure player settings button clicks in horizontal fullscreen elevate the popup/menu immediately
            document.addEventListener('click', function(e) {
                const isSettings = e.target.closest && e.target.closest('.ytp-settings-button, button[aria-label*="Settings"], button[aria-label*="settings"], button[aria-label*="Playback settings"], button[aria-label*="More options"], button[aria-label*="Quality and captions"], [title*="Settings"], [title*="settings"]');
                if (isSettings) {
                    setTimeout(function() {
                        const menus = document.querySelectorAll('ytm-menu-renderer, ytm-bottom-sheet-renderer, .ytm-bottom-sheet-renderer, .bottom-sheet-container, ytm-dialog-renderer, yt-dialog-container, #dialog, .ytp-popup, .ytp-settings-menu');
                        menus.forEach(function(m) {
                            m.style.zIndex = '2147483647';
                            m.style.pointerEvents = 'auto';
                        });
                    }, 50);
                }
            }, true);

            let isFastForwarding = false;
            let originalRate = 1.0;
            let pendingCheck = false;

            function checkAndSkipAds() {
                pendingCheck = false;
                const video = document.querySelector('video');
                const adShowing = document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay');

                // 1. Fast click skip button
                const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, [id^="skip-button"]');
                if (skipBtn && skipBtn.offsetParent !== null) {
                    try {
                        skipBtn.click();
                        notifyAdBlocked('Skip Button Triggered');
                    } catch(e) {}
                }

                // 2. Dismiss promo banners & overlays
                const overlayClose = document.querySelector('.ytp-ad-overlay-close-button');
                if (overlayClose) {
                    try {
                        overlayClose.click();
                        notifyAdBlocked('Ad Overlay Dismissed');
                    } catch(e) {}
                }

                // 3. Accelerate interrupting video ad
                if (video) {
                    if (adShowing) {
                        if (!isFastForwarding) {
                            originalRate = video.playbackRate || 1.0;
                            isFastForwarding = true;
                        }
                        video.muted = true;
                        video.playbackRate = 16.0;
                        if (!isNaN(video.duration) && isFinite(video.duration) && video.duration > 0) {
                            video.currentTime = video.duration;
                        }
                        notifyAdBlocked('Video Ad Auto-Skipped');
                    } else if (isFastForwarding) {
                        isFastForwarding = false;
                        video.muted = false;
                        video.playbackRate = originalRate;
                    }
                }
            }

            function requestSkipCheck() {
                if (!pendingCheck) {
                    pendingCheck = true;
                    requestAnimationFrame(checkAndSkipAds);
                }
            }

            // MutationObserver with requestAnimationFrame debouncing for minimal CPU overhead
            const observer = new MutationObserver(requestSkipCheck);
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            } else {
                document.addEventListener('DOMContentLoaded', () => {
                    observer.observe(document.body, { childList: true, subtree: true });
                });
            }

            // SPA Navigation & URL state detection
            function handleUrlChange() {
                try {
                    const url = window.location.href;
                    let videoId = '';
                    const vMatch = url.match(/[?&]v=([a-zA-Z0-9_-]{11})/);
                    if (vMatch) videoId = vMatch[1];
                    else {
                        const sMatch = url.match(/\/shorts\/([a-zA-Z0-9_-]{11})/);
                        if (sMatch) videoId = sMatch[1];
                    }

                    if (window.PureTubeBridge) {
                        let cleanTitle = (document.title || 'YouTube').replace(' - YouTube', '').trim();
                        if (window.PureTubeBridge.onVideoInfo) {
                            window.PureTubeBridge.onVideoInfo(cleanTitle, url);
                        }
                        if (videoId && window.PureTubeBridge.onVideoPlayingState) {
                            const video = document.querySelector('video');
                            const isPlaying = video ? (!video.paused && !video.ended) : true;
                            const duration = (video && !isNaN(video.duration)) ? Math.round(video.duration) : 0;
                            window.PureTubeBridge.onVideoPlayingState(isPlaying, videoId, cleanTitle, duration);
                        } else if (!videoId && (url.includes('/feed/') || url.includes('/channel/') || url.includes('/results?') || url === 'https://m.youtube.com/' || url === 'https://m.youtube.com' || url.startsWith('https://m.youtube.com/?') || url.startsWith('https://m.youtube.com/#'))) {
                            if (window.PureTubeBridge.onVideoPlayingState) {
                                window.PureTubeBridge.onVideoPlayingState(false, "", cleanTitle, 0);
                            }
                        }
                    }
                } catch(e) {}
            }

            window.addEventListener('popstate', handleUrlChange, true);
            window.addEventListener('hashchange', handleUrlChange, true);
            document.addEventListener('yt-navigate-finish', handleUrlChange, true);
            document.addEventListener('yt-page-data-updated', handleUrlChange, true);

            // Report video playback and playing status for Floating Download button
            function reportPlaybackState(isPlaying) {
                try {
                    if (window.PureTubeBridge && window.PureTubeBridge.onVideoPlayingState) {
                        const video = document.querySelector('video');
                        const url = window.location.href;
                        let videoId = '';
                        const vMatch = url.match(/[?&]v=([a-zA-Z0-9_-]{11})/);
                        if (vMatch) videoId = vMatch[1];
                        else {
                            const sMatch = url.match(/\/shorts\/([a-zA-Z0-9_-]{11})/);
                            if (sMatch) videoId = sMatch[1];
                        }

                        if (!videoId) return;

                        const title = (document.title || '').replace(' - YouTube', '').trim();
                        const duration = (video && !isNaN(video.duration)) ? Math.round(video.duration) : 0;
                        window.PureTubeBridge.onVideoPlayingState(isPlaying, videoId, title, duration);
                    }
                } catch(e) {}
            }

            document.addEventListener('play', () => {
                requestSkipCheck();
                reportPlaybackState(true);
            }, true);
            document.addEventListener('playing', () => {
                reportPlaybackState(true);
            }, true);
            document.addEventListener('pause', () => {
                reportPlaybackState(false);
            }, true);
            document.addEventListener('ended', () => {
                reportPlaybackState(false);
            }, true);

            document.addEventListener('timeupdate', () => {
                if (isFastForwarding || document.querySelector('.ad-showing')) {
                    requestSkipCheck();
                }
            }, true);

            // Light heartbeat fallback every 1200ms
            let lastUrlSnapshot = window.location.href;
            setInterval(() => {
                requestSkipCheck();
                if (window.location.href !== lastUrlSnapshot) {
                    lastUrlSnapshot = window.location.href;
                    handleUrlChange();
                }
                const video = document.querySelector('video');
                if (video && !video.paused && !video.ended) {
                    reportPlaybackState(true);
                }
            }, 1200);
            checkAndSkipAds();
            handleUrlChange();
        })();
    """.trimIndent()

    val PIP_ENABLE_SCRIPT = """
        (function() {
            window._isPureTubePiP = true;
            let style = document.getElementById('puretube-pip-css');
            if (!style) {
                style = document.createElement('style');
                style.id = 'puretube-pip-css';
                (document.head || document.documentElement).appendChild(style);
            }
            style.textContent = `
                html, body {
                    overflow: hidden !important;
                    background: #000 !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    width: 100% !important;
                    height: 100% !important;
                }
                * {
                    -webkit-tap-highlight-color: transparent !important;
                }
                .ytm-mobile-topbar-renderer, ytm-pivot-bar-renderer, .pivot-bar,
                ytm-item-section-renderer, #related-items, ytm-comment-section-renderer,
                #comments, .watch-below-the-player, #header-bar, .player-controls-middle,
                ytm-open-app-button-renderer, .open-app-button, .player-controls-background {
                    display: none !important;
                }
                #player-container-id, #player, .player-container, .html5-video-player, video, .html5-main-video {
                    position: fixed !important;
                    top: 0 !important;
                    left: 0 !important;
                    width: 100vw !important;
                    height: 100vh !important;
                    max-width: 100vw !important;
                    max-height: 100vh !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    z-index: 2147483647 !important;
                    object-fit: contain !important;
                    background: #000 !important;
                    transform: translateZ(0) !important;
                }
            `;
            const video = document.querySelector('video');
            if (video) {
                video.muted = false;
                if (video.paused && !video.ended) {
                    video.play().catch(() => {});
                }
            }
            window.dispatchEvent(new Event('resize'));
        })();
    """.trimIndent()

    val PIP_DISABLE_SCRIPT = """
        (function() {
            window._isPureTubePiP = false;
            const style = document.getElementById('puretube-pip-css');
            if (style) {
                style.remove();
            }
            // Clear inline styles on player/video elements so natural dimensions fit seamlessly
            const elements = document.querySelectorAll('#player-container-id, #player, .player-container, .html5-video-player, video, .html5-main-video');
            elements.forEach(el => {
                el.style.position = '';
                el.style.top = '';
                el.style.left = '';
                el.style.width = '';
                el.style.height = '';
                el.style.maxWidth = '';
                el.style.maxHeight = '';
                el.style.transform = '';
                el.style.zIndex = '';
            });

            // Trigger player recalculation & resize event sequence
            function triggerResize() {
                window.dispatchEvent(new Event('resize'));
                window.dispatchEvent(new Event('orientationchange'));
                const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                if (player && typeof player.setInternalSize === 'function') {
                    try { player.setInternalSize(); } catch(e) {}
                }
                if (player && typeof player.checkVideoScale === 'function') {
                    try { player.checkVideoScale(); } catch(e) {}
                }
            }

            triggerResize();
            setTimeout(triggerResize, 60);
            setTimeout(triggerResize, 180);
            setTimeout(triggerResize, 400);

            const video = document.querySelector('video');
            if (video && video.paused) {
                video.play().catch(() => {});
            }
        })();
    """.trimIndent()

    fun setPlaybackSpeedScript(speed: Float): String {
        return """
            (function() {
                const video = document.querySelector('video');
                if (video) {
                    video.playbackRate = $speed;
                }
            })();
        """.trimIndent()
    }

    val EXTRACT_VIDEO_INFO_SCRIPT = """
        (function() {
            let title = document.title || '';
            title = title.replace(' - YouTube', '').trim();
            const url = window.location.href;
            if (window.PureTubeBridge && window.PureTubeBridge.onVideoInfo) {
                window.PureTubeBridge.onVideoInfo(title, url);
            }
        })();
    """.trimIndent()
}
