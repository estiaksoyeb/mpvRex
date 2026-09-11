package xyz.mpv.rex.preferences

import xyz.mpv.rex.domain.ytdl.model.StreamExtractionOptions
import xyz.mpv.rex.preferences.preference.PreferenceStore

class YtdlPreferences(
    private val preferenceStore: PreferenceStore,
) {
    val qualityPreference = preferenceStore.getString("ytdl_quality", "auto")
    val customFormat = preferenceStore.getString("ytdl_custom_format", "")
    val customUserAgent = preferenceStore.getString("ytdl_custom_user_agent", "")
    val proxy = preferenceStore.getString("ytdl_proxy", "")
    val cookiesFile = preferenceStore.getString("ytdl_cookies_file", "")
    val geoBypass = preferenceStore.getBoolean("ytdl_geo_bypass", true)
    val preferNightly = preferenceStore.getBoolean("ytdl_prefer_nightly", false)
    val autoDetectWebPages = preferenceStore.getBoolean("ytdl_auto_detect_web_pages", true)
    val customDomains = preferenceStore.getString("ytdl_custom_domains", "")

    fun getParsedCustomDomains(): Set<String> {
        return customDomains.get()
            .split(",", "\n", " ", ";")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun buildExtractionOptions(): StreamExtractionOptions {
        val format = when (qualityPreference.get()) {
            "2160" -> "bestvideo[height<=2160][vcodec^=avc1]+bestaudio[acodec^=mp4a]/bestvideo[height<=2160][vcodec^=vp9]+bestaudio/bestvideo[height<=2160]+bestaudio/best[height<=2160]"
            "1440" -> "bestvideo[height<=1440][vcodec^=avc1]+bestaudio[acodec^=mp4a]/bestvideo[height<=1440][vcodec^=vp9]+bestaudio/bestvideo[height<=1440]+bestaudio/best[height<=1440]"
            "1080" -> "bestvideo[height<=1080][vcodec^=avc1]+bestaudio[acodec^=mp4a]/bestvideo[height<=1080][vcodec^=vp9]+bestaudio/bestvideo[height<=1080]+bestaudio/best[height<=1080]"
            "720" -> "bestvideo[height<=720][vcodec^=avc1]+bestaudio[acodec^=mp4a]/bestvideo[height<=720][vcodec^=vp9]+bestaudio/bestvideo[height<=720]+bestaudio/best[height<=720]"
            "480" -> "bestvideo[height<=480][vcodec^=avc1]+bestaudio/bestvideo[height<=480]+bestaudio/best[height<=480]"
            "audio_only" -> "bestaudio/best"
            else -> customFormat.get().takeIf { it.isNotBlank() } ?: "bestvideo[height<=1080][vcodec^=avc1]+bestaudio[acodec^=mp4a]/bestvideo[height<=1080][vcodec^=vp9]+bestaudio/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
        }

        return StreamExtractionOptions(
            format = format,
            userAgent = customUserAgent.get().takeIf { it.isNotBlank() },
            referer = null,
            proxy = proxy.get().takeIf { it.isNotBlank() },
            cookiesFilePath = cookiesFile.get().takeIf { it.isNotBlank() },
            extractorArgs = null,
            geoBypass = geoBypass.get(),
        )
    }
}
