package xyz.mpv.rex.utils.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import xyz.mpv.rex.BuildConfig

/**
 * Companion (REX-Ytdlp APK) update checker.
 *
 * Gated on the addon being installed: if the companion package is absent,
 * [checkForUpdate] returns null without touching the network, so users who
 * never installed the companion never see an update dialog.
 *
 * Reuses [Release]/[Asset] from UpdateFeature.kt. The version we compare
 * against is the companion APK's versionName (PackageManager), NOT the
 * yt-dlp core version reported over IPC.
 */
class YtdlpUpdateManager(private val context: Context) {

    private val client = UpdateManager.clientWithUserAgent()
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy { context.getSharedPreferences("mpvEx_prefs", Context.MODE_PRIVATE) }

    fun isCompanionInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    COMPANION_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(COMPANION_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun installedVersion(): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    COMPANION_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(COMPANION_PACKAGE, 0)
            }
            info.versionName?.removePrefix("v")
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    suspend fun checkForUpdate(forceShow: Boolean = false, manual: Boolean = forceShow): Release? {
        // Hard gate: no companion installed -> never nag.
        if (!isCompanionInstalled()) return null
        if (!BuildConfig.ENABLE_UPDATE_FEATURE) return null
        // Automatic checks are throttled to once per hour; manual ones always run.
        if (!manual && !isAutoCheckDue()) return null

        val currentVersion = installedVersion() ?: return null

        val release = getLatestRelease(
            "https://api.github.com/repos/mpvRex/REX-Ytdlp/releases/latest"
        )
        // Server answered, so count this as a check.
        markChecked()
        val remoteVersion = release.tagName.removePrefix("v")
        val ignoredVersion = prefs.getString("ytdlp_ignored_version", null)

        if (!forceShow && ignoredVersion == remoteVersion) return null

        return if (isNewerVersion(remoteVersion, currentVersion)) release else null
    }

    /** True if an automatic (non-manual) companion check is allowed right now. */
    fun isAutoCheckDue(): Boolean = UpdateManager.isAutoCheckDue(prefs.getLong(KEY_LAST_CHECK, 0L))

    /** Records a successful check so the next automatic check waits an hour. */
    fun markChecked() {
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun ignoreVersion(version: String) {
        prefs.edit().putString("ytdlp_ignored_version", version).apply()
    }

    private suspend fun getLatestRelease(url: String): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        json.decodeFromString<Release>(response.body.string())
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(rParts.size, cParts.size)) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    companion object {
        const val COMPANION_PACKAGE = "xyz.mpv.rex.addon.ytdl"
        private const val KEY_LAST_CHECK = "last_ytdlp_update_check"
    }
}
