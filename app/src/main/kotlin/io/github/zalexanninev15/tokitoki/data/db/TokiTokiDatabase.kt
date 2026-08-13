package io.github.zalexanninev15.tokitoki.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, FeedItemEntity::class, ReadQueueEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class TokiTokiDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun feedDao(): FeedDao
    abstract fun readQueueDao(): ReadQueueDao

    companion object {
        fun create(context: Context): TokiTokiDatabase =
            Room.databaseBuilder(context, TokiTokiDatabase::class.java, "tokitoki.db")
                // The cache is disposable; a schema change may drop it rather than
                // shipping a migration for data that will be refetched anyway.
                .fallbackToDestructiveMigration()
                .build()
    }
}
