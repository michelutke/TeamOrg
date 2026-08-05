package ch.teamorg.routes

import ch.teamorg.domain.models.DuplicateSuggestion
import ch.teamorg.domain.models.MemberSuggestionDto
import ch.teamorg.domain.models.MovableCounts
import ch.teamorg.domain.models.NdsConflictResolution
import ch.teamorg.domain.models.NdsMapping
import ch.teamorg.domain.models.NdsMember
import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.domain.models.NdsParseResponse
import ch.teamorg.domain.models.NdsSeriesTime
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.InviteRepository
import ch.teamorg.domain.repositories.NdsMappingConflictException
import ch.teamorg.domain.repositories.NdsRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.infra.nds.AnwesenheitslisteParser
import ch.teamorg.infra.nds.MemberSuggestion
import ch.teamorg.infra.nds.NdsEventImporter
import ch.teamorg.infra.nds.NdsImportPlanner
import ch.teamorg.infra.nds.NdsExportService
import ch.teamorg.infra.nds.NdsMemberMatcher
import ch.teamorg.infra.nds.NdsParseException
import ch.teamorg.infra.nds.RosterFileParser
import ch.teamorg.mail.MailService
import ch.teamorg.mail.buildInviteEmail
import ch.teamorg.middleware.requireClubRole
import ch.teamorg.middleware.requireTeamRole
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Serializable
data class NdsImportRequest(
    val teamId: String? = null,
    val createTeamName: String? = null,
    val nutzergruppe: String? = null,
    // Null for a persons-only import (no Anwesenheitsliste in this wizard run).
    val parsed: ParsedAnwesenheitsliste? = null,
    // Persons from the dedicated NDS exports (Teilnehmende CSV / Leiter xlsx); carry PERSONENNUMMER.
    // Merged with the Anwesenheitsliste roster (by rowKey) before any writes.
    val persons: List<NdsMemberInput> = emptyList(),
    val importEvents: Boolean = false,
    val attendanceMode: String = "discard", // 'keep' | 'discard'
    // Wizard decisions — Mitglieder-Zuordnung / Events & Konflikte steps. All default to empty,
    // which preserves the pre-wizard behavior (every row auto-created, 18:00 placeholder removed
    // upstream — a series importing events without a matching seriesTimes entry is now a 400).
    val mappings: List<NdsMapping> = emptyList(),
    val seriesTimes: List<NdsSeriesTime> = emptyList(),
    val conflictResolutions: List<NdsConflictResolution> = emptyList()
)

@Serializable
data class NdsImportResponse(
    val teamId: String,
    val membersImported: Int,
    val eventsCreated: Int,
    val attendanceImported: Int = 0
)

@Serializable
data class NdsMemberUpdateRequest(
    val personNumber: String? = null,
    val lastName: String? = null,
    val firstName: String? = null,
    val birthDate: String? = null
)

@Serializable
data class NdsMemberInviteRequest(val email: String? = null)

@Serializable
data class NdsMemberLinkRequest(val userId: String)

