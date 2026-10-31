package ch.teamorg

import androidx.compose.runtime.mutableStateOf
import ch.teamorg.navigation.Screen

object DeepLinkHandler {
    val pendingToken = mutableStateOf<String?>(null)
    val pendingInviteEmail = mutableStateOf<String?>(null)
    val pendingScreen = mutableStateOf<Screen?>(null)

    fun openNotification(entityType: String?, entityId: String?) {
        pendingScreen.value = if (entityType == "event" && !entityId.isNullOrBlank()) {
            Screen.EventDetail(entityId)
        } else {
            Screen.Inbox
        }
    }
}
