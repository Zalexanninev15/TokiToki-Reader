package io.github.zalexanninev15.tokitoki.data.repo

import android.net.Uri
import io.github.zalexanninev15.tokitoki.data.db.AccountDao
import io.github.zalexanninev15.tokitoki.data.db.AccountEntity
import io.github.zalexanninev15.tokitoki.data.mastodon.api.MastodonClientFactory
import io.github.zalexanninev15.tokitoki.data.misskey.api.MisskeyClientFactory
import io.github.zalexanninev15.tokitoki.data.misskey.dto.CredentialRequest
import io.github.zalexanninev15.tokitoki.data.secure.SecureStore
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

private const val REDIRECT_URI = "tokitoki://auth/callback"
private const val CLIENT_NAME = "TokiToki Reader"
private const val MASTODON_SCOPES = "read:statuses read:accounts read:notifications write:statuses"

/**
 * Sign-in for both fediverse services.
 *
 * Neither requires the developer to pre-register anywhere: Mastodon supports dynamic app
 * registration, Misskey's MiAuth needs no registration at all. That is what makes
 * "type any instance URL and log in" work across thousands of servers.
 */
class AuthService(
    private val accountDao: AccountDao,
    private val secureStore: SecureStore,
) {

    /** Builds the URL to open in a Custom Tab and stashes the state needed on return. */
    suspend fun beginMastodon(instanceUrl: String): String {
        val base = normalise(instanceUrl)
        val api = MastodonClientFactory.create(base)

        val registration = api.registerApp(
            clientName = CLIENT_NAME,
            redirectUri = REDIRECT_URI,
            scopes = MASTODON_SCOPES,
        )

        val verifier = randomUrlSafe(64)
        val challenge = s256(verifier)

        secureStore.putPending("host", base)
        secureStore.putPending("source", SourceKind.MASTODON.name)
        secureStore.putPending("clientId", registration.clientId)
        registration.clientSecret?.let { secureStore.putPending("clientSecret", it) }
        secureStore.putPending("verifier", verifier)

        return Uri.parse(base).buildUpon()
            .appendPath("oauth")
            .appendPath("authorize")
            .appendQueryParameter("client_id", registration.clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", MASTODON_SCOPES)
            // PKCE is honoured from Mastodon 4.3 and ignored harmlessly by older servers,
            // so it costs nothing and protects the code on servers that support it.
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    fun beginMisskey(instanceUrl: String): String {
        val base = normalise(instanceUrl)
        val session = UUID.randomUUID().toString()

        secureStore.putPending("host", base)
        secureStore.putPending("source", SourceKind.MISSKEY.name)
        secureStore.putPending("session", session)

        return Uri.parse(base).buildUpon()
            .appendPath("miauth")
            .appendPath(session)
            .appendQueryParameter("name", CLIENT_NAME)
            .appendQueryParameter("callback", REDIRECT_URI)
            .appendQueryParameter("permission", "read:account,read:notifications")
            .build()
            .toString()
    }

    /** Completes whichever flow is pending. Returns the new account id. */
    suspend fun complete(callback: Uri): Result<String> = runCatching {
        val base = secureStore.pending("host") ?: error("no pending sign-in")
        val source = secureStore.pending("source") ?: error("no pending sign-in")

        val localId = when (SourceKind.valueOf(source)) {
            SourceKind.MASTODON -> completeMastodon(base, callback)
            SourceKind.MISSKEY -> completeMisskey(base)
            SourceKind.TELEGRAM -> error("Telegram is not supported yet")
        }
        secureStore.clearPending()
        localId
    }.onFailure { secureStore.clearPending() }

    private suspend fun completeMastodon(base: String, callback: Uri): String {
        val code = callback.getQueryParameter("code")
            ?: error(callback.getQueryParameter("error") ?: "authorization denied")

        val api = MastodonClientFactory.create(base)
        val token = api.exchangeToken(
            clientId = secureStore.pending("clientId") ?: error("missing client id"),
            clientSecret = secureStore.pending("clientSecret"),
            redirectUri = REDIRECT_URI,
            code = code,
            codeVerifier = secureStore.pending("verifier"),
            scope = MASTODON_SCOPES,
        )

        val me = api.verifyCredentials("Bearer ${token.accessToken}")
        val host = Uri.parse(base).host ?: base
        val localId = "mastodon:$host:${me.id}"

        secureStore.putToken(localId, token.accessToken)
        accountDao.upsert(
            AccountEntity(
                localId = localId,
                source = SourceKind.MASTODON.name,
                host = host,
                handle = if ('@' in me.acct) "@${me.acct}" else "@${me.acct}@$host",
                displayName = me.displayName.ifBlank { me.username },
                avatarUrl = me.avatar,
            ),
        )
        return localId
    }

    private suspend fun completeMisskey(base: String): String {
        val session = secureStore.pending("session") ?: error("missing session")
        val api = MisskeyClientFactory.create(base)

        val check = api.miAuthCheck(session)
        val token = check.token.takeIf { check.ok && !it.isNullOrBlank() }
            ?: error("instance rejected the sign-in")

        val me = api.currentUser(CredentialRequest(token))
        val host = Uri.parse(base).host ?: base
        val localId = "misskey:$host:${me.id}"

        secureStore.putToken(localId, token)
        accountDao.upsert(
            AccountEntity(
                localId = localId,
                source = SourceKind.MISSKEY.name,
                host = host,
                handle = "@${me.username}@${me.host ?: host}",
                displayName = me.name?.takeIf { it.isNotBlank() } ?: me.username,
                avatarUrl = me.avatarUrl,
            ),
        )
        return localId
    }

    companion object {
        /** Accepts "example.social", "https://example.social/", "@me@example.social". */
        fun normalise(raw: String): String {
            var value = raw.trim().removeSuffix("/")
            if (value.startsWith("@")) value = value.substringAfterLast('@')
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "https://$value"
            }
            return value
        }

        private fun randomUrlSafe(bytes: Int): String {
            val buffer = ByteArray(bytes)
            SecureRandom().nextBytes(buffer)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
        }

        private fun s256(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
