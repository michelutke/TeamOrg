package ch.teamorg

import java.net.HttpURLConnection
import java.net.URL

/**
 * Creates accounts directly against the backend so a login test does not depend on
 * the register test having run first. Uses HttpURLConnection to keep the
 * instrumented test source set free of extra dependencies.
 *
 * 10.0.2.2 is the emulator's alias for the host machine's loopback.
 */
object BackendSeed {

    private const val BASE_URL = "http://10.0.2.2:8080"

    fun register(email: String, password: String, displayName: String) {
        val body = """{"email":"$email","password":"$password","displayName":"$displayName"}"""
        val connection = (URL("$BASE_URL/auth/register").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            check(code == 200 || code == 201) {
                val error = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                "Seeding register failed with HTTP $code: $error"
            }
        } finally {
            connection.disconnect()
        }
    }
}
