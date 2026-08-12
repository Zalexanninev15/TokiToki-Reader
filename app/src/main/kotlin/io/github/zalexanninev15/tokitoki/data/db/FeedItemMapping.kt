package io.github.zalexanninev15.tokitoki.data.db

import io.github.zalexanninev15.tokitoki.domain.model.Author
import io.github.zalexanninev15.tokitoki.domain.model.FeedItem
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import io.github.zalexanninev15.tokitoki.domain.model.MediaAttachment
import io.github.zalexanninev15.tokitoki.domain.model.MediaKind
import io.github.zalexanninev15.tokitoki.domain.model.RichText
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import io.github.zalexanninev15.tokitoki.domain.model.SpanKind
import io.github.zalexanninev15.tokitoki.domain.model.TextSpan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SpanRecord(val start: Int, val end: Int, val kind: String, val target: String?)

@Serializable
private data class MediaRecord(
    val kind: String,
    val url: String?,
    val previewUrl: String?,
    val description: String?,
    val width: Int?,
    val height: Int?,
    val blurHash: String?,
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Flattens the domain item for storage.
 *
 * Boost and quote nesting is deliberately collapsed: the cache stores what is rendered in
 * the list, and the full structure is re-fetched when the detail screen needs it. Storing
 * an arbitrarily deep tree in a relational cache buys nothing for a timeline.
 */
fun FeedItem.toEntity(): FeedItemEntity {
    val body = displayed
    return FeedItemEntity(
        id = id.value,
        accountLocalId = id.accountLocalId,
        source = id.source.name,
        remoteId = id.remoteId,
        authorName = body.author.displayName,
        authorHandle = body.author.handle,
        authorAvatarUrl = body.author.avatarUrl,
        createdAt = createdAtEpochMillis,
        text = body.text.plain,
        spansJson = json.encodeToString(
            body.text.spans.map { SpanRecord(it.start, it.end, it.kind.name, it.target) },
        ),
        mediaJson = json.encodeToString(
            body.media.map {
                MediaRecord(
                    it.kind.name, it.url, it.previewUrl, it.description,
                    it.width, it.height, it.blurHash,
                )
            },
        ),
        contentWarning = body.contentWarning,
        canonicalUrl = body.canonicalUrl,
        repostedByName = repostedBy?.displayName,
    )
}

fun FeedItemEntity.toDomain(): FeedItem {
    val spans = runCatching {
        json.decodeFromString<List<SpanRecord>>(spansJson).mapNotNull { record ->
            val kind = SpanKind.entries.firstOrNull { it.name == record.kind } ?: return@mapNotNull null
            TextSpan(record.start, record.end, kind, record.target)
        }
    }.getOrDefault(emptyList())

    val media = runCatching {
        json.decodeFromString<List<MediaRecord>>(mediaJson).map { record ->
            MediaAttachment(
                kind = MediaKind.entries.firstOrNull { it.name == record.kind } ?: MediaKind.FILE,
                url = record.url,
                previewUrl = record.previewUrl,
                description = record.description,
                width = record.width,
                height = record.height,
                blurHash = record.blurHash,
            )
        }
    }.getOrDefault(emptyList())

    return FeedItem(
        id = FeedItemId(
            source = SourceKind.entries.firstOrNull { it.name == source } ?: SourceKind.MASTODON,
            accountLocalId = accountLocalId,
            remoteId = remoteId,
        ),
        author = Author(remoteId, authorName, authorHandle, authorAvatarUrl),
        createdAtEpochMillis = createdAt,
        text = RichText(text, spans),
        media = media,
        contentWarning = contentWarning,
        canonicalUrl = canonicalUrl,
        repostedBy = repostedByName?.let { Author("", it, it, null) },
    )
}