fun Route.ndsRoutes() {
    val clubRepository by inject<ClubRepository>()
    val teamRepository by inject<TeamRepository>()
    val ndsRepository by inject<NdsRepository>()
    val inviteRepository by inject<InviteRepository>()
    val userRepository by inject<UserRepository>()
    val mailService by inject<MailService>()
    val ndsEventImporter by inject<NdsEventImporter>()
    val ndsImportPlanner by inject<NdsImportPlanner>()
    val ndsExportService by inject<NdsExportService>()

    val inviteBaseUrl = application.environment.config
        .propertyOrNull("invite.base-url")?.getString()?.trim()
        ?.ifBlank { null } ?: "https://teamorg.ch"
    fun inviteUrlFor(token: String) = "$inviteBaseUrl/i/$token"

    authenticate("jwt") {
        // Parse uploaded NDS file(s) → preview JSON (no DB writes). Multipart part NAMES select the
        // parser: "anwesenheitsliste" (xlsx), "teilnehmende" (csv), "leiter" (xlsx); any other file
        // part name is treated as the Anwesenheitsliste (keeps the legacy single-file upload working).
        // Optional form field "teamId" scopes match suggestions + conflicts to that team and lets a
        // team coach (not just the club manager) run the parse.
        post("/clubs/{clubId}/nds/parse") {
            val clubId = UUID.fromString(call.parameters["clubId"])
            val callerId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            var teamIdParam: UUID? = null
            var teamIdInvalid = false
            var anwesenheitslisteBytes: ByteArray? = null
            var teilnehmendeBytes: ByteArray? = null
            var leiterBytes: ByteArray? = null

            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "teamId") {
                            val value = part.value.trim()
                            if (value.isNotEmpty()) {
                                teamIdParam = runCatching { UUID.fromString(value) }.getOrNull()
                                if (teamIdParam == null) teamIdInvalid = true
                            }
                        }
                    }
                    is PartData.FileItem -> {
                        val bytes = part.provider().toInputStream().use { it.readBytes() }
                        when (part.name) {
                            "teilnehmende" -> teilnehmendeBytes = bytes
                            "leiter" -> leiterBytes = bytes
                            else -> if (anwesenheitslisteBytes == null) anwesenheitslisteBytes = bytes
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (teamIdInvalid) return@post call.respond(HttpStatusCode.BadRequest, "Ungültige teamId")
            if (anwesenheitslisteBytes == null && teilnehmendeBytes == null && leiterBytes == null) {
                return@post call.respond(HttpStatusCode.BadRequest, "Keine Datei hochgeladen")
            }

            val teamId = teamIdParam
            // A teamId that doesn't belong to this club is rejected outright — it is never a valid
            // scope for this request, regardless of which role granted access below.
            if (teamId != null && teamRepository.getClubId(teamId) != clubId) {
                return@post call.respond(HttpStatusCode.Forbidden, "Team gehört nicht zu diesem Club")
            }
            val isClubManager = clubRepository.hasRole(callerId, clubId, "club_manager")
            val authorized = isClubManager || (teamId != null && teamRepository.hasRole(callerId, teamId, "coach"))
            if (!authorized) return@post call.respond(HttpStatusCode.Forbidden, "Keine Berechtigung für diesen Import")

            var parseError: String? = null
            val anwesenheitsliste = try {
                anwesenheitslisteBytes?.let { AnwesenheitslisteParser.parse(it.inputStream()) }
            } catch (ex: NdsParseException) {
                parseError = ex.message ?: "Datei konnte nicht gelesen werden"
                call.application.log.info("NDS parse rejected: ${ex.message}")
                null
            }

            val teilnehmendePersons = if (parseError == null) {
                try {
                    teilnehmendeBytes?.let { RosterFileParser.parseTeilnehmendeCsv(it.inputStream()) } ?: emptyList()
                } catch (ex: NdsParseException) {
                    parseError = ex.message ?: "Datei konnte nicht gelesen werden"
                    emptyList()
                }
            } else emptyList()

            val leiterPersons = if (parseError == null) {
                try {
                    leiterBytes?.let { RosterFileParser.parseLeiterXlsx(it.inputStream()) } ?: emptyList()
                } catch (ex: NdsParseException) {
                    parseError = ex.message ?: "Datei konnte nicht gelesen werden"
                    emptyList()
                }
            } else emptyList()

            if (parseError != null) return@post call.respond(HttpStatusCode.UnprocessableEntity, parseError)

            val persons = teilnehmendePersons + leiterPersons

            // Surface an existing Angebot→team link (same club only) so the import dialog
            // re-imports into that team instead of creating a new one.
            val linkedTeam = anwesenheitsliste?.let { ndsRepository.findTeamIdByAngebot(it.angebotId, clubId) }
                ?.let { teamRepository.findById(it) }

            val targetTeamId = teamId ?: linkedTeam?.let { UUID.fromString(it.id) }

            val memberSuggestions = if (targetTeamId != null) {
                val rows = mergeMemberRows(anwesenheitsliste, persons)
                val teamUsers = ndsRepository.listTeamUsersForMatching(targetTeamId)
                NdsMemberMatcher.suggest(rows, teamUsers).map { it.toDto() }
            } else emptyList()

            val series = anwesenheitsliste?.let { ndsImportPlanner.series(it.activities) } ?: emptyList()
            val conflicts = if (targetTeamId != null && series.isNotEmpty())
                ndsImportPlanner.conflicts(targetTeamId, series)
            else emptyList()

            call.respond(
                NdsParseResponse(
                    anwesenheitsliste = anwesenheitsliste,
                    persons = persons,
                    memberSuggestions = memberSuggestions,
                    series = series,
                    conflicts = conflicts,
                    linkedTeamId = linkedTeam?.id,
                    linkedTeamName = linkedTeam?.name
                )
            )
        }

        // Deprecated: kept only for backward compatibility with older clients that upload the
        // dedicated person exports (Teilnehmende .csv / Leiter .xlsx) separately. New clients
        // should send those as the "teilnehmende"/"leiter" parts of POST .../nds/parse instead.
        post("/clubs/{clubId}/nds/parse-roster") {
            val clubId = UUID.fromString(call.parameters["clubId"])
            if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@post

            var persons: List<NdsMemberInput>? = null
            var parseError: String? = null
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem && persons == null && parseError == null) {
                    val name = part.originalFileName?.lowercase() ?: ""
                    try {
                        part.provider().toInputStream().use { stream ->
                            persons = if (name.endsWith(".csv")) RosterFileParser.parseTeilnehmendeCsv(stream)
                            else RosterFileParser.parseLeiterXlsx(stream)
                        }
                    } catch (ex: NdsParseException) {
                        parseError = ex.message ?: "Datei konnte nicht gelesen werden"
                    }
                }
                part.dispose()
            }

            when {
                parseError != null -> call.respond(HttpStatusCode.UnprocessableEntity, parseError)
                persons == null -> call.respond(HttpStatusCode.BadRequest, "Keine Datei hochgeladen")
                else -> call.respond(persons)
            }
        }

        // Commit a (possibly edited) parsed list: create/link team + import roster (+ events),
        // driven by the wizard's mappings/seriesTimes/conflictResolutions. Member application,
        // event/series creation and attendance all happen in ONE transaction inside
        // ndsEventImporter.import — any failure rolls back the whole import.
        post("/clubs/{clubId}/nds/import") {
            val clubId = UUID.fromString(call.parameters["clubId"])
            val callerId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")
            val request = call.receive<NdsImportRequest>()
            val parsed = request.parsed

            // Auth: identical to parse — coach of the target team OR club_manager. A team that
            // doesn't belong to this club is rejected outright, before any role check.
            val requestedTeamId = request.teamId?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Ungültige teamId")
            }
            if (requestedTeamId != null && teamRepository.getClubId(requestedTeamId) != clubId) {
                return@post call.respond(HttpStatusCode.Forbidden, "Team gehört nicht zu diesem Club")
            }
            val isClubManager = clubRepository.hasRole(callerId, clubId, "club_manager")
            val authorized = isClubManager || (requestedTeamId != null && teamRepository.hasRole(callerId, requestedTeamId, "coach"))
            if (!authorized) return@post call.respond(HttpStatusCode.Forbidden, "Keine Berechtigung für diesen Import")

            // Resolve the target team WITHOUT creating anything yet — team creation is deferred
            // until after all validation passes (see below), otherwise a request that ultimately
            // fails validation would leave an orphan empty team behind (the same failure mode the
            // Angebot-conflict guards below exist to prevent).
            when {
                requestedTeamId != null -> {}
                !request.createTeamName.isNullOrBlank() -> {
                    if (!isClubManager) {
                        return@post call.respond(HttpStatusCode.Forbidden, "Nur Club-Manager dürfen ein neues Team erstellen")
                    }
                }
                else -> return@post call.respond(HttpStatusCode.BadRequest, "teamId oder createTeamName erforderlich")
            }

            if (parsed != null) {
                // The Angebot may only be linked to a single team within this club (scoped per-club
                // so another club's link never blocks this import) — checked against whichever team
                // this request targets: the existing one, or (for createTeamName) any team already
                // holding this Angebot, so a rejected create-new-team attempt never orphans a team.
                val existingForAngebot = ndsRepository.findTeamIdByAngebot(parsed.angebotId, clubId)
                if (existingForAngebot != null && existingForAngebot != requestedTeamId) {
                    val name = teamRepository.findById(existingForAngebot)?.name ?: "einem anderen Team"
                    return@post call.respond(
                        HttpStatusCode.Conflict,
                        if (requestedTeamId == null)
                            "Angebot ${parsed.angebotId} ist bereits mit «$name» verknüpft — " +
                                "der Import aktualisiert dieses Team (Datei erneut hochladen)."
                        else
                            "Angebot ${parsed.angebotId} ist bereits mit einem anderen Team verknüpft"
                    )
                }
            }

            // ---- Validation BEFORE any writes ---- (uses requestedTeamId; for a not-yet-created
            // team there can be no existing events/members, so conflicts are necessarily empty)
            val mergedRows = mergeMemberRows(parsed, request.persons)
            val rowKeys = mergedRows.map { NdsMemberMatcher.rowKey(it.funktion, it.lastName, it.firstName) }.toSet()

            val seenMappedUserIds = HashSet<UUID>()
            for (mapping in request.mappings) {
                if (mapping.rowKey !in rowKeys) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Unbekannter Datensatz: ${mapping.rowKey}")
                }
                when (mapping.action) {
                    "map" -> {
                        val uid = mapping.userId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@post call.respond(HttpStatusCode.BadRequest, "userId erforderlich für Zuordnung von ${mapping.rowKey}")
                        if (!clubRepository.isMember(uid, clubId)) {
                            return@post call.respond(HttpStatusCode.BadRequest, "Nutzer ist kein Mitglied dieses Clubs")
                        }
                        if (!seenMappedUserIds.add(uid)) {
                            return@post call.respond(HttpStatusCode.BadRequest, "Nutzer ist mehrfach zugeordnet")
                        }
                    }
                    "create", "skip" -> {}
                    else -> return@post call.respond(HttpStatusCode.BadRequest, "Ungültige Aktion: ${mapping.action}")
                }
            }

            val timePattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
            for (t in request.seriesTimes) {
                if (!timePattern.matches(t.startTime) || !timePattern.matches(t.endTime)) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Ungültige Uhrzeit für Serie ${t.seriesKey}")
                }
                if (!LocalTime.parse(t.endTime).isAfter(LocalTime.parse(t.startTime))) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Endzeit muss nach der Startzeit liegen (${t.seriesKey})")
                }
            }

            // Conflicts + series/time requirements only matter when events are actually imported.
            var effectiveResolutionByDate: Map<LocalDate, String> = emptyMap()
            if (request.importEvents && parsed != null) {
                val series = ndsImportPlanner.series(parsed.activities)
                // Recomputed fresh — never trust the client's conflict list. A not-yet-created team
                // (requestedTeamId == null) cannot have existing events, so conflicts are empty.
                val freshConflicts = if (requestedTeamId != null && series.isNotEmpty())
                    ndsImportPlanner.conflicts(requestedTeamId, series)
                else emptyList()
                val resolutionBySeriesKey = request.conflictResolutions.associateBy { it.seriesKey }

                for (group in freshConflicts) {
                    val resolution = resolutionBySeriesKey[group.seriesKey]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Konflikt für Serie ${group.seriesKey} nicht aufgelöst")
                    if (resolution.keep !in setOf("teamorg", "nds")) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Ungültige Konfliktentscheidung für ${group.seriesKey}")
                    }
                    if (resolution.overrides.any { it.keep !in setOf("teamorg", "nds") }) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Ungültige Konfliktentscheidung für ${group.seriesKey}")
                    }
                }

                val resolved = HashMap<LocalDate, String>()
                for (group in freshConflicts) {
                    val resolution = resolutionBySeriesKey.getValue(group.seriesKey)
                    val overridesByDate = resolution.overrides.associateBy { it.date }
                    for (d in group.dates) {
                        resolved[d.date] = overridesByDate[d.date]?.keep ?: resolution.keep
                    }
                }
                effectiveResolutionByDate = resolved

                val seriesTimeByKey = request.seriesTimes.associateBy { it.seriesKey }
                for (s in series) {
                    val importsAnyEvent = s.dates.any { d -> (resolved[d] ?: "nds") != "teamorg" }
                    if (importsAnyEvent && seriesTimeByKey[s.seriesKey] == null) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Uhrzeit fehlt für Serie ${s.seriesKey}")
                    }
                }
            }
            // ---- End validation ----

            // Validation passed — only now do we create the team (if this is a new one).
            val teamId: UUID = requestedTeamId
                ?: UUID.fromString(teamRepository.create(clubId, request.createTeamName!!.trim(), null).id)

            if (parsed != null) {
                ndsRepository.linkTeam(
                    teamId = teamId,
                    angebotId = parsed.angebotId,
                    kursName = parsed.kursName,
                    hauptsportart = parsed.hauptsportart,
                    nutzergruppe = request.nutzergruppe
                )
            }

            val counts = try {
                ndsEventImporter.import(
                    teamId = teamId,
                    parsed = parsed,
                    attendanceMode = request.attendanceMode,
                    createdBy = callerId,
                    importEvents = request.importEvents,
                    mergedMemberRows = mergedRows,
                    mappingsByRowKey = request.mappings.associateBy { it.rowKey },
                    seriesTimes = request.seriesTimes.associateBy { it.seriesKey },
                    resolutions = effectiveResolutionByDate
                )
            } catch (ex: NdsMappingConflictException) {
                return@post call.respond(HttpStatusCode.Conflict, ex.message ?: "Konflikt")
            }

            call.respond(
                HttpStatusCode.OK,
                NdsImportResponse(
                    teamId = teamId.toString(),
                    membersImported = ndsRepository.listMembers(teamId).size,
                    eventsCreated = counts.eventsCreated,
                    attendanceImported = counts.attendanceImported
                )
            )
        }

        // Roster for a team (claim status + missing person-number visibility).
        get("/teams/{teamId}/nds/members") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@get
            call.respond(ndsRepository.listMembers(teamId))
        }

        // Unresolved roster rows plus the real accounts they might be duplicates of.
        get("/teams/{teamId}/nds/duplicate-suggestions") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@get

            val unresolved = ndsRepository.listUnresolvedMembers(teamId)
            if (unresolved.isEmpty()) return@get call.respond(emptyList<DuplicateSuggestion>())

            val teamUsers = ndsRepository.listTeamUsersForMatching(teamId)
            val rows = unresolved.map {
                NdsMemberInput(it.lastName, it.firstName, it.birthDate, it.personNumber, it.funktion)
            }
            val suggestionsByRowKey = NdsMemberMatcher.suggest(rows, teamUsers).associateBy { it.rowKey }

            val result = unresolved.mapNotNull { member ->
                val key = NdsMemberMatcher.rowKey(member.funktion, member.lastName, member.firstName)
                val candidates = suggestionsByRowKey[key]?.candidates.orEmpty()
                if (candidates.isEmpty()) return@mapNotNull null
                DuplicateSuggestion(
                    memberId = member.id,
                    lastName = member.lastName,
                    firstName = member.firstName,
                    birthDate = member.birthDate,
                    personNumber = member.personNumber,
                    funktion = member.funktion,
                    candidates = candidates.map {
                        DuplicateSuggestion.Candidate(it.userId, it.displayName, it.score)
                    },
                    willMove = member.userId
                        ?.let { ndsRepository.countMovableRows(it, teamId) }
                        ?: MovableCounts(0, 0, 0)
                )
            }
            call.respond(result)
        }

        // Update a member's NDS data. Coaches/managers edit anyone; a member edits only their own row.
        patch("/teams/{teamId}/nds/members/{id}") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            val memberId = UUID.fromString(call.parameters["id"])
            val callerId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@patch call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            // Must at least be on the team.
            if (!call.requireTeamRole(teamId, "player", "coach", "club_manager", teamRepository = teamRepository)) return@patch

            val member = ndsRepository.getMember(memberId)
            if (member == null || member.teamId != teamId) {
                return@patch call.respond(HttpStatusCode.NotFound, "Mitglied nicht gefunden")
            }

            val elevated = teamRepository.hasRole(callerId, teamId, "coach", "club_manager")
            if (!elevated && member.userId != callerId) {
                return@patch call.respond(HttpStatusCode.Forbidden, "Nur das eigene Profil darf bearbeitet werden")
            }

            val body = call.receive<NdsMemberUpdateRequest>()
            val birth = body.birthDate?.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "Ungültiges Geburtsdatum (YYYY-MM-DD)")
            }
            if (body.personNumber != null && body.personNumber.isNotBlank() &&
                !Regex("^\\d{6,12}$").matches(body.personNumber.trim())
            ) {
                return@patch call.respond(HttpStatusCode.BadRequest, "Ungültige Personennummer")
            }

            val updated = ndsRepository.updateMember(
                memberId = memberId,
                personNumber = body.personNumber?.trim()?.ifBlank { null },
                lastName = body.lastName,
                firstName = body.firstName,
                birthDate = birth
            )
            if (updated == null) call.respond(HttpStatusCode.NotFound, "Mitglied nicht gefunden")
            else call.respond(updated)
        }

        // Create a per-member invite link (claims the roster member on redeem).
        post("/teams/{teamId}/nds/members/{id}/invite") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            val memberId = UUID.fromString(call.parameters["id"])
            if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@post
            val callerId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val member = ndsRepository.getMember(memberId)
            if (member == null || member.teamId != teamId) {
                return@post call.respond(HttpStatusCode.NotFound, "Mitglied nicht gefunden")
            }

            val body = call.receive<NdsMemberInviteRequest>()
            val email = body.email?.trim()?.lowercase()?.ifBlank { null }
            val role = if (member.funktion == "Leiter/in") "coach" else "player"

            val invite = inviteRepository.createNdsMemberInvite(
                teamId = teamId,
                createdByUserId = callerId,
                role = role,
                email = email,
                ndsMemberId = memberId
            )

            if (email != null) {
                runCatching {
                    val inviter = userRepository.findById(callerId)
                    val details = inviteRepository.getInviteDetails(invite.token)
                    if (inviter != null && details != null) {
                        val mail = buildInviteEmail(
                            inviterName = inviter.displayName,
                            teamName = details.teamName,
                            clubName = details.clubName,
                            role = invite.role,
                            inviteUrl = inviteUrlFor(invite.token),
                            expiresAt = invite.expiresAt
                        )
                        mailService.send(
                            to = email,
                            subject = mail.subject,
                            plainText = mail.plainText,
                            html = mail.html,
                            replyToName = inviter.displayName,
                            replyToEmail = inviter.email
                        )
                    }
                }.onFailure { call.application.log.error("NDS invite email failed", it) }
            }

            call.respond(
                HttpStatusCode.Created,
                InviteResponse(invite.token, inviteUrlFor(invite.token), invite.expiresAt)
            )
        }

        // Link an existing account directly to an imported roster member (no invite flow needed).
        post("/teams/{teamId}/nds/members/{id}/link") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            val memberId = UUID.fromString(call.parameters["id"])
            if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@post
            val member = ndsRepository.getMember(memberId)
            if (member == null || member.teamId != teamId)
                return@post call.respond(HttpStatusCode.NotFound, "Mitglied nicht gefunden")
            val userId = runCatching { UUID.fromString(call.receive<NdsMemberLinkRequest>().userId) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Ungültige userId")
            if (userRepository.findById(userId) == null)
                return@post call.respond(HttpStatusCode.NotFound, "Konto nicht gefunden")
            if (ndsRepository.isProvisionalUser(userId))
                return@post call.respond(HttpStatusCode.BadRequest, "Provisorische Konten können nicht verknüpft werden")

            val existingMemberId = ndsRepository.findMemberIdByUser(teamId, userId)
            if (existingMemberId != null && existingMemberId != memberId)
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    "Dieses Konto ist bereits mit einem anderen Mitglied dieses Teams verknüpft"
                )
            val role = if (member.funktion == "Leiter/in") "coach" else "player"
            teamRepository.addMember(teamId, userId, role)
            ndsRepository.claimMember(memberId, userId)
            val updated = ndsRepository.getMember(memberId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Mitglied nicht gefunden")
            call.respond(HttpStatusCode.OK, updated)
        }

        // Validation report before an export (lists blocking errors + warnings).
        get("/teams/{teamId}/nds/export/preflight") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@get
            call.respond(ndsExportService.preflight(teamId))
        }

        // Download both NDS import CSVs as a ZIP. Blocks (409 + report) if pre-flight has errors.
        get("/teams/{teamId}/nds/export") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@get

            val report = ndsExportService.preflight(teamId)
            if (!report.ok) {
                return@get call.respond(HttpStatusCode.Conflict, report)
            }

            val bundle = ndsExportService.buildCsvs(teamId)
            val zip = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(zip).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("Aktivitaeten-Import.csv"))
                zos.write(bundle.aktivitaetenCsv.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(java.util.zip.ZipEntry("Anwesenheitskontrolle-Import.csv"))
                zos.write(bundle.awkCsv.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "nds-export.zip").toString()
            )
            call.respondBytes(zip.toByteArray(), ContentType.Application.Zip)
        }
    }
}

