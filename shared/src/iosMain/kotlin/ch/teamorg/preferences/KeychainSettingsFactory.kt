package ch.teamorg.preferences

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

private const val KEYCHAIN_SERVICE = "ch.teamorg.auth"
private val MIGRATED_KEYS = listOf("auth_token", "user_id")

/**
 * Session storage for iOS.
 *
 * The session JWT used to live in `NSUserDefaults`, which is a plain plist inside the app
 * container — readable from an unencrypted device backup or from a jailbroken device.
 * The Keychain gives it file-level encryption tied to device unlock.
 *
 * Values already on a user's device are migrated on first launch, so nobody is logged out
 * by the switch.
 */
@OptIn(ExperimentalSettingsImplementation::class)
fun keychainSettings(): Settings {
    val keychain = KeychainSettings(KEYCHAIN_SERVICE)
    migrateFromUserDefaults(keychain)
    return keychain
}

@OptIn(ExperimentalSettingsImplementation::class)
private fun migrateFromUserDefaults(keychain: KeychainSettings) {
    val legacy = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    MIGRATED_KEYS.forEach { key ->
        val existing = legacy.getStringOrNull(key) ?: return@forEach
        if (keychain.getStringOrNull(key) == null) {
            keychain.putString(key, existing)
        }
        // Remove the plaintext copy once it is safely in the Keychain.
        legacy.remove(key)
    }
}
