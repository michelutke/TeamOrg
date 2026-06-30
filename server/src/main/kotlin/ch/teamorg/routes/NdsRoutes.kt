package ch.teamorg.routes

import ch.teamorg.domain.models.NdsMember
import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.InviteRepository
import ch.teamorg.domain.repositories.NdsRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.infra.nds.AnwesenheitslisteParser
import ch.teamorg.infra.nds.NdsEventImporter
import ch.teamorg.infra.nds.NdsImportCounts
import ch.teamorg.infra.nds.NdsExportService
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
    val ndsExportService by inject<NdsExportService>()

    val inviteBaseUrl = application.environment.config
        .propertyOrNull("invite.base-url")?.getString()?.trim()
        ?.ifBlank { null } ?: "https://teamorg.ch"
    fun inviteUrlFor(token: String) = "$inviteBaseUrl/i/$token"

    authenticate("jwt") {
        // Parse an uploaded Anwesenheitsliste xlsx → preview JSON (no DB writes).
        post("/clubs/{clubId}/nds/parse") {
            val clubId = UUID.fromString(call.parameters["clubId"])
            if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@post

            var parsed: ParsedAnwesenheitsliste? = null
            var parseError: String? = null
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && parsed == null && parseError == null) {
                    try {
                        parsed = part.provider().toInputStream().use { AnwesenheitslisteParser.parse(it) }
                    } catch (ex: NdsParseException) {
                        parseError = ex.message ?: "Datei konnte nicht gelesen werden"
                        call.application.log.info("NDS parse rejected: ${ex.message}")
                    }
                }
                part.dispose()
            }

            when {
                parseError != null -> call.respond(HttpStatusCode.UnprocessableEntity, parseError)
                parsed == null -> call.respond(HttpStatusCode.BadRequest, "Keine Datei hochgeladen")
                else -> call.respond(parsed)
            }
        }

        // Parse a dedicated person export (Teilnehmende .csv / Leiter .xlsx) → person list (no writes).
        // The parser is chosen by file extension.
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
                    UUID.fromString(teamRepository.create(clubId, request.createTeamName.trim(), null).id)
                }
                else -> return@post call.respond(HttpStatusCode.BadRequest, "teamId oder createTeamName erforderlich")
            }

            // The Angebot may only be linked to a single team.
            val existingForAngebot = ndsRepository.findTeamIdByAngebot(parsed.angebotId)
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
