package io.github.zalexanninev15.tokitoki

import android.app.Application
import io.github.zalexanninev15.tokitoki.data.db.TokiTokiDatabase
import io.github.zalexanninev15.tokitoki.data.prefs.SettingsStore
import io.github.zalexanninev15.tokitoki.data.repo.AuthService
import io.github.zalexanninev15.tokitoki.data.repo.FeedRepository
import io.github.zalexanninev15.tokitoki.data.secure.SecureStore
import io.github.zalexanninev15.tokitoki.data.sync.ReadSyncWorker

/**
 * Manual dependency container.
 *
 * A DI framework would add an annotation processor and a version-compatibility surface
 * for a graph this small. Constructor injection is still used everywhere; this is just
 * the composition root.
 */
class AppContainer(app: Application) {
    val database: TokiTokiDatabase by lazy { TokiTokiDatabase.create(app) }
    val secureStore: SecureStore by lazy { SecureStore(app) }
    val settingsStore: SettingsStore by lazy { SettingsStore(app) }

    val feedRepository: FeedRepository by lazy {
        FeedRepository(
            accountDao = database.accountDao(),
            feedDao = database.feedDao(),
            readQueueDao = database.readQueueDao(),
            secureStore = secureStore,
        )
    }

    val authService: AuthService by lazy {
        AuthService(accountDao = database.accountDao(), secureStore = secureStore)
    }
}

class TokiTokiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReadSyncWorker.schedule(this)
    }
}
