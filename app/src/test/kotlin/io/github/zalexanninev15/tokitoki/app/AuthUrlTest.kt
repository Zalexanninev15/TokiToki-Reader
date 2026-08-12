package io.github.zalexanninev15.tokitoki.app

import io.github.zalexanninev15.tokitoki.data.repo.AuthService
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Instance URL normalisation is pure string handling, so it is tested on the JVM rather
 * than on a device. Users type instance addresses in every shape imaginable.
 */
class InstanceUrlNormalisationTest {

    @Test
    fun `bare host gets https`() {
        assertEquals("https://example.social", AuthService.normalise("example.social"))
    }

    @Test
    fun `trailing slash is dropped`() {
        assertEquals("https://example.social", AuthService.normalise("https://example.social/"))
    }

    @Test
    fun `existing scheme is preserved`() {
        assertEquals("http://localhost:3000", AuthService.normalise("http://localhost:3000"))
    }

    @Test
    fun `full handle yields the host`() {
        assertEquals("https://example.social", AuthService.normalise("@me@example.social"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("https://example.social", AuthService.normalise("  example.social  "))
    }
}
