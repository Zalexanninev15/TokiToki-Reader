package io.github.zalexanninev15.tokitoki.data.mastodon.api

import io.github.zalexanninev15.tokitoki.data.mastodon.dto.AppRegistrationDto
import io.github.zalexanninev15.tokitoki.data.mastodon.dto.MarkersDto
import io.github.zalexanninev15.tokitoki.data.mastodon.dto.StatusDto
import io.github.zalexanninev15.tokitoki.data.mastodon.dto.TokenDto
import io.github.zalexanninev15.tokitoki.data.mastodon.dto.MastodonAccountDto
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The Mastodon REST surface this app needs.
 *
 * Every call is scoped to a single instance, so the Retrofit base URL is built per
 * account rather than being a constant.
 */
interface MastodonApi {

    /**
     * Registers this app on an arbitrary instance. Mastodon allows dynamic registration,
     * which is what makes "type your instance URL and log in" possible without the
     * developer pre-registering on every server in the fediverse.
     */
    @FormUrlEncoded
    @POST("api/v1/apps")
    suspend fun registerApp(
        @Field("client_name") clientName: String,
        @Field("redirect_uris") redirectUri: String,
        @Field("scopes") scopes: String,
        @Field("website") website: String? = null,
    ): AppRegistrationDto

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeToken(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String?,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
        /** PKCE verifier. Supported from Mastodon 4.3; harmless on servers that ignore it. */
        @Field("code_verifier") codeVerifier: String?,
        @Field("scope") scope: String,
    ): TokenDto

    @FormUrlEncoded
    @POST("oauth/revoke")
    suspend fun revokeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String?,
        @Field("token") token: String,
    ): Response<Unit>

    @GET("api/v1/accounts/verify_credentials")
    suspend fun verifyCredentials(@Header("Authorization") bearer: String): MastodonAccountDto

    /**
     * Returned as a [Response] rather than a bare list because the `Link` header carries
     * the pagination cursors and the `X-RateLimit-*` headers drive the backoff.
     */
    @GET("api/v1/timelines/home")
    suspend fun homeTimeline(
        @Header("Authorization") bearer: String,
        @Query("max_id") maxId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int = 40,
    ): Response<List<StatusDto>>

    /** Follows a `Link: rel="next"` URL verbatim instead of rebuilding the query. */
    @GET
    suspend fun timelinePage(
        @Header("Authorization") bearer: String,
        @Url url: String,
    ): Response<List<StatusDto>>

    /**
     * Read markers. Requires `read:statuses`.
     *
     * The `timeline[]` array parameter is unusual for this API; omitting it returns an
     * empty object rather than everything.
     */
    @GET("api/v1/markers")
    suspend fun getMarkers(
        @Header("Authorization") bearer: String,
        @Query("timeline[]") timelines: List<String> = listOf("home", "notifications"),
    ): Response<MarkersDto>

    /**
     * Saves the home timeline position. Requires `write:statuses`.
     *
     * On a version conflict the server rejects the write, so the caller must re-read the
     * marker and retry rather than assuming success.
     */
    @FormUrlEncoded
    @POST("api/v1/markers")
    suspend fun setHomeMarker(
        @Header("Authorization") bearer: String,
        @Field("home[last_read_id]") lastReadId: String,
    ): Response<MarkersDto>
}
