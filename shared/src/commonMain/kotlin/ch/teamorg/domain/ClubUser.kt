package ch.teamorg.domain

import kotlinx.serialization.Serializable

@Serializable
data class TeamRoleRef(val teamId: String, val teamName: String, val role: String)

@Serializable
data class ClubUser(
    val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    val teamRoles: List<TeamRoleRef> = emptyList()
)
