package ch.teamorg.crash

class RecordingCrashReporting : CrashReportingBridge {
    val calls = mutableListOf<String>()

    override fun setUser(userId: String) {
        calls += "setUser:$userId"
    }

    override fun clearUser() {
        calls += "clearUser"
    }
}
