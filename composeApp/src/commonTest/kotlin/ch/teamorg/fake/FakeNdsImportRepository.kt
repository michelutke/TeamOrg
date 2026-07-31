package ch.teamorg.fake

import ch.teamorg.domain.NdsImportRequest
import ch.teamorg.domain.NdsImportResponse
import ch.teamorg.domain.NdsParseResponse
import ch.teamorg.repository.NdsFilePart
import ch.teamorg.repository.NdsImportRepository

class FakeNdsImportRepository : NdsImportRepository {

    var parseResult: Result<NdsParseResponse> = Result.success(NdsParseResponse())
    var importResult: Result<NdsImportResponse> = Result.success(
        NdsImportResponse(teamId = "team1", membersImported = 0, eventsCreated = 0, attendanceImported = 0)
    )

    var lastParseClubId: String? = null
    var lastParseTeamId: String? = null
    var lastParseFiles: List<NdsFilePart>? = null
    var lastImportClubId: String? = null
    var lastImportRequest: NdsImportRequest? = null

    fun reset() {
        parseResult = Result.success(NdsParseResponse())
        importResult = Result.success(
            NdsImportResponse(teamId = "team1", membersImported = 0, eventsCreated = 0, attendanceImported = 0)
        )
        lastParseClubId = null
        lastParseTeamId = null
        lastParseFiles = null
        lastImportClubId = null
        lastImportRequest = null
    }

    override suspend fun parse(clubId: String, teamId: String?, files: List<NdsFilePart>): Result<NdsParseResponse> {
        lastParseClubId = clubId
        lastParseTeamId = teamId
        lastParseFiles = files
        return parseResult
    }

    override suspend fun import(clubId: String, request: NdsImportRequest): Result<NdsImportResponse> {
        lastImportClubId = clubId
        lastImportRequest = request
        return importResult
    }
}
