package io.github.zalexanninev15.tokitoki.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY source, handle")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE enabled = 1")
    suspend fun enabled(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE localId = :localId")
    suspend fun byId(localId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE accounts SET enabled = :enabled WHERE localId = :localId")
    suspend fun setEnabled(localId: String, enabled: Boolean)

    @Query("DELETE FROM accounts WHERE localId = :localId")
    suspend fun delete(localId: String)
}

@Dao
interface FeedDao {
    @Query(
        """
        SELECT * FROM feed_items
        WHERE accountLocalId IN (:accountIds)
        ORDER BY createdAt DESC, id ASC
        LIMIT :limit
        """,
    )
    fun observeFeed(accountIds: List<String>, limit: Int = 500): Flow<List<FeedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<FeedItemEntity>): List<Long>

    @Query("SELECT MIN(createdAt) FROM feed_items WHERE accountLocalId = :accountId")
    suspend fun oldestTimestamp(accountId: String): Long?

    @Query("UPDATE feed_items SET locallyRead = 1 WHERE id IN (:ids)")
    suspend fun markLocallyRead(ids: List<String>)

    @Query("UPDATE feed_items SET remoteConfirmed = 1 WHERE id IN (:ids)")
    suspend fun markRemoteConfirmed(ids: List<String>)

    @Query("DELETE FROM feed_items WHERE accountLocalId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    /** Keeps the cache bounded; the feed is a window, not an archive. */
    @Query(
        """
        DELETE FROM feed_items
        WHERE id NOT IN (SELECT id FROM feed_items ORDER BY createdAt DESC LIMIT :keep)
        """,
    )
    suspend fun trimTo(keep: Int)
}

@Dao
interface ReadQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entries: List<ReadQueueEntity>)

    @Query("SELECT * FROM read_queue WHERE nextAttemptAt <= :now ORDER BY enqueuedAt LIMIT :limit")
    suspend fun due(now: Long, limit: Int = 200): List<ReadQueueEntity>

    @Query("SELECT COUNT(*) FROM read_queue")
    fun observeDepth(): Flow<Int>

    @Query("DELETE FROM read_queue WHERE itemId IN (:ids)")
    suspend fun remove(ids: List<String>)

    @Query(
        "UPDATE read_queue SET attempts = :attempts, nextAttemptAt = :nextAttemptAt, " +
            "lastError = :error WHERE accountLocalId = :accountId",
    )
    suspend fun reschedule(accountId: String, attempts: Int, nextAttemptAt: Long, error: String?)

    @Query("DELETE FROM read_queue WHERE accountLocalId = :accountId")
    suspend fun clearForAccount(accountId: String)
}
