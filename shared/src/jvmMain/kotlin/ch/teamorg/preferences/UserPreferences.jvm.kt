package ch.teamorg.preferences

import com.russhwolf.settings.Settings

actual class UserPreferences actual constructor(settings: Settings) {
    private val settings: Settings = settings

    actual fun saveToken(token: String) {
        settings.putString("auth_token", token)
    }

    actual fun getToken(): String? = settings.getStringOrNull("auth_token")

    actual fun clearToken() {
        settings.remove("auth_token")
        settings.remove("user_id")
    }

    actual fun saveUserId(id: String) {
        settings.putString("user_id", id)
    }

    actual fun getUserId(): String? = settings.getStringOrNull("user_id")
}