/**
 * Union of the Anwesenheitsliste roster and the dedicated person exports for match suggestions,
 * deduped by [NdsMemberMatcher.rowKey]. The Anwesenheitsliste wins on birthdate (it is the freshest
 * export); the person export's PERSONENNUMMER is preserved when the Anwesenheitsliste row lacks one.
 */
private fun mergeMemberRows(awl: ParsedAnwesenheitsliste?, persons: List<NdsMemberInput>): List<NdsMemberInput> {
    val merged = LinkedHashMap<String, NdsMemberInput>()
    for (p in persons) merged[NdsMemberMatcher.rowKey(p.funktion, p.lastName, p.firstName)] = p
    awl?.members?.forEach { m ->
        val input = NdsMemberInput(m.lastName, m.firstName, m.birthDate, null, m.funktion)
        val key = NdsMemberMatcher.rowKey(input.funktion, input.lastName, input.firstName)
        val existing = merged[key]
        merged[key] = if (existing != null) {
            input.copy(personNumber = existing.personNumber ?: input.personNumber, birthDate = input.birthDate ?: existing.birthDate)
        } else input
    }
    return merged.values.toList()
}

private fun MemberSuggestion.toDto() = MemberSuggestionDto(
    rowKey = rowKey,
    candidates = candidates.map {
        MemberSuggestionDto.CandidateDto(it.userId.toString(), it.displayName, it.score, it.birthdateMatch)
    },
    preselectedUserId = preselectedUserId?.toString(),
    alreadyLinkedUserId = alreadyLinkedUserId?.toString()
)
