package ch.teamorg.network

import ch.teamorg.data.network.requireSecureBaseUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpClientBaseUrlTest {

    @Test
    fun `https url is accepted and returned unchanged`() {
        assertEquals("https://api.teamorg.app", requireSecureBaseUrl("https://api.teamorg.app"))
    }

    @Test
    fun `emulator host over http is accepted`() {
        assertEquals("http://10.0.2.2:8080", requireSecureBaseUrl("http://10.0.2.2:8080"))
    }

    @Test
    fun `localhost over http is accepted`() {
        assertEquals("http://localhost:8080", requireSecureBaseUrl("http://localhost:8080"))
    }

    @Test
    fun `loopback ip over http is accepted`() {
        assertEquals("http://127.0.0.1:8080", requireSecureBaseUrl("http://127.0.0.1:8080"))
    }

    @Test
    fun `remote host over http is rejected`() {
        assertFailsWith<IllegalArgumentException> { requireSecureBaseUrl("http://api.teamorg.app") }
    }

    @Test
    fun `attacker host that merely contains localhost is rejected`() {
        assertFailsWith<IllegalArgumentException> { requireSecureBaseUrl("http://localhost.evil.example") }
    }
}
