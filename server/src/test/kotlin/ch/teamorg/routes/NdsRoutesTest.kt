package ch.teamorg.routes

import ch.teamorg.db.tables.AttendanceRecordsTable
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NdsMembersTable
import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.NdsMember
import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import ch.teamorg.infra.nds.NdsPreflightReport
import ch.teamorg.nds.NdsTestFixtures
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NdsRoutesTest : IntegrationTestBase() {

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse =
        createJsonClient().post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User $email"))
        }.body()

    private suspend fun ApplicationTestBuilder.createClub(token: String, name: String): String =
        createJsonClient().post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("$name Club"))
        }.body<Club>().id

    private suspend fun ApplicationTestBuilder.parseFile(token: String, clubId: String, bytes: ByteArray) =
        createJsonClient().post("/clubs/$clubId/nds/parse") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"liste.xlsx\"")
                    append(HttpHeaders.ContentType, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                })
            }))
        }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                out[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        return out
    }

    private suspend fun ApplicationTestBuilder.parseRoster(
        token: String,
        clubId: String,
        bytes: ByteArray,
        filename: String
    ): List<NdsMemberInput> =
        createJsonClient().post("/clubs/$clubId/nds/parse-roster") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                })
            }))
        }.body()

    private suspend fun ApplicationTestBuilder.importAll(
        token: String,
        clubId: String,
        ng: String? = "NG2"
    ): NdsImportResponse {
        val angebot = "753813-${UUID.randomUUID().toString().take(6)}"
        val parsed = parseFile(token, clubId, NdsTestFixtures.anwesenheitslisteBytes(angebot)).body<ParsedAnwesenheitsliste>()
        return createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                createTeamName = "NDS Team",
                nutzergruppe = ng,
                parsed = parsed,
                importEvents = true,
                attendanceMode = "keep"
            ))
        }.body()
    }

    @Test
    fun `parse returns metadata roster and activities`() = withTeamorgTestApplication {
        val mgr = register("nds_parse@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ParseClub")
        val parsed = parseFile(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes()).body<ParsedAnwesenheitsliste>()
        assertEquals("753813", parsed.angebotId)
        assertEquals(8, parsed.activities.size)
        assertEquals(3, parsed.members.size)
    }

    @Test
    fun `import creates team members provisional users events series and attendance`() = withTeamorgTestApplication {
        val mgr = register("nds_import@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ImportClub")
        val res = importAll(mgr.token, clubId)
        assertEquals(3, res.membersImported)
        assertEquals(8, res.eventsCreated)
        val teamId = UUID.fromString(res.teamId)

        // Events: 8, all in a series (two weekly series MO+MI), none standalone.
        val (eventCount, seriesIds, standalone) = transaction {
            val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                .where { EventTeamsTable.teamId eq teamId }
                .map { it[EventTeamsTable.eventId] }
            val rows = EventsTable.selectAll().where { EventsTable.id inList ids }.toList()
            Triple(
                rows.size,
                rows.mapNotNull { it[EventsTable.seriesId] }.toSet(),
                rows.count { it[EventsTable.seriesId] == null }
            )
        }
        assertEquals(8, eventCount)
        assertEquals(2, seriesIds.size)
        assertEquals(0, standalone)

        // Attendance: total 'J' marks (coach 2 + Lara 3 + Tim 1) = 6 present records.
        val presentCount = transaction {
            val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
            AttendanceRecordsTable.selectAll().where { AttendanceRecordsTable.eventId inList ids }.count()
        }
        assertEquals(6, presentCount)

        // Members list exposed via API; all unclaimed (provisional) initially.
        val members = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>()
        assertEquals(3, members.size)
        assertTrue(members.none { it.claimed })
    }

    @Test
    fun `re-importing the same file is idempotent`() = withTeamorgTestApplication {
        val mgr = register("nds_idem@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "IdemClub")
        val angebot = "idem-${UUID.randomUUID().toString().take(6)}"
        val bytes = NdsTestFixtures.anwesenheitslisteBytes(angebot)

        val parsed = parseFile(mgr.token, clubId, bytes).body<ParsedAnwesenheitsliste>()
        val first = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(createTeamName = "Idem", parsed = parsed, importEvents = true, attendanceMode = "keep"))
        }.body<NdsImportResponse>()
        val teamId = first.teamId

        // Re-import into the SAME team.
        val second = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(teamId = teamId, parsed = parsed, importEvents = true, attendanceMode = "keep"))
        }.body<NdsImportResponse>()

        assertEquals(3, second.membersImported)
        assertEquals(0, second.eventsCreated) // nothing new
        val memberCount = transaction {
            NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq UUID.fromString(teamId) }.count()
        }
        assertEquals(3, memberCount)
    }

    @Test
    fun `preflight blocks on missing person numbers and trainings without location`() = withTeamorgTestApplication {
        val mgr = register("nds_pf@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "PfClub")
        val res = importAll(mgr.token, clubId)
        val teamId = res.teamId

        val report = createJsonClient().get("/teams/$teamId/nds/export/preflight") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<NdsPreflightReport>()
        assertFalse(report.ok)
        assertTrue(report.issues.any { it.code == "missing_person_number" })
        assertTrue(report.issues.any { it.code == "training_missing_location" })
    }

    @Test
    fun `export produces consistent Aktivitaeten and AWK after data is complete`() = withTeamorgTestApplication {
        val mgr = register("nds_export@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ExportClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        // Assign person numbers to every member.
        val members = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>()
        members.forEachIndexed { i, m ->
            createJsonClient().patch("/teams/$teamId/nds/members/${m.id}") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
                contentType(ContentType.Application.Json)
                setBody(NdsMemberUpdateRequest(personNumber = "10000000$i"))
            }
        }
        // Fill in a location for every event (trainings need ORT).
        transaction {
            val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
            EventsTable.update({ EventsTable.id inList ids }) { it[location] = "Halle Thun" }
        }

        val report = createJsonClient().get("/teams/$teamId/nds/export/preflight") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<NdsPreflightReport>()
        assertTrue(report.ok, "preflight should pass; issues=${report.issues}")

        val zipResp = createJsonClient().get("/teams/$teamId/nds/export") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }
        assertEquals(HttpStatusCode.OK, zipResp.status)
        val files = unzip(zipResp.readRawBytes())
        val akt = files.getValue("Aktivitaeten-Import.csv").removePrefix("﻿").trim().split("\r\n")
        val awk = files.getValue("Anwesenheitskontrolle-Import.csv").removePrefix("﻿").trim().split("\r\n")

        // 8 activities + header.
        assertEquals("AKTIVITAETSTYP;DATUM;ZEIT;DAUER;ORT;FOKUS", akt[0])
        assertEquals(9, akt.size)
        assertTrue(akt.drop(1).all { it.startsWith("Training;") && it.contains(";18:00;90;Halle Thun;") })

        // 6 present records + header. Every AWK row's tail must equal an Aktivitäten row's fields.
        assertEquals("PERSONENNUMMER;FUNKTION;DATUM;AKTIVITAETSTYP;ZEIT;DAUER;ORT", awk[0])
        assertEquals(7, awk.size)
        assertTrue(awk.drop(1).all { it.contains(";Training;18:00;90;Halle Thun") })
    }

    @Test
    fun `member invite redeem claims the roster member and moves attendance`() = withTeamorgTestApplication {
        val mgr = register("nds_claim_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ClaimClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val lara = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }
        val provisionalUserId = lara.userId!!

        // Coach creates a personal invite link bound to Lara's roster row.
        val invite = createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/invite") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberInviteRequest(email = "lara_real@example.com"))
        }.body<InviteResponse>()

        // Lara registers for real and redeems.
        val laraUser = register("lara_real@example.com")
        val redeem = createJsonClient().post("/invites/${invite.token}/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${laraUser.token}")
        }
        assertEquals(HttpStatusCode.OK, redeem.status)

        // Member now claimed → backed by the real user; provisional placeholder removed.
        val claimed = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }
        assertEquals(laraUser.userId, claimed.userId.toString())
        assertTrue(claimed.claimed)

        // Lara's 3 present records moved from the provisional user to the real user.
        val realUserId = UUID.fromString(laraUser.userId)
        val (movedToReal, leftOnProvisional) = transaction {
            val real = AttendanceRecordsTable.selectAll()
                .where { AttendanceRecordsTable.userId eq realUserId }.count()
            val prov = AttendanceRecordsTable.selectAll()
                .where { AttendanceRecordsTable.userId eq provisionalUserId }.count()
            real to prov
        }
        assertEquals(3, movedToReal)
        assertEquals(0, leftOnProvisional)
    }

    @Test
    fun `club manager links an existing account to an imported player`() = withTeamorgTestApplication {
        val mgr = register("cm3@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "Link")
        val res = importAll(mgr.token, clubId)          // existing helper → team + roster + attendance
        val teamId = UUID.fromString(res.teamId)
        val lara = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }
        val realUser = register("lara.real@example.com")
        val linked = createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = realUser.userId))
        }
        assertEquals(HttpStatusCode.OK, linked.status)
        val updated = linked.body<NdsMember>()
        assertEquals(realUser.userId, updated.userId.toString())
        assertTrue(updated.claimed)
        val provisionalUserId = lara.userId!!
        val (movedToReal, leftOnProvisional) = transaction {
            val real = AttendanceRecordsTable.selectAll()
                .where { AttendanceRecordsTable.userId eq UUID.fromString(realUser.userId) }.count()
            val prov = AttendanceRecordsTable.selectAll()
                .where { AttendanceRecordsTable.userId eq provisionalUserId }.count()
            real to prov
        }
        assertEquals(3, movedToReal)
        assertEquals(0, leftOnProvisional)
    }

    @Test
    fun `link with malformed userId returns 400`() = withTeamorgTestApplication {
        val mgr = register("cm3_bad@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "LinkBad")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)
        val lara = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }
        val resp = createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = "not-a-uuid"))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `link with non-existent userId returns 404`() = withTeamorgTestApplication {
        val mgr = register("cm3_ghost@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "LinkGhost")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)
        val lara = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }
        val resp = createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = UUID.randomUUID().toString()))
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `person files supply person numbers and merge by name with the Anwesenheitsliste`() =
        withTeamorgTestApplication {
            val mgr = register("nds_persons@example.com"); promoteToSuperAdmin(mgr.userId)
            val clubId = createClub(mgr.token, "PersonsClub")

            // Step 1+2: parse the dedicated person exports (carry PERSONENNUMMER).
            val players = parseRoster(mgr.token, clubId, NdsTestFixtures.teilnehmendeCsvBytes(), "teilnehmende.csv")
            val coaches = parseRoster(mgr.token, clubId, NdsTestFixtures.leiterXlsxBytes(), "leiter.xlsx")
            assertEquals(2, players.size)
            assertEquals(1, coaches.size)

            // Step 3: Anwesenheitsliste + the persons in one import.
            val parsed = parseFile(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("persons-1"))
                .body<ParsedAnwesenheitsliste>()
            val res = createJsonClient().post("/clubs/$clubId/nds/import") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
                contentType(ContentType.Application.Json)
                setBody(NdsImportRequest(
                    createTeamName = "Persons Team",
                    nutzergruppe = "NG2",
                    parsed = parsed,
                    persons = players + coaches,
                    importEvents = true,
                    attendanceMode = "keep"
                ))
            }.body<NdsImportResponse>()
            val teamId = UUID.fromString(res.teamId)

            // No duplicate members: 3 total (1 coach + 2 players), each with a person number.
            val members = createJsonClient().get("/teams/$teamId/nds/members") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            }.body<List<NdsMember>>()
            assertEquals(3, members.size)
            assertTrue(members.all { !it.personNumber.isNullOrBlank() }, "all members carry a PERSONENNUMMER")
            // Player merged: PN from CSV, birthdate from Anwesenheitsliste.
            val lara = members.single { it.lastName == "Müller" }
            assertEquals("111111111", lara.personNumber)
            assertEquals(java.time.LocalDate.of(2008, 5, 20), lara.birthDate)

            // Coach attendance still imported (matched by name despite birthdate difference).
            val presentCount = transaction {
                val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                    .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
                AttendanceRecordsTable.selectAll().where { AttendanceRecordsTable.eventId inList ids }.count()
            }
            assertEquals(6, presentCount)

            // With person numbers present from the start, export only needs locations.
            transaction {
                val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                    .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
                EventsTable.update({ EventsTable.id inList ids }) { it[location] = "Halle" }
            }
            val report = createJsonClient().get("/teams/$teamId/nds/export/preflight") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            }.body<NdsPreflightReport>()
            assertTrue(report.ok, "no manual person-number entry needed; issues=${report.issues}")
        }
}
