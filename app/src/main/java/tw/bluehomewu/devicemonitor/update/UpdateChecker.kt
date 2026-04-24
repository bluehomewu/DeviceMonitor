package tw.bluehomewu.devicemonitor.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 從 GitHub Releases API 取得最新版本資訊。
 *
 * Release tag 格式：v1.5.0（去除 v 前綴後與 VERSION_NAME 比較）。
 * APK asset 需命名為 *.apk，否則 apkUrl 為 null（fallback 至開啟瀏覽器）。
 */
class UpdateChecker {

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name")  val tagName: String,
        @SerialName("html_url")  val htmlUrl: String,
        @SerialName("body")      val body: String = "",
        @SerialName("assets")    val assets: List<GitHubAsset> = emptyList()
    )

    @Serializable
    private data class GitHubAsset(
        @SerialName("name")                 val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String
    )

    /**
     * @param version  版本號，例如 "1.5.0"（已去除 v 前綴）
     * @param body     GitHub Release 的 Markdown 說明文字
     * @param htmlUrl  GitHub Release 頁面 URL
     * @param apkUrl   APK 直連下載 URL，無 APK asset 時為 null
     */
    data class ReleaseInfo(
        val version: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String?
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 取得最新 release 資訊（不論版本是否比當前新）。
     * [beta] = true 時從 releases 清單中找第一個含 "-beta" 的 tag；
     * [beta] = false（預設）時取 /releases/latest（最新穩定版）。
     */
    suspend fun fetchLatestRelease(beta: Boolean = false): ReleaseInfo? =
        if (beta) fetchLatestBetaRelease() else fetchLatestStableRelease()

    private suspend fun fetchLatestStableRelease(): ReleaseInfo? {
        val client = HttpClient(OkHttp)
        return try {
            runCatching {
                val body = client.get(RELEASES_LATEST_URL) {
                    header("Accept", "application/vnd.github+json")
                }.bodyAsText()
                json.decodeFromString<GitHubRelease>(body).toReleaseInfo()
            }.getOrNull()
        } finally {
            client.close()
        }
    }

    private suspend fun fetchLatestBetaRelease(): ReleaseInfo? {
        val client = HttpClient(OkHttp)
        return try {
            runCatching {
                val body = client.get(RELEASES_LIST_URL) {
                    header("Accept", "application/vnd.github+json")
                }.bodyAsText()
                json.decodeFromString<List<GitHubRelease>>(body)
                    .firstOrNull { it.tagName.contains("-beta") }
                    ?.toReleaseInfo()
            }.getOrNull()
        } finally {
            client.close()
        }
    }

    /**
     * 檢查是否有比 [currentVersion] 更新的版本。
     * [beta] = true 時比對 beta 頻道；否則只比對穩定版。
     * 有則回傳 [ReleaseInfo]，否則回傳 null。
     */
    suspend fun checkForUpdate(currentVersion: String, beta: Boolean = false): ReleaseInfo? {
        val info = fetchLatestRelease(beta) ?: return null
        return if (isNewerVersion(info.version, currentVersion)) info else null
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    private fun GitHubRelease.toReleaseInfo() = ReleaseInfo(
        version = tagName.removePrefix("v"),
        body    = body,
        htmlUrl = htmlUrl,
        apkUrl  = assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
    )

    companion object {
        private const val RELEASES_LATEST_URL =
            "https://api.github.com/repos/bluehomewu/DeviceMonitor/releases/latest"
        private const val RELEASES_LIST_URL =
            "https://api.github.com/repos/bluehomewu/DeviceMonitor/releases"
        const val RELEASES_PAGE_URL =
            "https://github.com/bluehomewu/DeviceMonitor/releases/latest"
    }
}
