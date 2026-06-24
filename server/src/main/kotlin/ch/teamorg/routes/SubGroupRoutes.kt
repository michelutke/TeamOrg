package ch.teamorg.routes

import ch.teamorg.db.tables.SubGroupMembersTable
import ch.teamorg.db.tables.SubGroupsTable
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.middleware.requireTeamRole
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.koin.ktor.ext.inject
import java.util.UUID

@Serializable
data class CreateSubGroupRequest(val name: String)

@Serializable
data class UpdateSubGroupRequest(val name: String)

@Serializable
data class AddSubGroupMemberRequest(val userId: String)

@Serializable
data class SubGroupResponse(val id: String, val teamId: String, val name: String, val memberCount: Long)

/** Returns the team a subgroup belongs to, or null if the subgroup does not exist. */
private suspend fun subGroupTeamId(subGroupId: UUID): UUID? = newSuspendedTransaction {
    SubGroupsTable.select(SubGroupsTable.teamId)
        .where { SubGroupsTable.id eq subGroupId }
        .map { it[SubGroupsTable.teamId] }
        .singleOrNull()
}

/**
 * Confirm the subgroup belongs to the team in the path. Guards against a coach of team A
 * mutating a subgroup owned by team B via a forged path. Responds 403 and returns false on
 * mismatch (or when the subgroup is missing — do not leak existence).
 */
private suspend fun io.ktor.server.application.ApplicationCall.requireSubGroupInTeam(
    subGroupId: UUID,
    teamId: UUID
): Boolean {
    if (subGroupTeamId(subGroupId) != teamId) {
        respond(HttpStatusCode.Forbidden, "Subgroup does not belong to this team")
        return false
    }
    return true
}

fun Route.subGroupRoutes() {
    val teamRepository by inject<TeamRepository>()

    authenticate("jwt") {
        route("/teams/{teamId}/subgroups") {
            get {
                val teamId = UUID.fromString(call.parameters["teamId"])
                if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@get
                val subGroups = newSuspendedTransaction {
                    SubGroupsTable
                        .leftJoin(SubGroupMembersTable)
                        .selectAll()
                        .where { SubGroupsTable.teamId eq teamId }
                        .groupBy { it[SubGroupsTable.id] }
                        .map { (groupId, rows) ->
                            val first = rows.first()
                            SubGroupResponse(
                                id = groupId.toString(),
                                teamId = first[SubGroupsTable.teamId].toString(),
                                name = first[SubGroupsTable.name],
                                memberCount = rows.count { it.getOrNull(SubGroupMembersTable.userId) != null }.toLong()
                            )
                        }
                }
                call.respond(subGroups)
            }

            post {
                val teamId = UUID.fromString(call.parameters["teamId"])
                if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@post
                val request = call.receive<CreateSubGroupRequest>()
                val subGroup = newSuspendedTransaction {
                    val id = SubGroupsTable.insert {
                        it[SubGroupsTable.teamId] = teamId
                        it[name] = request.name
                    } get SubGroupsTable.id
                    SubGroupResponse(id = id.toString(), teamId = teamId.toString(), name = request.name, memberCount = 0L)
                }
                call.respond(HttpStatusCode.Created, subGroup)
            }

            route("/{subGroupId}") {
                put {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    val subGroupId = UUID.fromString(call.parameters["subGroupId"])
                    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@put
                    if (!call.requireSubGroupInTeam(subGroupId, teamId)) return@put
                    val request = call.receive<UpdateSubGroupRequest>()
                    newSuspendedTransaction {
                        SubGroupsTable.update({ SubGroupsTable.id eq subGroupId }) {
                            it[name] = request.name
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("id" to subGroupId.toString(), "name" to request.name))
                }

                delete {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    val subGroupId = UUID.fromString(call.parameters["subGroupId"])
                    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@delete
                    if (!call.requireSubGroupInTeam(subGroupId, teamId)) return@delete
                    newSuspendedTransaction {
                        SubGroupMembersTable.deleteWhere { SubGroupMembersTable.subGroupId eq subGroupId }
                        SubGroupsTable.deleteWhere { SubGroupsTable.id eq subGroupId }
                    }
                    call.respond(HttpStatusCode.NoContent)
                }

                post("/members") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    val subGroupId = UUID.fromString(call.parameters["subGroupId"])
                    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@post
                    if (!call.requireSubGroupInTeam(subGroupId, teamId)) return@post
                    val request = call.receive<AddSubGroupMemberRequest>()
                    newSuspendedTransaction {
                        SubGroupMembersTable.insert {
                            it[SubGroupMembersTable.subGroupId] = subGroupId
                            it[userId] = UUID.fromString(request.userId)
                        }
                    }
                    call.respond(HttpStatusCode.Created)
                }

                delete("/members/{userId}") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    val subGroupId = UUID.fromString(call.parameters["subGroupId"])
                    val userId = UUID.fromString(call.parameters["userId"])
                    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@delete
                    if (!call.requireSubGroupInTeam(subGroupId, teamId)) return@delete
                    newSuspendedTransaction {
                        SubGroupMembersTable.deleteWhere {
                            (SubGroupMembersTable.subGroupId eq subGroupId) and (SubGroupMembersTable.userId eq userId)
                        }
                    }
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
