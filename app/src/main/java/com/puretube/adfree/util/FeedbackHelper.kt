package com.puretube.adfree.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.puretube.adfree.BuildConfig

object FeedbackHelper {

    const val SUPPORT_EMAIL = "abdullah.wali004@gmail.com"

    fun sendFeedback(context: Context, supportEmail: String = SUPPORT_EMAIL) {
        try {
            AppLogger.flush()

            val versionName = BuildConfig.VERSION_NAME
            val versionCode = BuildConfig.VERSION_CODE
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            val subject = "[PureTube Feedback] v$versionName"

            val body = buildString {
                append("Please write your feedback, feature request, or bug report below:\n\n\n\n")
                append("------------------------------------------\n")
                append("--- Device & App Diagnostics ---\n")
                append("App: PureTube v$versionName (Build $versionCode)\n")
                append("Device: $deviceModel\n")
                append("OS: $androidVersion\n")
                append("------------------------------------------\n")
                append("(Note: Diagnostic logs are only attached to help diagnose technical issues.)\n")
            }

            val logFile = AppLogger.getLogFile()
            if (logFile != null && logFile.exists() && logFile.length() > 0L) {
                val logUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    logFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    putExtra(Intent.EXTRA_STREAM, logUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(Intent.createChooser(intent, "Send Feedback via Email"))
            } else {
                val mailtoUri = Uri.parse("mailto:$supportEmail?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
                val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Send Feedback via Email"))
            }
        } catch (e: Exception) {
            AppLogger.e("FeedbackHelper", "Failed to launch email client: ${e.message}", e)
            Toast.makeText(context, "Could not open email client: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
