package ch.teamorg.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Club(
    val id: String,
    val name: String,
    val sportType: String,
    val location: String?,
    val logoUrl: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TeamAppearance(
    val shape: String,
    val color: String
)

@Serializable
data class Team(
    val id: String,
    val clubId: String,
    val name: String,
    val memberCount: Int,
    val description: String?,
    val appearance: TeamAppearance?,
    val archivedAt: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TeamMember(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    val jerseyNumber: Int?,
    val position: String?
)
