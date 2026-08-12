package io.github.zalexanninev15.tokitoki.data.misskey.internal

/**
 * Which Misskey-family server we are talking to, and what it can do.
 *
 * This matters more here than on the Mastodon side: forks diverge in the auth mechanism
 * they accept and in whether quote-renotes and channels exist at all, and they announce
 * themselves through `nodeinfo` with a software name that is not `misskey`.
 */
data class MisskeyFlavour(
    val softwareName: String,
    val version: SemVer?,
    val authMechanism: AuthMechanism,
    val supportsOAuth: Boolean,
) {
    enum class AuthMechanism {
        /** `/miauth/{uuid}` — no app registration needed. Preferred for a mobile client. */
        MI_AUTH,

        /** OAuth 2.0, added in Misskey 2023.9. */
        OAUTH2,

        /** `app/create` + `auth/session/generate`. Only for genuinely old servers. */
        LEGACY_APP,
    }

    companion object {
        /** MiAuth landed in Misskey 12.x; anything older gets the legacy flow. */
        private val MIAUTH_SINCE = SemVer(12, 0, 0)
        private val OAUTH_SINCE = SemVer(2023, 9, 0)

        private val KNOWN_FORKS = setOf(
            "misskey", "sharkey", "firefish", "iceshrimp", "calckey", "cherrypick", "foundkey",
        )

        fun from(softwareName: String?, versionString: String?): MisskeyFlavour {
            val name = softwareName?.lowercase()?.trim().orEmpty()
            val version = SemVer.parse(versionString)
            val oauth = version != null && version >= OAUTH_SINCE && name == "misskey"
            val mechanism = when {
                version == null -> AuthMechanism.MI_AUTH // optimistic; falls back on failure
                version >= MIAUTH_SINCE -> AuthMechanism.MI_AUTH
                else -> AuthMechanism.LEGACY_APP
            }
            return MisskeyFlavour(
                softwareName = name.ifEmpty { "unknown" },
                version = version,
                authMechanism = mechanism,
                supportsOAuth = oauth,
            )
        }

        fun isMisskeyFamily(softwareName: String?): Boolean =
            softwareName?.lowercase()?.trim() in KNOWN_FORKS
    }
}

/**
 * Lenient version parser. Misskey versions look like `2024.5.0`, forks add suffixes
 * (`2024.5.0-sharkey.1`, `12.119.2-beta.3`), and some servers report a bare `13`.
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String?): SemVer? {
            if (raw.isNullOrBlank()) return null
            val core = raw.trim().removePrefix("v").takeWhile { it.isDigit() || it == '.' }
            if (core.isEmpty()) return null
            val parts = core.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.isEmpty()) return null
            return SemVer(
                major = parts[0],
                minor = parts.getOrElse(1) { 0 },
                patch = parts.getOrElse(2) { 0 },
            )
        }
    }
}
