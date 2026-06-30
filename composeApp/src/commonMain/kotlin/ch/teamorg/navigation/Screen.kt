package ch.teamorg.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen(val route: String) {
    @Serializable
    data object Loading : Screen("loading")

    @Serializable
    data object Events : Screen("events")

    @Serializable
    data object Calendar : Screen("calendar")

    @Serializable
    data object Teams : Screen("teams")

    @Serializable
    data object Inbox : Screen("inbox")

    @Serializable
    data object Profile : Screen("profile")

    // Auth screens
    @Serializable
    data object Login : Screen("login")

    @Serializable
    data object Register : Screen("register")

    @Serializable
    data object EmptyState : Screen("empty_state")

    @Serializable
    data object ClubSetup : Screen("club_setup")

    @Serializable
    data class TeamRoster(val teamId: String) : Screen("team_roster/{teamId}")

    @Serializable
    data class Invite(val token: String) : Screen("invite/{token}")

    @Serializable
    data class EventDetail(val eventId: String) : Screen("event_detail/{eventId}")

    @Serializable
    data object CreateEvent : Screen("create_event")

    @Serializable
    data class EditEvent(val eventId: String) : Screen("edit_event/{eventId}")

    @Serializable
    data class DuplicateEvent(val eventId: String) : Screen("duplicate_event/{eventId}")

    @Serializable
    data class PlayerProfile(val teamId: String, val userId: String) : Screen("player_profile/{teamId}/{userId}")

    @Serializable
    data object NotificationSettings : Screen("notification_settings")

    @Serializable
    data class ClubMembers(val clubId: String) : Screen("club_members/{clubId}")
}
