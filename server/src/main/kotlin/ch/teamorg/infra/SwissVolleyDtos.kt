package ch.teamorg.infra

import kotlinx.serialization.Serializable

@Serializable
data class SVLeague(
    val leagueId: Int? = null,
    val caption: String? = null
)

@Serializable
data class SVClub(
    val clubId: Int? = null,
    val clubCaption: String? = null
)

@Serializable
data class SVTeam(
    val teamId: Int? = null,
    val seasonalTeamId: Int? = null,
    val caption: String? = null,
    val gender: String? = null,
    val league: SVLeague? = null,
    val club: SVClub? = null
)

@Serializable
data class SVGameTeam(
    val teamId: Int? = null,
    val seasonalTeamId: Int? = null,
    val caption: String? = null,
    val clubId: Int? = null,
    val clubCaption: String? = null
)

@Serializable
data class SVGameTeams(
    val home: SVGameTeam? = null,
    val away: SVGameTeam? = null
)

@Serializable
data class SVHall(
    val hallId: Int? = null,
    val caption: String? = null,
    val city: String? = null
)

@Serializable
data class SVSetResult(
    val home: Int? = null,
    val away: Int? = null
)

@Serializable
data class SVResultSummary(
    val winner: String? = null,
    val wonSetsHomeTeam: Int? = null,
    val wonSetsAwayTeam: Int? = null
)

@Serializable
data class SVGame(
    val gameId: Int? = null,
    val playDate: String? = null,
    val playDateUtc: String? = null,
    val status: Int? = null,
    val teams: SVGameTeams? = null,
    val hall: SVHall? = null,
    val setResults: List<SVSetResult>? = null,
    val resultSummary: SVResultSummary? = null
)
