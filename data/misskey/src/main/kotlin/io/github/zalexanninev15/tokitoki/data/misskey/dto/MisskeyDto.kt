package io.github.zalexanninev15.tokitoki.data.misskey.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MiAuthCheckDto(val ok: Boolean = false, val token: String? = null, val user: MisskeyUserDto? = null)

@Serializable
data class NodeInfoDto(val software: NodeInfoSoftwareDto? = null)

@Serializable
data class NodeInfoSoftwareDto(val name: String? = null, val version: String? = null)

@Serializable
data class MisskeyUserDto(
    val id: String,
    val username: String,
    val name: String? = null,
    val host: String? = null,
    val avatarUrl: String? = null,
    val isBot: Boolean = false,
)

@Serializable
data class DriveFileDto(
    val id: String,
    val type: String,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val comment: String? = null,
    val size: Long? = null,
    val blurhash: String? = null,
    val properties: DriveFilePropertiesDto? = null,
    val isSensitive: Boolean = false,
)

@Serializable
data class DriveFilePropertiesDto(val width: Int? = null, val height: Int? = null)

@Serializable
data class NoteDto(
    val id: String,
    val createdAt: String,
    val text: String? = null,
    val cw: String? = null,
    val user: MisskeyUserDto,
    val userId: String,
    val replyId: String? = null,
    val renoteId: String? = null,
    val renote: NoteDto? = null,
    val reply: NoteDto? = null,
    val files: List<DriveFileDto> = emptyList(),
    val uri: String? = null,
    val url: String? = null,
    val visibility: String = "public",
) {
    /**
     * Misskey overloads one field for two concepts: a renote with no body of its own is a
     * boost, while a renote carrying text or files is a quote. Nothing in the payload says
     * which, so it has to be derived.
     */
    val isPureRenote: Boolean
        get() = renote != null && text.isNullOrBlank() && files.isEmpty() && cw == null

    val isQuote: Boolean get() = renote != null && !isPureRenote
}

@Serializable
data class TimelineRequest(
    val i: String,
    val limit: Int = 30,
    val sinceId: String? = null,
    val untilId: String? = null,
)

@Serializable
data class CredentialRequest(val i: String)

@Serializable
data class NotificationsReadRequest(val i: String)

@Serializable
data class AntennaNotesRequest(
    val i: String,
    val antennaId: String,
    val limit: Int = 30,
    val untilId: String? = null,
    /** Reading antenna notes clears the server-side unread flag for that antenna. */
    val markAsRead: Boolean = true,
)

@Serializable
data class AntennaDto(
    val id: String,
    val name: String,
    val hasUnreadNote: Boolean = false,
)

@Serializable
data class FollowingRequest(
    val i: String,
    val userId: String,
    val limit: Int = 100,
    val untilId: String? = null,
)

@Serializable
data class FollowingDto(
    val id: String,
    val followeeId: String? = null,
    val followee: MisskeyUserDto? = null,
)
