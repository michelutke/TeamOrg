package ch.teamorg.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.Notification
import ch.teamorg.navigation.Screen
import ch.teamorg.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val hasUnread: Boolean = false,
    val unreadCount: Long = 0
)

class InboxViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InboxState())
    val state = _state.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.notifications.isEmpty(), error = null) }
            notificationRepository.getNotifications(50, 0)
                .onSuccess { notifications ->
                    _state.update { it.copy(notifications = notifications, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, isLoading = false) }
                }
            notificationRepository.getUnreadCount()
                .onSuccess { count ->
                    _state.update { it.copy(unreadCount = count, hasUnread = count > 0) }
                }
        }
    }

    fun markRead(notificationId: String) {
        // Optimistic update
        val previous = _state.value.notifications
        _state.update { s ->
            val updated = s.notifications.map {
                if (it.id == notificationId) it.copy(isRead = true) else it
            }
            val unread = updated.count { !it.isRead }.toLong()
            s.copy(notifications = updated, unreadCount = unread, hasUnread = unread > 0)
        }
        viewModelScope.launch {
            notificationRepository.markRead(notificationId)
                .onFailure {
                    // Revert on failure
                    val unread = previous.count { !it.isRead }.toLong()
                    _state.update { s ->
                        s.copy(notifications = previous, unreadCount = unread, hasUnread = unread > 0)
                    }
                }
        }
    }

    fun markAllRead() {
        val previous = _state.value.notifications
        val previousUnread = _state.value.unreadCount
        // Optimistic update
        _state.update { s ->
            s.copy(
                notifications = s.notifications.map { it.copy(isRead = true) },
                unreadCount = 0,
                hasUnread = false
            )
        }
        viewModelScope.launch {
            notificationRepository.markAllRead()
                .onFailure {
                    // Revert on failure
                    _state.update { s ->
                        s.copy(
                            notifications = previous,
                            unreadCount = previousUnread,
                            hasUnread = previousUnread > 0,
                            error = "Could not mark read. Try again."
                        )
                    }
                }
        }
    }

    fun deleteAll() {
        val previous = _state.value.notifications
        val previousUnread = _state.value.unreadCount
        _state.update { it.copy(notifications = emptyList(), unreadCount = 0, hasUnread = false) }
        viewModelScope.launch {
            notificationRepository.deleteAll()
                .onFailure {
                    _state.update { s ->
                        s.copy(
                            notifications = previous,
                            unreadCount = previousUnread,
                            hasUnread = previousUnread > 0,
                            error = "Could not delete. Try again."
                        )
                    }
                }
        }
    }

    fun refresh() {
        loadNotifications()
    }

    fun getDeepLinkScreen(notification: Notification): Screen? {
        return when (notification.entityType) {
            "event" -> notification.entityId?.let { Screen.EventDetail(it) }
            else -> null
        }
    }
}
