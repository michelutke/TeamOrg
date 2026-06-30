package ch.teamorg.data.repository

import ch.teamorg.domain.SubGroup
import ch.teamorg.domain.TeamMember
import ch.teamorg.domain.UserRoles
import ch.teamorg.repository.TeamRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class CreateInviteRequest(val role: String, val email: String?)

@Serializable
private data class InviteResponse(val token: String, val inviteUrl: String, val expiresAt: String)

@Serializable
private data class UpdateRoleRequest(val role: String)

@Serializable
private data class UpdateProfileRequest(val jerseyNumber: Int? = null, val position: String? = null)

@Serializable
private data class CreateSubGroupRequest(val name: String)

@Serializable
private data class AddSubGroupMemberRequest(val userId: String)

@Serializable
private data class AddMemberRequest(val userId: String, val role: String)

@Serializable
private data class UpdateRoleBodyRequest(val role: String)

@Serializable
private data class LinkNdsMemberRequest(val userId: String)

class TeamRepositoryImpl(private val client: HttpClient) : TeamRepository {
    override suspend fun getTeamRoster(teamId: String): Result<List<TeamMember>> {
        return try {
            val response = client.get("/teams/$teamId/members")
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch roster: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeMember(teamId: String, userId: String): Result<Unit> {
        return try {
            val response = client.delete("/teams/$teamId/members/$userId")
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove member: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createInvite(teamId: String, role: String, email: String?): Result<String> {
        return try {
            val response = client.post("/teams/$teamId/invites") {
                contentType(ContentType.Application.Json)
                setBody(CreateInviteRequest(role, email))
            }
            if (response.status == HttpStatusCode.Created) {
                val inviteResponse = response.body<InviteResponse>()
                Result.success(inviteResponse.inviteUrl)
            } else {
                Result.failure(Exception("Failed to create invite: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private var cachedRoles: UserRoles? = null

    override suspend fun getMyRoles(): Result<UserRoles> {
        return try {
            val response = client.get("/auth/me/roles")
            if (response.status == HttpStatusCode.OK) {
                val roles: UserRoles = response.body()
                cachedRoles = roles
                Result.success(roles)
            } else {
                Result.failure(Exception("Failed to fetch roles: ${response.status}"))
            }
        } catch (e: Exception) {
            // Offline fallback: return cached roles if available
            val cached = cachedRoles
            if (cached != null) Result.success(cached)
            else Result.failure(Exception("You're offline. Team data not available."))
        }
    }

    override suspend fun updateMemberRole(teamId: String, userId: String, role: String): Result<TeamMember> {
        return try {
            val response = client.patch("/teams/$teamId/members/$userId/role") {
                contentType(ContentType.Application.Json)
                setBody(UpdateRoleRequest(role))
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to update role: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMemberProfile(teamId: String, userId: String, jerseyNumber: Int?, position: String?): Result<TeamMember> {
        return try {
            val response = client.patch("/teams/$teamId/members/$userId/profile") {
                contentType(ContentType.Application.Json)
                setBody(UpdateProfileRequest(jerseyNumber, position))
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to update profile: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveTeam(teamId: String): Result<Unit> {
        return try {
            val response = client.delete("/teams/$teamId/leave")
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to leave team: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSubGroups(teamId: String): Result<List<SubGroup>> {
        return try {
            val response = client.get("/teams/$teamId/subgroups")
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch sub-groups: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createSubGroup(teamId: String, name: String): Result<SubGroup> {
        return try {
            val response = client.post("/teams/$teamId/subgroups") {
                contentType(ContentType.Application.Json)
                setBody(CreateSubGroupRequest(name))
            }
            if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to create sub-group: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSubGroup(teamId: String, subGroupId: String): Result<Unit> {
        return try {
            val response = client.delete("/teams/$teamId/subgroups/$subGroupId")
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete sub-group: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addSubGroupMember(teamId: String, subGroupId: String, userId: String): Result<Unit> {
        return try {
            val response = client.post("/teams/$teamId/subgroups/$subGroupId/members") {
                contentType(ContentType.Application.Json)
                setBody(AddSubGroupMemberRequest(userId))
            }
            if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add sub-group member: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeSubGroupMember(teamId: String, subGroupId: String, userId: String): Result<Unit> {
        return try {
            val response = client.delete("/teams/$teamId/subgroups/$subGroupId/members/$userId")
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove sub-group member: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadAvatar(imageBytes: ByteArray, extension: String): Result<Unit> {
        return try {
            val response = client.submitFormWithBinaryData(
                url = "/auth/me/avatar",
                formData = formData {
                    append("avatar", imageBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/$extension")
                        append(HttpHeaders.ContentDisposition, "filename=\"avatar.$extension\"")
                    })
                }
            )
            if (response.status == HttpStatusCode.OK) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to upload avatar: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addMember(teamId: String, userId: String, role: String): Result<Unit> = try {
        val r = client.post("/teams/$teamId/members") {
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId, role))
        }
        if (r.status == HttpStatusCode.OK || r.status == HttpStatusCode.Created) Result.success(Unit)
        else Result.failure(Exception("addMember: ${r.status}"))
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun updateRole(teamId: String, userId: String, role: String): Result<Unit> = try {
        val r = client.patch("/teams/$teamId/members/$userId/role") {
            contentType(ContentType.Application.Json)
            setBody(UpdateRoleBodyRequest(role))
        }
        if (r.status == HttpStatusCode.OK) Result.success(Unit)
        else Result.failure(Exception("updateRole: ${r.status}"))
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun linkNdsMember(teamId: String, memberId: String, userId: String): Result<Unit> = try {
        val r = client.post("/teams/$teamId/nds/members/$memberId/link") {
            contentType(ContentType.Application.Json)
            setBody(LinkNdsMemberRequest(userId))
        }
        if (r.status == HttpStatusCode.OK || r.status == HttpStatusCode.Created) Result.success(Unit)
        else Result.failure(Exception("linkNdsMember: ${r.status}"))
    } catch (e: Exception) { Result.failure(e) }
}
