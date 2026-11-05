package ch.teamorg.data

// The iOS OneSignal SDK is a native Swift framework and cannot be called from Kotlin/Native.
// iOSApp.swift registers the native login/logout handlers at startup.
actual object PushRegistration : PushBridge {
    private var loginHandler: ((String) -> Unit)? = null
    private var logoutHandler: (() -> Unit)? = null

    fun register(login: (String) -> Unit, logout: () -> Unit) {
        loginHandler = login
        logoutHandler = logout
    }

    override fun login(userId: String) {
        loginHandler?.invoke(userId)
    }

    override fun logout() {
        logoutHandler?.invoke()
    }
}
