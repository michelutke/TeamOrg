package ch.teamorg.data

interface PushBridge {
    fun login(userId: String)
    fun logout()
}

expect object PushRegistration : PushBridge
