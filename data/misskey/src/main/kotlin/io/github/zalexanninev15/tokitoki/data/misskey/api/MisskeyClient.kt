package io.github.zalexanninev15.tokitoki.data.misskey.api

import io.github.zalexanninev15.tokitoki.data.misskey.dto.TimelineRequest
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.repository.Page
import io.github.zalexanninev15.tokitoki.domain.repository.PageCursor
import io.github.zalexanninev15.tokitoki.domain.repository.SourceError
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

object MisskeyClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun create(baseUrl: String, client: OkHttpClient = defaultClient()): MisskeyApi =
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            // asConverterFactory is an extension on Json: Kotlin cannot call it in the
            // static two-argument form, only as a receiver call.
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MisskeyApi::class.java)

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/**
 * Reads a Misskey timeline.
 *
 * Pagination is `untilId`, not an opaque URL, and the server caps history at roughly
 * 30 days: when a page comes back shorter than requested the source is exhausted for
 * good, not merely for now.
 */
class MisskeyRemoteSource(
    private val api: MisskeyApi,
    private val token: String,
    private val mapper: MisskeyPostMapper,
) {

    suspend fun loadPage(cursor: PageCursor?, limit: Int = 30): Page {
        val notes = try {
            api.homeTimeline(TimelineRequest(i = token, limit = limit, untilId = cursor?.raw))
        } catch (e: IOException) {
            throw SourceError.Network(e)
        } catch (e: retrofit2.HttpException) {
            throw when (e.code()) {
                401, 403 -> SourceError.Unauthorized("")
                429 -> SourceError.RateLimited(null)
                in 500..599 -> SourceError.Server(e.code(), "upstream error")
                else -> SourceError.Server(e.code(), "unexpected status")
            }
        }

        val items: List<FeedItem> = notes.map(mapper::map)
        val next = if (notes.size < limit) null else notes.lastOrNull()?.id?.let(::PageCursor)
        return Page(items, next)
    }
}
