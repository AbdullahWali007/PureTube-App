package com.puretube.adfree.data.update

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int = 1,
    val isMandatory: Boolean = false,
    val releaseDate: String = "",
    val downloadUrl: String,
    val releaseNotes: String = ""
)
