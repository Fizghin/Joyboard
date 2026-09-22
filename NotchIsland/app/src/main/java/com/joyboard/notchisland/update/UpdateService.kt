package com.joyboard.notchisland.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.joyboard.notchisland.BuildConfig
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks a small JSON manifest published alongside the APKs, downloads the matching build and
 * hands it to the package installer. No store, no account, no background service.
 */
class UpdateService(private val context: Context) {

    suspend fun check(currentVersionCode: Int, currentVersionName: String): UpdateState =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = readText(MANIFEST_URL)
                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.optString("versionName", "?"),
                    notes = json.optJSONArray("notes")?.let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    }.orEmpty(),
                    debugUrl = json.optString("debugApkUrl").takeIf { it.isNotBlank() },
                    releaseUrl = json.optString("releaseApkUrl").takeIf { it.isNotBlank() },
                    sizeBytes = if (BuildConfig.DEBUG) {
                        json.optLong("debugSizeBytes", json.optLong("sizeBytes", 0L))
                    } else {
                        json.optLong("sizeBytes", 0L)
                    },
                    mandatory = json.optBoolean("mandatory", false),
                )
                if (info.versionCode > currentVersionCode) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate(currentVersionName)
                }
            }.getOrElse { error ->
                UpdateState.Failed(error.friendlyMessage())
            }
        }

    suspend fun download(
        info: UpdateInfo,
        debugBuild: Boolean,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = info.downloadUrl(debugBuild)
                ?: error("This release has no APK for your build type")
            val directory = File(context.cacheDir, "updates").apply {
                deleteRecursively()
                mkdirs()
            }
            val target = File(directory, "NotchIsland-${info.versionCode}.apk")
            val connection = openConnection(url)
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER)
                    var downloaded = 0L
                    var lastReported = -1f
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val progress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                            if (progress - lastReported >= 0.01f) {
                                lastReported = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            connection.disconnect()
            if (target.length() <= 0L) error("The download came back empty")
            onProgress(1f)
            target
        }
    }

    /** True when the installer can be launched; false means the user must allow this app first. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun install(file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                )
        )
        true
    }.getOrDefault(false)

    private fun readText(url: String): String {
        val connection = openConnection(url)
        return connection.inputStream.bufferedReader().use { it.readText() }
            .also { connection.disconnect() }
    }

    /** Follows redirects by hand so GitHub's hop to its CDN survives the http/https switch. */
    private fun openConnection(url: String): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "*/*")
                setRequestProperty("User-Agent", "NotchIsland-Updater")
            }
            val code = connection.responseCode
            if (code in 300..399 && redirects < MAX_REDIRECTS) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) error("Redirect without a destination")
                current = URL(URL(current), location).toString()
                redirects++
                continue
            }
            if (code !in 200..299) {
                connection.disconnect()
                error(
                    when (code) {
                        404 -> "No update manifest published yet"
                        else -> "Server returned $code"
                    }
                )
            }
            return connection
        }
    }

    companion object {
        /** Lives on the branch that carries the APKs, so it updates whenever they do. */
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/Fizghin/Joyboard/notch/update.json"
        private const val MAX_REDIRECTS = 5
        private const val DOWNLOAD_BUFFER = 64 * 1024
    }
}

private fun Throwable.friendlyMessage(): String = when (this) {
    is java.net.UnknownHostException -> "No internet connection"
    is java.net.SocketTimeoutException -> "The update server timed out"
    else -> message ?: "Could not check for updates"
}
