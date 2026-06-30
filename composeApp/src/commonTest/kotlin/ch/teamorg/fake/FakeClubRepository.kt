package ch.teamorg.fake

import ch.teamorg.domain.Club
import ch.teamorg.domain.ClubUser
import ch.teamorg.domain.Team
import ch.teamorg.repository.ClubRepository

class FakeClubRepository : ClubRepository {

    var createClubResult: Result<Club> = Result.success(
        Club(id = "club1", name = "Test Club", logoUrl = null, sportType = "volleyball")
    )
    var uploadLogoResult: Result<Club> = Result.success(
        Club(id = "club1", name = "Test Club", logoUrl = "https://example.com/logo.png", sportType = "volleyball")
    )
    var getClubTeamsResult: Result<List<Team>> = Result.success(emptyList())

    var lastCreatedName: String? = null
    var lastCreatedSportType: String? = null
    var lastCreatedLocation: String? = null
    var lastUploadedClubId: String? = null
    var lastUploadedExtension: String? = null

    fun reset() {
        createClubResult = Result.success(
            Club(id = "club1", name = "Test Club", logoUrl = null, sportType = "volleyball")
        )
        uploadLogoResult = Result.success(
            Club(id = "club1", name = "Test Club", logoUrl = "https://example.com/logo.png", sportType = "volleyball")
        )
        getClubTeamsResult = Result.success(emptyList())
        lastCreatedName = null
        lastCreatedSportType = null
        lastCreatedLocation = null
        lastUploadedClubId = null
        lastUploadedExtension = null
    }

    override suspend fun createClub(name: String, sportType: String, location: String?): Result<Club> {
        lastCreatedName = name
        lastCreatedSportType = sportType
        lastCreatedLocation = location
        return createClubResult
    }

    override suspend fun uploadLogo(clubId: String, imageBytes: ByteArray, extension: String): Result<Club> {
        lastUploadedClubId = clubId
        lastUploadedExtension = extension
        return uploadLogoResult
    }

    var getClubResult: Result<Club> = Result.success(
        Club(id = "club1", name = "Test Club", logoUrl = null, sportType = "volleyball")
    )
    var createTeamResult: Result<Team> = Result.success(
        Team(id = "team1", clubId = "club1", name = "Test Team")
    )
    var updateTeamResult: Result<Team> = Result.success(
        Team(id = "team1", clubId = "club1", name = "Updated Team")
    )
    var updateClubResult: Result<Club> = Result.success(
        Club(id = "club1", name = "Updated Club", logoUrl = null, sportType = "volleyball")
    )

    override suspend fun getClub(clubId: String): Result<Club> = getClubResult

    override suspend fun getClubTeams(clubId: String): Result<List<Team>> = getClubTeamsResult

    override suspend fun createTeam(clubId: String, name: String, description: String?): Result<Team> = createTeamResult

    override suspend fun updateTeam(teamId: String, name: String?, description: String?): Result<Team> = updateTeamResult

    override suspend fun updateClub(clubId: String, name: String?, location: String?): Result<Club> = updateClubResult

    var listClubUsersResult: Result<List<ClubUser>> = Result.success(emptyList())

    override suspend fun listClubUsers(clubId: String, limit: Int, offset: Int): Result<List<ClubUser>> = listClubUsersResult
}
