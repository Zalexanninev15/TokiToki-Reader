package io.github.zalexanninev15.tokitoki.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Access tokens and pending auth state.
 *
 * Backed by EncryptedSharedPreferences with an AES-256-GCM master key held in the Android
 * Keystore, so the key material never leaves the secure hardware and the on-disk file is
 * useless if pulled off the device. Nothing here is ever logged.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "tokitoki_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun putToken(accountLocalId: String, token: String) {
        prefs.edit().putString("token:$accountLocalId", token).apply()
    }

    fun token(accountLocalId: String): String? = prefs.getString("token:$accountLocalId", null)

    fun clearAccount(accountLocalId: String) {
        prefs.edit()
            .remove("token:$accountLocalId")
            .remove("cursor:$accountLocalId")
            .apply()
    }

    /** Last Mastodon marker we successfully wrote, so the cursor never regresses. */
    fun cursor(accountLocalId: String): String? = prefs.getString("cursor:$accountLocalId", null)

    fun putCursor(accountLocalId: String, cursor: String) {
        prefs.edit().putString("cursor:$accountLocalId", cursor).apply()
    }

    /** Short-lived OAuth state: client credentials and the PKCE verifier. */
    fun putPending(key: String, value: String) {
        prefs.edit().putString("pending:$key", value).apply()
    }

    fun pending(key: String): String? = prefs.getString("pending:$key", null)

    fun clearPending() {
        val doomed = prefs.all.keys.filter { it.startsWith("pending:") }
        prefs.edit().apply { doomed.forEach(::remove) }.apply()
    }
}
