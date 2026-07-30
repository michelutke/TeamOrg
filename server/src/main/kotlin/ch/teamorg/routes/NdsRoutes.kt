package ch.teamorg.routes

import ch.teamorg.domain.models.MemberSuggestionDto
import ch.teamorg.domain.models.NdsMember
import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.domain.models.NdsParseResponse
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.InviteRepository
import ch.teamorg.domain.repositories.NdsRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.infra.nds.AnwesenheitslisteParser
import ch.teamorg.infra.nds.MemberSuggestion
import ch.teamorg.infra.nds.NdsEventImporter
import ch.teamorg.infra.nds.NdsImportCounts
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
import java.util.UUID

@Serializable
data class NdsImportRequest(
    val teamId: String? = null,
    val createTeamName: String? = null,
    val nutzergruppe: String? = null,
    val parsed: ParsedAnwesenheitsliste,
    // Persons from the dedicated NDS exports (Teilnehmende CSV / Leiter xlsx); carry PERSONENNUMMER.
    // Applied BEFORE the Anwesenheitsliste roster so names merge and person numbers are preserved.
    val persons: List<NdsMemberInput> = emptyList(),
    val importEvents: Boolean = false,
    val attendanceMode: String = "discard" // 'keep' | 'discard'
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

        // Commit a (possibly edited) parsed list: create/link team + import roster (+ events).
        post("/clubs/{clubId}/nds/import") {
            val clubId = UUID.fromString(call.parameters["clubId"])
            if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@post

            val callerId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")
            val request = call.receive<NdsImportRequest>()
            val parsed = request.parsed

            // Resolve target team.
            val teamId: UUID = when {
                request.teamId != null -> {
                    val tid = UUID.fromString(request.teamId)
                    if (teamRepository.getClubId(tid) != clubId) {
                        return@post call.respond(HttpStatusCode.Forbidden, "Team gehört nicht zu diesem Club")
                    }
                    tid
                }
                !request.createTeamName.isNullOrBlank() -> {
                    // The Angebot may only be linked to a single team — check BEFORE creating,
                    // otherwise every rejected re-import attempt leaves an orphan empty team.
                    val existing = ndsRepository.findTeamIdByAngebot(parsed.angebotId, clubId)
                    if (existing != null) {
                        val name = teamRepository.findById(existing)?.name ?: "einem anderen Team"
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            "Angebot ${parsed.angebotId} ist bereits mit «$name» verknüpft — " +
                                "der Import aktualisiert dieses Team (Datei erneut hochladen)."
                        )
                    }
                    UUID.fromString(teamRepository.create(clubId, request.createTeamName.trim(), null).id)
                }
                else -> return@post call.respond(HttpStatusCode.BadRequest, "teamId oder createTeamName erforderlich")
            }

            // The Angebot may only be linked to a single team within this club (scoped per-club so
            // another club's link never blocks this import).
            val existingForAngebot = ndsRepository.findTeamIdByAngebot(parsed.angebotId, clubId)
            if (existingForAngebot != null && existingForAngebot != teamId) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    "Angebot ${parsed.angebotId} ist bereits mit einem anderen Team verknüpft"
                )
            }

            ndsRepository.linkTeam(
                teamId = teamId,
                angebotId = parsed.angebotId,
                kursName = parsed.kursName,
                hauptsportart = parsed.hauptsportart,
                nutzergruppe = request.nutzergruppe
            )

            // Persons first (PERSONENNUMMER), then the Anwesenheitsliste roster merges by name.
            if (request.persons.isNotEmpty()) ndsRepository.upsertMembers(teamId, request.persons)
            ndsRepository.importRoster(teamId, parsed.members)

            val counts = if (request.importEvents)
                ndsEventImporter.import(teamId, parsed, request.attendanceMode, callerId)
            else NdsImportCounts(0, 0)

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
