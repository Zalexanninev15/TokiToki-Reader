package io.github.zalexanninev15.tokitoki.data.misskey.api

import io.github.zalexanninev15.tokitoki.data.misskey.dto.DriveFileDto
import io.github.zalexanninev15.tokitoki.data.misskey.dto.MisskeyUserDto
import io.github.zalexanninev15.tokitoki.data.misskey.dto.NoteDto
import io.github.zalexanninev15.tokitoki.data.misskey.internal.MfmParser
import io.github.zalexanninev15.tokitoki.domain.model.Author
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.MediaAttachment
import io.github.zalexanninev15.tokitoki.domain.model.MediaKind
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import java.time.Instant
import java.time.format.DateTimeParseException

class MisskeyPostMapper(
    private val accountLocalId: String,
    private val instanceHost: String,
) {

    fun map(dto: NoteDto): FeedItem {
        val boosted = if (dto.isPureRenote) dto.renote else null
        val body = boosted ?: dto

        return FeedItem(
            id = FeedItemId(SourceKind.MISSKEY, accountLocalId, dto.id),
            author = mapAuthor(body.user),
            createdAtEpochMillis = parseTimestamp(dto.createdAt),
            text = MfmParser.parse(body.text),
            media = body.files.map(::mapFile),
            contentWarning = body.cw?.takeIf { it.isNotBlank() },
            canonicalUrl = canonicalUrl(body),
            inReplyTo = body.replyId?.let { FeedItemId(SourceKind.MISSKEY, accountLocalId, it) },
            quoted = if (dto.isQuote) dto.renote?.let { map(it) } else null,
            reposted = boosted?.let { map(it) },
            repostedBy = boosted?.let { mapAuthor(dto.user) },
        )
    }

    private fun mapAuthor(dto: MisskeyUserDto): Author = Author(
        remoteId = dto.id,
        displayName = dto.name?.takeIf { it.isNotBlank() } ?: dto.username,
        handle = "@${dto.username}@${dto.host ?: instanceHost}",
        avatarUrl = dto.avatarUrl,
        isBot = dto.isBot,
    )

    private fun mapFile(dto: DriveFileDto): MediaAttachment = MediaAttachment(
        kind = when {
            dto.type == "image/gif" -> MediaKind.ANIMATED
            dto.type.startsWith("image/") -> MediaKind.IMAGE
            dto.type.startsWith("video/") -> MediaKind.VIDEO
            dto.type.startsWith("audio/") -> MediaKind.AUDIO
            else -> MediaKind.FILE
        },
        url = dto.url,
        previewUrl = dto.thumbnailUrl ?: dto.url,
        description = dto.comment,
        width = dto.properties?.width,
        height = dto.properties?.height,
        blurHash = dto.blurhash,
        sizeBytes = dto.size,
    )

    /**
     * A remote note carries the origin server's URL; a local one has neither `url` nor
     * `uri` and its permalink has to be constructed from the instance host.
     */
    private fun canonicalUrl(dto: NoteDto): String =
        dto.url ?: dto.uri ?: "https://$instanceHost/notes/${dto.id}"

    private fun parseTimestamp(raw: String): Long = try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        System.currentTimeMillis()
    }
}
