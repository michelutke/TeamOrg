package ch.teamorg.fake

import ch.teamorg.domain.Notification
import ch.teamorg.domain.NotificationSettings
import ch.teamorg.domain.ReminderOverride
import ch.teamorg.domain.UpdateNotificationSettingsRequest
import ch.teamorg.repository.NotificationRepository

class FakeNotificationRepository : NotificationRepository {

    var getNotificationsResult: Result<List<Notification>> = Result.success(emptyList())
    var getUnreadCountResult: Result<Long> = Result.success(0L)
    var markReadResult: Result<Boolean> = Result.success(true)
    var markAllReadResult: Result<Int> = Result.success(0)
    var deleteAllResult: Result<Unit> = Result.success(Unit)
    var getSettingsResult: Result<NotificationSettings> = Result.failure(NotImplementedError())
    var settingsByTeam: MutableMap<String, Result<NotificationSettings>> = mutableMapOf()
    var updateSettingsResult: Result<Unit> = Result.success(Unit)
    var getReminderOverrideResult: Result<ReminderOverride> = Result.success(ReminderOverride(reminderLeadMinutes = null))
    var setReminderOverrideResult: Result<Unit> = Result.success(Unit)

    var getNotificationsCallCount = 0
    val markReadCalls = mutableListOf<String>()
    var markAllReadCallCount = 0
    var deleteAllCallCount = 0
    val getSettingsCalls = mutableListOf<String>()
    val updateSettingsCalls = mutableListOf<Pair<String, UpdateNotificationSettingsRequest>>()
    val setReminderOverrideCalls = mutableListOf<Pair<String, Int?>>()

    override suspend fun getNotifications(limit: Int, offset: Int): Result<List<Notification>> {
        getNotificationsCallCount++
        return getNotificationsResult
    }

    override suspend fun getUnreadCount(): Result<Long> = getUnreadCountResult

    override suspend fun markRead(notificationId: String): Result<Boolean> {
        markReadCalls += notificationId
        return markReadResult
    }

    override suspend fun markAllRead(): Result<Int> {
        markAllReadCallCount++
        return markAllReadResult
    }

    override suspend fun deleteAll(): Result<Unit> {
        deleteAllCallCount++
        return deleteAllResult
    }

    override suspend fun getSettings(teamId: String): Result<NotificationSettings> {
        getSettingsCalls += teamId
        return settingsByTeam[teamId] ?: getSettingsResult
    }

    override suspend fun updateSettings(teamId: String, request: UpdateNotificationSettingsRequest): Result<Unit> {
        updateSettingsCalls += teamId to request
        return updateSettingsResult
    }

    override suspend fun getReminderOverride(eventId: String): Result<ReminderOverride> = getReminderOverrideResult

    override suspend fun setReminderOverride(eventId: String, leadMinutes: Int?): Result<Unit> {
        setReminderOverrideCalls += eventId to leadMinutes
        return setReminderOverrideResult
    }
}
