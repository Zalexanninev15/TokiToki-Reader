package io.github.zalexanninev15.tokitoki.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manual update check against the GitHub releases API.
 *
 * Deliberately not automatic and not on a schedule: a reader has no business polling
 * GitHub in the background, and an app that nags about updates on every launch is worse
 * than one you check yourself when you feel like it.
 */
object UpdateChecker {

    private const val LATEST_RELEASE =
        "https://api.github.com/repos/Zalexanninev15/TokiToki-Reader/releases/latest"

    sealed interface Result {
        data class UpToDate(val current: String) : Result
        data class Available(val version: String, val url: String) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "TokiToki-Reader")
            }
            val body = connection.use { it.inputStream.bufferedReader().readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { error("no tag in response") }
            val url = json.optString("html_url").ifBlank { LATEST_RELEASE }

            if (isNewer(tag, currentVersion)) Result.Available(tag, url)
            else Result.UpToDate(currentVersion)
        }.getOrElse { Result.Failed(it.message ?: "network error") }
    }

    /**
     * Tags look like `v0.1-pre-alpha-42`, so the build number at the end is what actually
     * orders two releases. Falls back to a plain inequality when no number is present.
     */
    internal fun isNewer(tag: String, current: String): Boolean {
        val remote = trailingNumber(tag)
        val local = trailingNumber(current)
        return when {
            remote != null && local != null -> remote > local
            else -> tag.trimStart('v') != current
        }
    }

    private fun trailingNumber(value: String): Int? =
        Regex("(\\d+)\\s*$").find(value)?.groupValues?.get(1)?.toIntOrNull()

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
