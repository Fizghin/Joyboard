package com.joyboard.notchisland.update

import java.io.File

/** A release described by the update manifest hosted next to the APKs. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: List<String>,
    val debugUrl: String?,
    val releaseUrl: String?,
    val sizeBytes: Long,
    val mandatory: Boolean,
) {
    fun downloadUrl(debugBuild: Boolean): String? =
        if (debugBuild) debugUrl ?: releaseUrl else releaseUrl ?: debugUrl

    val readableSize: String
        get() = when {
            sizeBytes <= 0 -> ""
            sizeBytes >= 1_000_000 -> "${"%.1f".format(sizeBytes / 1_000_000f)} MB"
            else -> "${sizeBytes / 1000} kB"
        }
}

/** Where the in-app updater is in its journey, start to finish. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val versionName: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}
