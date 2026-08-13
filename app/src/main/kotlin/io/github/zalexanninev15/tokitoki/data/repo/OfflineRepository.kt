package io.github.zalexanninev15.tokitoki.data.repo

import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.zalexanninev15.tokitoki.data.db.FeedDao
import io.github.zalexanninev15.tokitoki.data.db.toDomain
import io.github.zalexanninev15.tokitoki.domain.model.FeedItemId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OfflineResult(val posts: Int, val images: Int, val failedImages: Int)

/**
 * Saving posts for offline reading.
 *
 * Text is already in Room after a refresh, so "saving" a post is really two things:
 * marking the row so the cache trim stops evicting it, and pulling its images into
 * Coil's disk cache. Without the first part a saved post silently disappears once two
 * thousand newer ones arrive; without the second the text survives but the pictures do
 * not, which is the more visible half of the failure.
 */
class OfflineRepository(
    private val context: Context,
    private val feedDao: FeedDao,
    private val imageLoader: ImageLoader,
) {

    suspend fun savePost(id: FeedItemId): OfflineResult = withContext(Dispatchers.IO) {
        feedDao.setPinned(id.value, true)
        val entity = feedDao.snapshot(listOf(id.accountLocalId))
            .firstOrNull { it.id == id.value }
        val urls = entity?.toDomain()?.imageUrls().orEmpty()
        val downloaded = prefetch(urls)
        OfflineResult(posts = 1, images = downloaded.first, failedImages = downloaded.second)
    }

    suspend fun removePost(id: FeedItemId) = withContext(Dispatchers.IO) {
        feedDao.setPinned(id.value, false)
    }

    /** Saves the whole cached window for the given accounts. */
    suspend fun saveFeed(accountIds: List<String>): OfflineResult = withContext(Dispatchers.IO) {
        if (accountIds.isEmpty()) return@withContext OfflineResult(0, 0, 0)

        val entities = feedDao.snapshot(accountIds)
        feedDao.pinAll(accountIds)

        val urls = entities.flatMap { it.toDomain().imageUrls() }.distinct()
        val downloaded = prefetch(urls)
        OfflineResult(
            posts = entities.size,
            images = downloaded.first,
            failedImages = downloaded.second,
        )
    }

    suspend fun clearSaved() = withContext(Dispatchers.IO) { feedDao.unpinAll() }

    suspend fun savedCount(): Int = withContext(Dispatchers.IO) { feedDao.pinnedCount() }

    /** @return downloaded to failed. */
    private suspend fun prefetch(urls: List<String>): Pair<Int, Int> {
        var ok = 0
        var failed = 0
        // Sequential on purpose: firing a hundred parallel downloads at one instance is
        // a good way to collect a rate limit instead of a cached feed.
        for (url in urls.take(MAX_IMAGES)) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build()
            if (imageLoader.execute(request).drawable != null) ok++ else failed++
        }
        return ok to failed
    }

    private companion object {
        /** A whole timeline of images can be hundreds of megabytes; this bounds it. */
        const val MAX_IMAGES = 300
    }
}

private fun io.github.zalexanninev15.tokitoki.domain.model.FeedItem.imageUrls(): List<String> =
    (media + (reposted?.media.orEmpty()))
        .mapNotNull { it.url ?: it.previewUrl }
        .distinct()
