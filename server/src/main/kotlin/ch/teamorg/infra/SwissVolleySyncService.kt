package ch.teamorg.infra

import ch.teamorg.db.tables.SystemUsers
import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.IntegrationRepository
import ch.teamorg.domain.repositories.NotificationRepository
import ch.teamorg.domain.repositories.SyncEnabledLink
import ch.teamorg.domain.repositories.TeamRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("SwissVolleySyncService")

private const val EXTERNAL_SOURCE = "swissvolley"

/**
 * Pure, injectable SwissVolley → TeamOrg game-sync engine (design §6, §7, §13).
 *
 * All side effects go through the constructor-injected collaborators, so the per-game
 * upsert logic is unit-testable with fakes. The polling job (cadence, club iteration)
 * lives elsewhere and only calls [syncClub] / [syncTeam].
 */
class SwissVolleySyncService(
    private val integrationRepository: IntegrationRepository,
    private val eventRepository: EventRepository,
    private val notificationDispatcher: NotificationDispatcher,
    private val swissVolleyClient: SwissVolleyClient,
    // Resolve a team's club for syncTeam (integration state is per-club) and club managers
    // for the always-on sv_key_invalid alert (user-targeted path, design §13.4).
    private val teamRepository: TeamRepository,
    private val notificationRepository: NotificationRepository
) {

    /** One full sync pass for a single club (design §6). */
    suspend fun syncClub(clubId: UUID) {
        runSync(clubId, restrictToTeamId = null)
    }

    /**
     * Season-rollover refresh for a single club (design §6 "Season rollover", §14 deprecation).
     *
     * Re-reads `/indoor/teams` and, matching by the stable `sv_team_id`:
     *  - refreshes seasonal id / league / gender on existing active links,
     *  - deprecates active links whose `sv_team_id` vanished from the feed,
     *  - alerts club managers (always-on, user-targeted) about NEW SV teams not yet linked —
     *    it never auto-creates teams (the manager stays in control).
     *
     * Does NOT sync games; the polling job calls this before [syncClub].
     */
    suspend fun refreshClubTeams(clubId: UUID) {
        val integration = integrationRepository.getIntegration(clubId)
        if (integration == null || integration.keyValid != true) {
            logger.info("refreshClubTeams $clubId: no valid integration, skipping")
            return
        }
        val apiKey = integrationRepository.getApiKey(clubId) ?: run {
            logger.info("refreshClubTeams $clubId: no api key, skipping")
            return
        }

        val svTeams: List<SVTeam> = try {
            swissVolleyClient.listTeams(apiKey)
        } catch (e: InvalidApiKeyException) {
            handleInvalidKey(clubId)
            return
        }

        val svTeamsById: Map<Int, SVTeam> = svTeams
            .mapNotNull { team -> team.teamId?.let { it to team } }
            .toMap()

        // Match existing active links by stable sv_team_id: refresh present ones, deprecate vanished.
        val activeSvIds = integrationRepository.listActiveLinkSvTeamIds(clubId)
        for (svTeamId in activeSvIds) {
            val svTeam = svTeamsById[svTeamId]
            if (svTeam != null) {
                integrationRepository.updateLinkSeasonal(
                    clubId = clubId,
                    svTeamId = svTeamId,
                    svSeasonalTeamId = svTeam.seasonalTeamId,
                    svLeagueCaption = svTeam.league?.caption,
                    svGender = svTeam.gender
                )
            } else {
                integrationRepository.deprecateLink(clubId, svTeamId)
            }
        }

        // New SV teams not linked anywhere in the club -> notify managers (always-on, user-targeted).
        val linkedSvIds = integrationRepository.listLinkedSvTeamIdsForClub(clubId)
        val newSvTeams = svTeamsById.filterKeys { it !in linkedSvIds }.values
        if (newSvTeams.isNotEmpty()) {
            val managerIds = notificationRepository.getManagerIdsForClub(clubId)
            if (managerIds.isNotEmpty()) {
                for (svTeam in newSvTeams) {
                    val svTeamId = svTeam.teamId ?: continue
                    notificationDispatcher.notifyUsers(
                        userIds = managerIds,
                        type = "sv_team_available",
                        title = "Neues SwissVolley-Team",
                        body = svTeam.caption ?: "Neues Team verfügbar",
                        entityId = clubId,
                        entityType = "club",
                        idempotencyKeySuffix = svTeamId.toString()
                    )
                }
            }
        }
    }

    /** Immediate sync for a single team (toggle-on); same per-game logic, scoped to the team. */
    suspend fun syncTeam(teamId: UUID) {
        val clubId = teamRepository.getClubId(teamId) ?: run {
            logger.warn("syncTeam: no club for team $teamId; skipping")
            return
        }
        runSync(clubId, restrictToTeamId = teamId)
    }

    private suspend fun runSync(clubId: UUID, restrictToTeamId: UUID?) {
        val integration = integrationRepository.getIntegration(clubId)
        if (integration == null || integration.keyValid != true) {
            logger.info("syncClub $clubId: no valid integration, skipping")
            return
        }
        val apiKey = integrationRepository.getApiKey(clubId) ?: run {
            logger.info("syncClub $clubId: no api key, skipping")
            return
        }

        val games: List<SVGame> = try {
            swissVolleyClient.listGames(apiKey)
        } catch (e: InvalidApiKeyException) {
            handleInvalidKey(clubId)
            return
        }

        // Map svTeamId -> the sync-enabled TeamOrg teams linked to it (derby => multiple).
        val syncEnabledLinks: List<SyncEnabledLink> = integrationRepository.listSyncEnabledLinks(clubId)
            .let { links ->
                if (restrictToTeamId == null) links
                else links.filter { UUID.fromString(it.teamId) == restrictToTeamId }
            }
        if (syncEnabledLinks.isEmpty()) {
            integrationRepository.upsertSyncState(clubId, Instant.now(), "ok", null)
            return
        }
        val teamsBySvId: Map<Int, List<UUID>> = syncEnabledLinks
            .groupBy({ it.svTeamId }, { UUID.fromString(it.teamId) })
        val syncedSvIds: Set<Int> = teamsBySvId.keys

        try {
            val relevantGames = games.filter { game ->
                val home = game.teams?.home?.teamId
                val away = game.teams?.away?.teamId
                (home != null && home in syncedSvIds) || (away != null && away in syncedSvIds)
            }

            val seenGameIds = mutableSetOf<Long>()
            for (game in relevantGames) {
                val gameId = game.gameId?.toLong() ?: continue
                seenGameIds += gameId
                upsertGame(game, gameId, teamsBySvId, syncedSvIds)
            }

            // In DB for this scope but absent from the feed -> postponed (never cancel/delete).
            val knownIds = if (restrictToTeamId == null) {
                eventRepository.listSyncedExternalGameIds(clubId)
            } else {
                // Scope to games whose linked team includes the restricted team would require a
                // team-scoped query; for the toggle-on path we only add/refresh, postponing of
                // vanished games is handled by the club-wide pass.
                emptyList()
            }
            for (knownId in knownIds) {
                if (knownId !in seenGameIds) {
                    eventRepository.findByExternalGameId(EXTERNAL_SOURCE, knownId)?.let { ref ->
                        if (ref.externalStatus != "postponed") {
                            eventRepository.markPostponed(ref.id)
                        }
                    }
                }
            }

            integrationRepository.upsertSyncState(clubId, Instant.now(), "ok", null)
        } catch (e: Exception) {
            logger.error("syncClub $clubId failed", e)
            integrationRepository.upsertSyncState(clubId, Instant.now(), "error", e.message)
        }
    }

    private suspend fun upsertGame(
        game: SVGame,
        gameId: Long,
        teamsBySvId: Map<Int, List<UUID>>,
        syncedSvIds: Set<Int>
    ) {
        val home = game.teams?.home
        val away = game.teams?.away
        val homeSvId = home?.teamId
        val awaySvId = away?.teamId
        if (homeSvId == null || awaySvId == null) return

        val playDateUtc = game.playDateUtc ?: return
        val startAt = try {
            Instant.parse(playDateUtc)
        } catch (e: Exception) {
            logger.warn("Skipping game $gameId: unparseable playDateUtc '$playDateUtc'")
            return
        }
        val endAt = startAt.plusSeconds(2 * 60 * 60)
        val title = "${home.caption ?: "?"} vs ${away.caption ?: "?"}"
        val location = locationOf(game)
        val hash = matchHash(playDateUtc, game.hall?.hallId, homeSvId, awaySvId)

        // All sync-enabled TeamOrg teams participating in this game (derby => >1).
        val linkedTeamIds: List<UUID> = buildList {
            if (homeSvId in syncedSvIds) addAll(teamsBySvId[homeSvId].orEmpty())
            if (awaySvId in syncedSvIds) addAll(teamsBySvId[awaySvId].orEmpty())
        }.distinct()
        if (linkedTeamIds.isEmpty()) return

        val existing = eventRepository.findByExternalGameId(EXTERNAL_SOURCE, gameId)
        val finishedOrLive = isFinishedOrLive(game)

        if (existing == null) {
            val event = eventRepository.createSyncedMatch(
                title = title,
                startAt = startAt,
                endAt = endAt,
                location = location,
                externalSource = EXTERNAL_SOURCE,
                externalGameId = gameId,
                externalHash = hash,
                createdBy = SystemUsers.VOLLEY_MANAGER,
                teamIds = linkedTeamIds
            )
            for (teamId in linkedTeamIds) {
                notificationDispatcher.notifyTeamCoaches(
                    teamId = teamId,
                    excludeUserId = null,
                    type = "sv_game_new",
                    title = "Neues Spiel",
                    body = title,
                    entityId = event.id,
                    entityType = "event",
                    idempotencyKeySuffix = hash
                )
            }
            return
        }

        // Previously postponed game reappeared in the feed -> revive it.
        if (existing.externalStatus == "postponed") {
            eventRepository.clearPostponedToSynced(existing.id)
        }

        // Finished/live games: never touch facts (design §6 / §13.3).
        if (finishedOrLive) return

        // Only update + notify when the source facts actually changed.
        if (existing.externalHash != hash) {
            eventRepository.updateSyncedFacts(
                eventId = existing.id,
                title = title,
                startAt = startAt,
                endAt = endAt,
                location = location,
                newHash = hash
            )
            for (teamId in linkedTeamIds) {
                notificationDispatcher.notifyTeamCoaches(
                    teamId = teamId,
                    excludeUserId = null,
                    type = "sv_game_changed",
                    title = "Spiel geändert",
                    body = title,
                    entityId = existing.id,
                    entityType = "event",
                    // Fold the new hash in so re-polling an unreconciled change won't re-notify.
                    idempotencyKeySuffix = hash
                )
            }
        }
    }

    private suspend fun handleInvalidKey(clubId: UUID) {
        integrationRepository.setKeyInvalid(clubId, "Valid API-Key required")
        integrationRepository.upsertSyncState(clubId, Instant.now(), "paused", "Valid API-Key required")
        val managerIds = notificationRepository.getManagerIdsForClub(clubId)
        notificationDispatcher.notifyUsers(
            userIds = managerIds,
            type = "sv_key_invalid",
            title = "SwissVolley-Schlüssel ungültig",
            body = "Die Synchronisation wurde pausiert. Bitte den API-Schlüssel erneuern.",
            entityId = null,
            entityType = "club",
            idempotencyKeySuffix = clubId.toString()
        )
        logger.warn("syncClub $clubId: invalid api key, sync paused")
    }

    private fun locationOf(game: SVGame): String? {
        val hall = game.hall ?: return null
        val caption = hall.caption
        val city = hall.city
        return when {
            caption != null && city != null -> "$caption, $city"
            caption != null -> caption
            else -> city
        }
    }

    /** Finished/live = results present (design §13.3) — don't trust the undocumented status int. */
    private fun isFinishedOrLive(game: SVGame): Boolean =
        !game.setResults.isNullOrEmpty() || game.resultSummary != null
}
