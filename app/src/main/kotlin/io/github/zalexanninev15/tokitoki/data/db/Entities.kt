package io.github.zalexanninev15.tokitoki.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val localId: String,
    val source: String,
    val host: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
    /** Id of this user ON their own server. Needed for the following/subscriptions call. */
    val remoteUserId: String = "",
    /** Toggled from the Feeds screen; a disabled account keeps its cache and token. */
    val enabled: Boolean = true,
)

@Entity(
    tableName = "feed_items",
    indices = [Index("accountLocalId"), Index("createdAt")],
)
data class FeedItemEntity(
    @PrimaryKey val id: String,
    val accountLocalId: String,
    val source: String,
    val remoteId: String,
    /**
     * The author's id on their server — NOT the post id. Restoring Author from the post
     * id is what made "open profile" from the feed return 404 while the same profile
     * opened fine from the subscriptions list.
     */
    val authorRemoteId: String = "",
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String?,
    val createdAt: Long,
    val text: String,
    /** Spans and media are stored as JSON: they are read and written as whole blobs. */
    val spansJson: String,
    val mediaJson: String,
    val contentWarning: String?,
    val canonicalUrl: String?,
    val repostedByName: String?,
    val favourited: Boolean = false,
    val boosted: Boolean = false,
    val favouriteCount: Int = 0,
    val boostCount: Int = 0,
    val replyCount: Int = 0,
    val myReaction: String? = null,
    @ColumnInfo(defaultValue = "0") val locallyRead: Boolean = false,
    @ColumnInfo(defaultValue = "0") val remoteConfirmed: Boolean = false,
)

/**
 * Pending read acknowledgements.
 *
 * A separate table rather than a flag on the item, because it must survive process death
 * and be drained by WorkManager after the app is gone.
 */
@Entity(tableName = "read_queue")
data class ReadQueueEntity(
    @PrimaryKey val itemId: String,
    val accountLocalId: String,
    val remoteId: String,
    val enqueuedAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val lastError: String? = null,
)
