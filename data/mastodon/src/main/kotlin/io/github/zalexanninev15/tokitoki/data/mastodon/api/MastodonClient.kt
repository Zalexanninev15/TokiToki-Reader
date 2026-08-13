package io.github.zalexanninev15.tokitoki.data.mastodon.api

import io.github.zalexanninev15.tokitoki.data.mastodon.internal.LinkHeaderParser
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.repository.Page
import io.github.zalexanninev15.tokitoki.domain.repository.PageCursor
import io.github.zalexanninev15.tokitoki.domain.repository.SourceError
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

object MastodonClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun create(baseUrl: String, client: OkHttpClient = defaultClient()): MastodonApi =
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            // asConverterFactory is an extension on Json: Kotlin cannot call it in the
            // static two-argument form, only as a receiver call.
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MastodonApi::class.java)

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/**
 * Reads the home timeline. Pagination follows the `Link` header verbatim rather than
 * rebuilding query strings, which is the only approach that survives instances hosted
 * under a path prefix.
 */
class MastodonRemoteSource(
    private val api: MastodonApi,
    private val bearer: String,
    private val mapper: MastodonPostMapper,
) {

    suspend fun loadPage(cursor: PageCursor?, limit: Int = PAGE_SIZE): Page {
        val response = try {
            if (cursor == null) api.homeTimeline(bearer, limit = limit)
            else api.timelinePage(bearer, cursor.raw)
        } catch (e: IOException) {
            throw SourceError.Network(e)
        }

        if (!response.isSuccessful) throw response.toError()

        val items: List<FeedItem> = response.body().orEmpty().map(mapper::map)
        val next = LinkHeaderParser.parse(response.headers()["Link"]).next
        return Page(items, next?.let(::PageCursor))
    }

    private fun Response<*>.toError(): SourceError = when (code()) {
        401 -> SourceError.Unauthorized("")
        403 -> SourceError.Forbidden("access denied")
        404 -> SourceError.NotFound("timeline not found")
        429 -> SourceError.RateLimited(
            headers()["X-RateLimit-Reset"]?.let { 60_000L }
                ?: headers()["Retry-After"]?.toLongOrNull()?.times(1_000),
        )
        in 500..599 -> SourceError.Server(code(), "upstream error")
        else -> SourceError.Server(code(), "unexpected status")
    }

    private companion object {
        /** Small pages keep the first screen fast and make infinite scroll feel smooth. */
        const val PAGE_SIZE = 10
    }
}
