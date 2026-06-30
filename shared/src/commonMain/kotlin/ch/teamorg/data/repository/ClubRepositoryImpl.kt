package ch.teamorg.data.repository

import ch.teamorg.domain.Club
import ch.teamorg.domain.ClubUser
import ch.teamorg.domain.Team
import ch.teamorg.repository.ClubRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class CreateClubRequest(val name: String, val sportType: String, val location: String?)

@Serializable
private data class UpdateClubRequest(val name: String? = null, val location: String? = null)

@Serializable
private data class CreateTeamRequest(val name: String, val description: String? = null)

@Serializable
private data class UpdateTeamRequest(val name: String? = null, val description: String? = null)

class ClubRepositoryImpl(private val client: HttpClient) : ClubRepository {
    override suspend fun createClub(name: String, sportType: String, location: String?): Result<Club> {
        return try {
            val response = client.post("/clubs") {
                setBody(CreateClubRequest(name, sportType, location))
            }
            if (response.status == HttpStatusCode.Created) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to create club: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadLogo(clubId: String, imageBytes: ByteArray, extension: String): Result<Club> {
        return try {
            val response = client.submitFormWithBinaryData(
                url = "/clubs/$clubId/logo",
                formData = formData {
                    append("logo", imageBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/$extension")
                        append(HttpHeaders.ContentDisposition, "filename=\"logo.$extension\"")
                    })
                }
            )
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to upload logo: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private var cachedClub: Pair<String, Club>? = null

    override suspend fun getClub(clubId: String): Result<Club> {
        return try {
            val response = client.get("/clubs/$clubId")
            if (response.status == HttpStatusCode.OK) {
                val club: Club = response.body()
                cachedClub = clubId to club
                Result.success(club)
            } else {
                Result.failure(Exception("Failed to fetch club: ${response.status}"))
            }
        } catch (e: Exception) {
            val cached = cachedClub
            if (cached != null && cached.first == clubId) Result.success(cached.second)
            else Result.failure(Exception("You're offline. Club data not available."))
        }
    }

    private val cachedTeams = mutableMapOf<String, List<Team>>()

    override suspend fun getClubTeams(clubId: String): Result<List<Team>> {
        return try {
            val response = client.get("/clubs/$clubId/teams")
            if (response.status == HttpStatusCode.OK) {
                val teams: List<Team> = response.body()
                cachedTeams[clubId] = teams
                Result.success(teams)
            } else {
                Result.failure(Exception("Failed to fetch teams: ${response.status}"))
            }
        } catch (e: Exception) {
            val cached = cachedTeams[clubId]
            if (cached != null) Result.success(cached)
            else Result.failure(Exception("You're offline. Teams not available."))
        }
    }

    override suspend fun createTeam(clubId: String, name: String, description: String?): Result<Team> {
        return try {
            val response = client.post("/clubs/$clubId/teams") {
                setBody(CreateTeamRequest(name, description))
            }
            if (response.status == HttpStatusCode.Created) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to create team: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateClub(clubId: String, name: String?, location: String?): Result<Club> {
        return try {
            val response = client.patch("/clubs/$clubId") {
                setBody(UpdateClubRequest(name, location))
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to update club: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTeam(teamId: String, name: String?, description: String?): Result<Team> {
        return try {
            val response = client.patch("/teams/$teamId") {
                setBody(UpdateTeamRequest(name, description))
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to update team: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listClubUsers(clubId: String, limit: Int, offset: Int): Result<List<ClubUser>> {
        return try {
            val response = client.get("/clubs/$clubId/users?limit=$limit&offset=$offset")
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("listClubUsers: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
