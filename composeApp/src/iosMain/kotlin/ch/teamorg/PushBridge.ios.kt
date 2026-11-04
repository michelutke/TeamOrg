package ch.teamorg

import ch.teamorg.data.PushRegistration

fun setPushBridge(login: (String) -> Unit, logout: () -> Unit) {
    PushRegistration.register(login, logout)
}
