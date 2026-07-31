package ch.teamorg.repository

import ch.teamorg.domain.NdsImportRequest
import ch.teamorg.domain.NdsImportResponse
import ch.teamorg.domain.NdsParseResponse

data class NdsFilePart(val slot: String, val fileName: String, val bytes: ByteArray)

interface NdsImportRepository {
    suspend fun parse(clubId: String, teamId: String?, files: List<NdsFilePart>): Result<NdsParseResponse>
    suspend fun import(clubId: String, request: NdsImportRequest): Result<NdsImportResponse>
}
