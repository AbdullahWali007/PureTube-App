package com.puretube.adfree.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object DefaultAppHelper {

    /**
     * Checks if PureTube is currently verified or selected by the user
     * to open YouTube domain links by default.
     */
    fun isDefaultYouTubeHandler(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(DomainVerificationManager::class.java)
                val userState = manager?.getDomainVerificationUserState(context.packageName)
                if (userState != null) {
                    val hostMap = userState.hostToStateMap
                    // Check if any youtube hosts are selected or verified
                    hostMap.any { (host, state) ->
                        (host.contains("youtube.com") || host.contains("youtu.be")) &&
                        (state == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                         state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED)
                    }
                } else {
                    false
                }
            } else {
                val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
                val resolveInfo = context.packageManager.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
                resolveInfo?.activityInfo?.packageName == context.packageName
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens the system settings screen directly to the "Open by default"
     * configuration for this app, guiding the user to enable supported links.
     */
    fun openDefaultAppSettings(context: Context) {
        var launched = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                launched = true
            } catch (e: Exception) {
                // Fallback
            }
        }

        if (!launched) {
            try {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                launched = true
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e2: Exception) {}
            }
        }

        try {
            Toast.makeText(
                context,
                "In Settings: Tap '+ Add link' and select all YouTube links",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {}
    }

    /**
     * Opens the Official YouTube app's settings so user can disable
     * 'Open supported links' on the official app if Android forces it.
     */
    fun openOfficialYouTubeAppSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(
                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    Uri.parse("package:com.google.android.youtube")
                )
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:com.google.android.youtube")
                )
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Official YouTube app not found", Toast.LENGTH_SHORT).show()
        }
    }
}
