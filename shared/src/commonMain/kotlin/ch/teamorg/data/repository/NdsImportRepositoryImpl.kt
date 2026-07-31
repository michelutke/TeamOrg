package ch.teamorg.data.repository

import ch.teamorg.domain.NdsImportRequest
import ch.teamorg.domain.NdsImportResponse
import ch.teamorg.domain.NdsParseResponse
import ch.teamorg.repository.NdsFilePart
import ch.teamorg.repository.NdsImportRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class NdsImportRepositoryImpl(private val client: HttpClient) : NdsImportRepository {
    override suspend fun parse(clubId: String, teamId: String?, files: List<NdsFilePart>): Result<NdsParseResponse> {
        return try {
            val response = client.submitFormWithBinaryData(
                url = "/clubs/$clubId/nds/parse",
                formData = formData {
                    files.forEach { part ->
                        append(part.slot, part.bytes, Headers.build {
                            append(HttpHeaders.ContentType, mimeFor(part.fileName))
                            append(HttpHeaders.ContentDisposition, "filename=\"${part.fileName}\"")
                        })
                    }
                    if (teamId != null) {
                        append("teamId", teamId)
                    }
                }
            )
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to parse NDS files: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun import(clubId: String, request: NdsImportRequest): Result<NdsImportResponse> {
        return try {
            val response = client.post("/clubs/$clubId/nds/import") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to import NDS: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mimeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "csv" -> "text/csv"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "application/octet-stream"
        }
    }
}
