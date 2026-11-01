package com.puretube.adfree

import android.app.Application
import android.util.Log
import com.puretube.adfree.util.AppLogger
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class PureTubeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize persistent file logger (append mode, 5MB limit, never overwrites old logs)
        AppLogger.init(this)
        AppLogger.i(TAG, "Application onCreate started.")

        // 2. Install global uncaught exception crash recorder
        val defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.e("FATAL_CRASH", "FATAL UNCAUGHT EXCEPTION on thread [${thread.name}]: ${throwable.message}", throwable)
                AppLogger.flush()
            } catch (e: Throwable) {}
            defaultUncaughtHandler?.uncaughtException(thread, throwable)
        }

        // 3. Initialize YoutubeDL and FFmpeg engine
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            AppLogger.i(TAG, "YoutubeDL and FFmpeg initialized successfully.")
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Failed to initialize YoutubeDL / FFmpeg: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "PureTubeApplication"
        lateinit var instance: PureTubeApplication
            private set
    }
}
