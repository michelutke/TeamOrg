package ch.teamorg.data.network

actual object ApiConfig {
    actual val baseUrl: String = System.getenv("API_BASE_URL") ?: "http://localhost:8080"
    actual val enableNetworkLogging: Boolean = false
}
