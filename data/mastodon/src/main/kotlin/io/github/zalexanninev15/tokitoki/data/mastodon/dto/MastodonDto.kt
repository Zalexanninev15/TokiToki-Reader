package io.github.zalexanninev15.tokitoki.data.mastodon.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppRegistrationDto(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class MastodonAccountDto(
    val id: String,
    val username: String,
    val acct: String,
    @SerialName("display_name") val displayName: String = "",
    val avatar: String? = null,
    val bot: Boolean = false,
    val url: String? = null,
)

@Serializable
data class MediaAttachmentDto(
    val id: String,
    /** image | gifv | video | audio | unknown */
    val type: String,
    val url: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    val description: String? = null,
    val blurhash: String? = null,
    val meta: MediaMetaDto? = null,
)

@Serializable
data class MediaMetaDto(val original: MediaSizeDto? = null, val small: MediaSizeDto? = null)

@Serializable
data class MediaSizeDto(val width: Int? = null, val height: Int? = null)

@Serializable
data class StatusDto(
    val id: String,
    val uri: String,
    /** Canonical public permalink. Absent on some Mastodon-compatible servers. */
    val url: String? = null,
    @SerialName("created_at") val createdAt: String,
    val account: MastodonAccountDto,
    val content: String = "",
    @SerialName("spoiler_text") val spoilerText: String = "",
    val sensitive: Boolean = false,
    @SerialName("in_reply_to_id") val inReplyToId: String? = null,
    val reblog: StatusDto? = null,
    @SerialName("media_attachments") val mediaAttachments: List<MediaAttachmentDto> = emptyList(),
    /** Present on Akkoma/Pleroma and on Mastodon builds with quote support. */
    val quote: StatusDto? = null,
    val favourited: Boolean = false,
    val reblogged: Boolean = false,
    @SerialName("favourites_count") val favouritesCount: Int = 0,
    @SerialName("reblogs_count") val reblogsCount: Int = 0,
    @SerialName("replies_count") val repliesCount: Int = 0,
)

/**
 * Response of `GET|POST /api/v1/markers`. Both keys are optional: a fresh account has no
 * marker at all, and a Mastodon-compatible server may implement only one of them.
 */
@Serializable
data class MarkersDto(
    val home: MarkerDto? = null,
    val notifications: MarkerDto? = null,
)

@Serializable
data class MarkerDto(
    @SerialName("last_read_id") val lastReadId: String,
    /** Optimistic lock counter; increments on every successful update. */
    val version: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)
