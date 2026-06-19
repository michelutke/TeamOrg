package ch.teamorg.data.network

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSBundle

actual object ApiConfig {
    actual val baseUrl: String = NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String ?: "http://localhost:8080"

    @OptIn(ExperimentalNativeApi::class)
    actual val enableNetworkLogging: Boolean = Platform.isDebugBinary
}
