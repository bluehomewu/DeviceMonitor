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
 * 從 GitHub Releases API 檢查是否有新版本可用。
 *
 * 使用方式：
 *   val info = UpdateChecker().checkForUpdate(BuildConfig.VERSION_NAME)
 *   if (info != null) // 有新版本，info.latestVersion / info.releaseUrl
 *
 * Release tag 格式：v1.4.0（去除 v 前綴後與 VERSION_NAME 比較）。
 * 上傳新版 APK 至 GitHub Releases 後，App 啟動時會自動提示使用者更新。
 */
class UpdateChecker {

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String
    )

    data class UpdateInfo(val latestVersion: String, val releaseUrl: String)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        val client = HttpClient(OkHttp)
        return try {
            runCatching {
                val body = client.get(RELEASES_API_URL) {
                    header("Accept", "application/vnd.github+json")
                }.bodyAsText()
                val release = json.decodeFromString<GitHubRelease>(body)
                val latestVersion = release.tagName.removePrefix("v")
                if (isNewerVersion(latestVersion, currentVersion)) {
                    UpdateInfo(latestVersion, release.htmlUrl)
                } else null
            }.getOrNull()
        } finally {
            client.close()
        }
    }

    /**
     * 語意化版本比較（Major.Minor.Patch）。
     * 回傳 true 表示 latest 比 current 新。
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
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

    companion object {
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/bluehomewu/DeviceMonitor/releases/latest"

        /** 開啟此 URL 會自動跳轉至最新 release 頁面。 */
        const val RELEASES_PAGE_URL =
            "https://github.com/bluehomewu/DeviceMonitor/releases/latest"
    }
}
