package ch.teamorg.data

// JVM target (desktop/tests) — push not applicable.
actual object PushRegistration : PushBridge {
    override fun login(userId: String) {}
    override fun logout() {}
}
