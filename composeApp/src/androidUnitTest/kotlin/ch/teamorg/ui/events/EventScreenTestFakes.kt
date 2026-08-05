package ch.teamorg.ui.events

import ch.teamorg.domain.Club
import ch.teamorg.domain.ClubUser
import ch.teamorg.domain.CreateEventRequest
import ch.teamorg.domain.DuplicateSuggestion
import ch.teamorg.domain.EditEventRequest
import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.LinkMemberResult
import ch.teamorg.domain.SubGroup
import ch.teamorg.domain.Team
import ch.teamorg.domain.TeamMember
import ch.teamorg.domain.TeamRoleEntry
import ch.teamorg.domain.UserRoles
import ch.teamorg.repository.ClubRepository
import ch.teamorg.repository.EventRepository
import ch.teamorg.repository.TeamRepository

/** EventRepository fake returning a fixed event list; subgroups keyed by teamId. */
class FakeEventListRepository(
    private val events: List<EventWithTeams>,
    private val subGroupsByTeam: Map<String, List<SubGroup>> = emptyMap(),
) : EventRepository {
    override suspend fun getMyEvents(from: String?, to: String?, type: String?, teamId: String?) =
        Result.success(events)
    override suspend fun getEventDetail(id: String) =
        Result.failure<EventWithTeams>(NotImplementedError())
    override suspend fun createEvent(request: CreateEventRequest) =
        Result.failure<Event>(NotImplementedError())
    override suspend fun editEvent(id: String, request: EditEventRequest) =
        Result.failure<Event>(NotImplementedError())
    override suspend fun cancelEvent(id: String, scope: String) = Result.success(Unit)
    override suspend fun uncancelEvent(id: String, scope: String) = Result.success(Unit)
    override suspend fun duplicateEvent(id: String) = Result.failure<Event>(NotImplementedError())
    override suspend fun getSubGroups(teamId: String) =
        Result.success(subGroupsByTeam[teamId] ?: emptyList())
}

/** TeamRepository fake. Optionally exposes team roles so availableTeams can be derived. */
class FakeEventListTeamRepo(
    private val roles: UserRoles = UserRoles(),
) : TeamRepository {
    override suspend fun getMyRoles(): Result<UserRoles> = Result.success(roles)
    override suspend fun getTeamRoster(teamId: String) = Result.success(emptyList<TeamMember>())
    override suspend fun removeMember(teamId: String, userId: String) = Result.success(Unit)
    override suspend fun createInvite(teamId: String, role: String, email: String?) = Result.success("")
    override suspend fun updateMemberRole(teamId: String, userId: String, role: String) =
        Result.failure<TeamMember>(NotImplementedError())
    override suspend fun updateMemberProfile(teamId: String, userId: String, jerseyNumber: Int?, position: String?) =
        Result.failure<TeamMember>(NotImplementedError())
    override suspend fun leaveTeam(teamId: String) = Result.success(Unit)
    override suspend fun getSubGroups(teamId: String) = Result.success(emptyList<SubGroup>())
    override suspend fun createSubGroup(teamId: String, name: String) =
        Result.failure<SubGroup>(NotImplementedError())
    override suspend fun deleteSubGroup(teamId: String, subGroupId: String) = Result.success(Unit)
    override suspend fun addSubGroupMember(teamId: String, subGroupId: String, userId: String) = Result.success(Unit)
    override suspend fun removeSubGroupMember(teamId: String, subGroupId: String, userId: String) = Result.success(Unit)
    override suspend fun uploadAvatar(imageBytes: ByteArray, extension: String) = Result.success(Unit)
    override suspend fun addMember(teamId: String, userId: String, role: String) = Result.success(Unit)
    override suspend fun linkNdsMember(teamId: String, memberId: String, userId: String) = LinkMemberResult.Success
    override suspend fun getDuplicateSuggestions(teamId: String) = Result.success(emptyList<DuplicateSuggestion>())
}

/** ClubRepository fake returning fixed teams for a club. */
class FakeEventListClubRepo(
    private val teamsByClub: Map<String, List<Team>> = emptyMap(),
) : ClubRepository {
    override suspend fun createClub(name: String, sportType: String, location: String?) =
        Result.failure<Club>(NotImplementedError())
    override suspend fun uploadLogo(clubId: String, imageBytes: ByteArray, extension: String) =
        Result.failure<Club>(NotImplementedError())
    override suspend fun getClub(clubId: String) = Result.failure<Club>(NotImplementedError())
    override suspend fun getClubTeams(clubId: String) =
        Result.success(teamsByClub[clubId] ?: emptyList())
    override suspend fun createTeam(clubId: String, name: String, description: String?) =
        Result.failure<Team>(NotImplementedError())
    override suspend fun updateTeam(teamId: String, name: String?, description: String?) =
        Result.failure<Team>(NotImplementedError())
    override suspend fun updateClub(clubId: String, name: String?, location: String?) =
        Result.failure<Club>(NotImplementedError())
    override suspend fun listClubUsers(clubId: String, limit: Int, offset: Int) =
        Result.success(emptyList<ClubUser>())
}

fun teamRole(teamId: String, clubId: String, role: String = "coach") =
    TeamRoleEntry(teamId = teamId, clubId = clubId, role = role)
