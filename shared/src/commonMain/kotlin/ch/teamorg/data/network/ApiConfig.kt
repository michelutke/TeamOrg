package ch.teamorg.data.network

expect object ApiConfig {
    val baseUrl: String

    /** Network request/response logging — enabled only in debug builds (leaks tokens/bodies otherwise). */
    val enableNetworkLogging: Boolean
}
