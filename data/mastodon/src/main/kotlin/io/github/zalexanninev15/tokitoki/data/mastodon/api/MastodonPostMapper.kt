package io.github.zalexanninev15.tokitoki.data.mastodon.api

import io.github.zalexanninev15.tokitoki.data.mastodon.dto.MastodonAccountDto
import io.github.zalexanninev15.tokitoki.data.mastodon.dto.MediaAttachmentDto
import io.github.zalexanninev15.tokitoki.data.mastodon.dto.StatusDto
import io.github.zalexanninev15.tokitoki.data.mastodon.internal.MastodonHtmlParser
import io.github.zalexanninev15.tokitoki.domain.model.Author
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.MediaAttachment
import io.github.zalexanninev15.tokitoki.domain.model.MediaKind
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Maps Mastodon DTOs onto the shared domain model.
 *
 * Thin by design: everything with real logic in it (HTML handling) lives in
 * [MastodonHtmlParser], where it is unit tested without a network or serialization
 * dependency.
 */
class MastodonPostMapper(
    private val accountLocalId: String,
    private val instanceHost: String,
) {

    fun map(dto: StatusDto): FeedItem {
        val boosted = dto.reblog
        val body = boosted ?: dto

        return FeedItem(
            id = FeedItemId(SourceKind.MASTODON, accountLocalId, dto.id),
            author = mapAuthor(body.account),
            createdAtEpochMillis = parseTimestamp(dto.createdAt),
            text = MastodonHtmlParser.parse(body.content),
            media = body.mediaAttachments.map(::mapMedia),
            contentWarning = body.spoilerText.takeIf { it.isNotBlank() },
            canonicalUrl = body.url ?: body.uri,
            inReplyTo = body.inReplyToId?.let {
                FeedItemId(SourceKind.MASTODON, accountLocalId, it)
            },
            quoted = body.quote?.let { map(it) },
            reposted = boosted?.let { map(it) },
            repostedBy = boosted?.let { mapAuthor(dto.account) },
        )
    }

    private fun mapAuthor(dto: MastodonAccountDto): Author = Author(
        remoteId = dto.id,
        displayName = dto.displayName.ifBlank { dto.username },
        // `acct` is bare for local users and user@host for remote ones; normalise both.
        handle = if ('@' in dto.acct) "@${dto.acct}" else "@${dto.acct}@$instanceHost",
        avatarUrl = dto.avatar,
        isBot = dto.bot,
    )

    private fun mapMedia(dto: MediaAttachmentDto): MediaAttachment {
        val size = dto.meta?.original
        return MediaAttachment(
            kind = when (dto.type) {
                "image" -> MediaKind.IMAGE
                "gifv" -> MediaKind.ANIMATED
                "video" -> MediaKind.VIDEO
                "audio" -> MediaKind.AUDIO
                else -> MediaKind.FILE
            },
            url = dto.url,
            previewUrl = dto.previewUrl,
            description = dto.description,
            width = size?.width,
            height = size?.height,
            blurHash = dto.blurhash,
        )
    }

    /**
     * Mastodon sends ISO-8601 UTC. A malformed value must not take down the whole page,
     * so it degrades to "now" and the item simply sorts to the top.
     */
    private fun parseTimestamp(raw: String): Long = try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        System.currentTimeMillis()
    }
}
