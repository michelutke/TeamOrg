package ch.teamorg.routes

import ch.teamorg.db.tables.AbwesenheitRulesTable
import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.EventStatus
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NdsMembersTable
import ch.teamorg.db.tables.PresetType
import ch.teamorg.db.tables.RuleType
import ch.teamorg.db.tables.SubGroupMembersTable
import ch.teamorg.db.tables.SubGroupsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.deriveCheckInStatus
import ch.teamorg.domain.models.DuplicateSuggestion
import ch.teamorg.domain.models.NdsConflictOverride
import ch.teamorg.domain.models.NdsConflictResolution
import ch.teamorg.domain.models.NdsMapping
import ch.teamorg.domain.models.NdsMember
import ch.teamorg.domain.models.NdsMemberInput
import ch.teamorg.domain.models.NdsParseResponse
import ch.teamorg.domain.models.NdsSeries
import ch.teamorg.domain.models.NdsSeriesTime
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import ch.teamorg.infra.nds.NdsMemberMatcher
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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // Raw response — for tests asserting the status code (auth, missing files, …).
    private suspend fun ApplicationTestBuilder.parseFileRaw(
        token: String,
        clubId: String,
        bytes: ByteArray,
        teamId: String? = null
    ) =
        createJsonClient().post("/clubs/$clubId/nds/parse") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                if (teamId != null) append("teamId", teamId)
                append("anwesenheitsliste", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"liste.xlsx\"")
                    append(HttpHeaders.ContentType, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                })
            }))
        }

    // Full parse response — for tests that also need the detected series (to build seriesTimes).
    private suspend fun ApplicationTestBuilder.parseFull(token: String, clubId: String, bytes: ByteArray, teamId: String? = null): NdsParseResponse =
        parseFileRaw(token, clubId, bytes, teamId).body()

    // Convenience for the many existing tests that only care about the parsed Anwesenheitsliste.
    private suspend fun ApplicationTestBuilder.parseFile(token: String, clubId: String, bytes: ByteArray): ParsedAnwesenheitsliste =
        parseFull(token, clubId, bytes).anwesenheitsliste!!

    // Wizard-set times for every detected series — 18:00-19:30 matches the old 90min placeholder,
    // keeping pre-existing assertions (export times, event counts) unchanged.
    private fun defaultSeriesTimes(series: List<NdsSeries>): List<NdsSeriesTime> =
        series.map { NdsSeriesTime(it.seriesKey, "18:00", "19:30") }

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
        val parseResponse = parseFull(token, clubId, NdsTestFixtures.anwesenheitslisteBytes(angebot))
        return createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                createTeamName = "NDS Team",
                nutzergruppe = ng,
                parsed = parseResponse.anwesenheitsliste,
                importEvents = true,
                attendanceMode = "keep",
                seriesTimes = defaultSeriesTimes(parseResponse.series)
            ))
        }.body()
    }

    @Test
    fun `parse returns metadata roster and activities`() = withTeamorgTestApplication {
        val mgr = register("nds_parse@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ParseClub")
        val parsed = parseFile(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes())
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

        // Attendance: total 'J' marks (coach 2 + Lara 3 + Tim 1) = 6 confirmed responses.
        // Non-attended dates only get a 'declined' write once the event is past — all fixture
        // events are in the future (Aug 2026), so no declined responses are written at all here.
        val (confirmedCount, declinedCount, openCount) = transaction {
            val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
            val confirmed = AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.eventId inList ids) and (AttendanceResponsesTable.status eq "confirmed") }
                .count()
            val declined = AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.eventId inList ids) and (AttendanceResponsesTable.status eq "declined") }
                .count()
            // All fixture events are in the future (Aug 2026) → none auto-finalized.
            val open = EventsTable.selectAll()
                .where { (EventsTable.id inList ids) and EventsTable.checkInCompletedAt.isNull() }
                .count()
            Triple(confirmed, declined, open)
        }
        assertEquals(6, confirmedCount)
        assertEquals(6, res.attendanceImported)
        assertEquals(0, declinedCount) // future events are never pre-declined
        assertEquals(8, openCount) // future events stay open

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

        val parseResponse = parseFull(mgr.token, clubId, bytes)
        val parsed = parseResponse.anwesenheitsliste!!
        val seriesTimes = defaultSeriesTimes(parseResponse.series)
        val first = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(createTeamName = "Idem", parsed = parsed, importEvents = true, attendanceMode = "keep", seriesTimes = seriesTimes))
        }.body<NdsImportResponse>()
        val teamId = first.teamId

        // Re-import into the SAME team.
        val second = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(teamId = teamId, parsed = parsed, importEvents = true, attendanceMode = "keep", seriesTimes = seriesTimes))
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
        val eventIds = transaction {
            val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
            EventsTable.update({ EventsTable.id inList ids }) { it[location] = "Halle Thun" }
            ids
        }

        // The NDS import already wrote confirmed attendance_responses for every attended (event, user)
        // pair (6 'J' marks). Export reads those confirmed responses directly — no extra seeding.
        val confirmedForExport = transaction {
            AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.eventId inList eventIds) and (AttendanceResponsesTable.status eq "confirmed") }
                .count()
        }
        assertEquals(6, confirmedForExport)

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

        // 6 confirmed responses + header. Every AWK row's tail must match an Aktivitäten row.
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

        // Lara's 3 confirmed responses (attended dates) moved from the provisional user to the real
        // user; nothing left on the provisional. (Records no longer exist — responses are the model.)
        val realUserId = UUID.fromString(laraUser.userId)
        val (movedToReal, leftOnProvisional) = transaction {
            val real = AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.userId eq realUserId) and (AttendanceResponsesTable.status eq "confirmed") }
                .count()
            val prov = AttendanceResponsesTable.selectAll()
                .where { AttendanceResponsesTable.userId eq provisionalUserId }.count()
            real to prov
        }
        assertEquals(3, movedToReal)
        assertEquals(0, leftOnProvisional)

        // Real user holds the team role after claiming.
        val role = transaction {
            ch.teamorg.db.tables.TeamRolesTable.selectAll()
                .where { (ch.teamorg.db.tables.TeamRolesTable.userId eq realUserId) and (ch.teamorg.db.tables.TeamRolesTable.teamId eq teamId) }
                .map { it[ch.teamorg.db.tables.TeamRolesTable.role] }.singleOrNull()
        }
        assertEquals("player", role)
    }

    @Test
    fun `large realistic import writes the expected confirmed response total`() = withTeamorgTestApplication {
        val mgr = register("nds_large@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "LargeClub")
        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.largeAnwesenheitslisteBytes("large-1"))
        val parsed = parseResponse.anwesenheitsliste!!
        val expected = parsed.members.sumOf { it.attendedDates.size }
        assertTrue(expected > 20, "fixture should have many marks; got $expected")
        val res = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                createTeamName = "Large", nutzergruppe = "NG2", parsed = parsed, importEvents = true, attendanceMode = "keep",
                seriesTimes = defaultSeriesTimes(parseResponse.series)
            ))
        }.body<NdsImportResponse>()
        // attendanceImported counts confirmed responses only (one per attended date).
        assertEquals(expected, res.attendanceImported)
        val teamId = UUID.fromString(res.teamId)
        val confirmedCount = transaction {
            val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
            AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.eventId inList ids) and (AttendanceResponsesTable.status eq "confirmed") }
                .count()
        }
        assertEquals(expected.toLong(), confirmedCount)
    }

    @Test
    fun `auto-finalize marks past imported events done and leaves future events open`() = withTeamorgTestApplication {
        val mgr = register("nds_finalize@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "FinalizeClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        // All fixture events are in the future → the import left them all open.
        val eventIds = transaction {
            EventTeamsTable.select(EventTeamsTable.eventId)
                .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
        }
        val openAfterImport = transaction {
            EventsTable.selectAll()
                .where { (EventsTable.id inList eventIds) and EventsTable.checkInCompletedAt.isNull() }
                .count()
        }
        assertEquals(8, openAfterImport)

        // Backdate ONE event into the past, then re-import (idempotent: no new events, but the
        // auto-finalize pass runs over all past NDS events of the team).
        val pastEventId = eventIds.first()
        transaction {
            EventsTable.update({ EventsTable.id eq pastEventId }) {
                it[startAt] = java.time.Instant.now().minusSeconds(86_400)
                it[endAt] = java.time.Instant.now().minusSeconds(82_800)
            }
        }
        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes())
        val parsed = parseResponse.anwesenheitsliste!!
        createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = res.teamId, parsed = parsed, importEvents = true, attendanceMode = "keep",
                seriesTimes = defaultSeriesTimes(parseResponse.series)
            ))
        }

        val now = java.time.Instant.now()
        transaction {
            val rows = EventsTable.selectAll().where { EventsTable.id inList eventIds }.toList()
            val past = rows.single { it[EventsTable.id] == pastEventId }
            val pastStatus = deriveCheckInStatus(
                now,
                past[EventsTable.responseDeadline] ?: past[EventsTable.startAt],
                past[EventsTable.endAt],
                past[EventsTable.checkInCompletedAt]
            )
            assertEquals("done", pastStatus) // past imported event auto-finalized
            // Every other (future) event stays open.
            val futures = rows.filter { it[EventsTable.id] != pastEventId }
            assertTrue(futures.all { it[EventsTable.checkInCompletedAt] == null })
            assertTrue(futures.all {
                deriveCheckInStatus(
                    now,
                    it[EventsTable.responseDeadline] ?: it[EventsTable.startAt],
                    it[EventsTable.endAt],
                    it[EventsTable.checkInCompletedAt]
                ) == "open"
            })
        }
    }

    @Test
    fun `attendanceMode keep only pre-declines past events, never future ones`() = withTeamorgTestApplication {
        val mgr = register("nds_keep_future@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "KeepFutureClub")
        val teamId = createTeam(mgr.token, clubId, "KeepFuture Team")

        val bytes = NdsTestFixtures.anwesenheitslisteBytes("nds-i4-1")
        val parseResponse = parseFull(mgr.token, clubId, bytes, teamId)
        val parsed = parseResponse.anwesenheitsliste!!

        createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId, parsed = parsed, importEvents = true, attendanceMode = "keep",
                seriesTimes = defaultSeriesTimes(parseResponse.series)
            ))
        }

        val eventIds = transaction {
            EventTeamsTable.select(EventTeamsTable.eventId).where { EventTeamsTable.teamId eq UUID.fromString(teamId) }.map { it[EventTeamsTable.eventId] }
        }
        assertEquals(8, eventIds.size)

        // No non-attended date gets pre-declined while every fixture event is still in the future.
        val declinedAfterFirstImport = transaction {
            AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.eventId inList eventIds) and (AttendanceResponsesTable.status eq "declined") }
                .count()
        }
        assertEquals(0, declinedAfterFirstImport)

        // Backdate the ONE event nobody attends (MONDAYS[2], see NdsTestFixtures — coach attends
        // index 0/2, Lara attends 0/1/2, Tim attends 1; index 4 = MONDAYS[2] is attended by none),
        // so the first import wrote no attendance row at all for it, and its declined-write below
        // is a clean 3-for-3 rather than colliding with a pre-existing confirmed row via insertIgnore.
        val pastEventId = transaction {
            (EventsTable innerJoin EventTeamsTable).select(EventsTable.id)
                .where {
                    (EventTeamsTable.teamId eq UUID.fromString(teamId)) and
                        (EventsTable.startAt eq NdsTestFixtures.MONDAYS[2].atTime(18, 0).toInstant(java.time.ZoneOffset.UTC))
                }
                .single()[EventsTable.id]
        }
        transaction {
            EventsTable.update({ EventsTable.id eq pastEventId }) {
                it[startAt] = java.time.Instant.now().minusSeconds(86_400)
                it[endAt] = java.time.Instant.now().minusSeconds(82_800)
            }
        }
        createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId, parsed = parsed, importEvents = true, attendanceMode = "keep",
                seriesTimes = defaultSeriesTimes(parseResponse.series)
            ))
        }

        val (declinedOnPastEvent, declinedOnFutureEvents) = transaction {
            val past = AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.eventId eq pastEventId) and (AttendanceResponsesTable.status eq "declined") }
                .count()
            val future = AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.eventId inList (eventIds - pastEventId)) and (AttendanceResponsesTable.status eq "declined") }
                .count()
            past to future
        }
        assertEquals(3, declinedOnPastEvent, "all 3 matched members get declined once the event is past")
        assertEquals(0, declinedOnFutureEvents, "future events are never pre-declined")
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
            val real = AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.userId eq UUID.fromString(realUser.userId)) and (AttendanceResponsesTable.status eq "confirmed") }
                .count()
            val prov = AttendanceResponsesTable.selectAll()
                .where { AttendanceResponsesTable.userId eq provisionalUserId }.count()
            real to prov
        }
        assertEquals(3, movedToReal)
        assertEquals(0, leftOnProvisional)

        // Linked real user holds the team role after linking.
        val role = transaction {
            ch.teamorg.db.tables.TeamRolesTable.selectAll()
                .where { (ch.teamorg.db.tables.TeamRolesTable.userId eq UUID.fromString(realUser.userId)) and (ch.teamorg.db.tables.TeamRolesTable.teamId eq teamId) }
                .map { it[ch.teamorg.db.tables.TeamRolesTable.role] }.singleOrNull()
        }
        assertEquals("player", role)
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
    fun `link rejects a user already linked to another roster row of the team`() = withTeamorgTestApplication {
        val mgr = register("nds_dup_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "DupClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val members = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>()
        val first = members.first { it.funktion == "Teilnehmer/in" }
        val second = members.filter { it.funktion == "Teilnehmer/in" }[1]

        val user = register("dup_target@example.com")
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = user.userId, role = "player"))
        }

        suspend fun link(memberId: UUID) = createJsonClient().post("/teams/$teamId/nds/members/$memberId/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = user.userId))
        }

        assertEquals(HttpStatusCode.OK, link(first.id).status)
        assertEquals(HttpStatusCode.Conflict, link(second.id).status)
    }

    @Test
    fun `link rejects a provisional target`() = withTeamorgTestApplication {
        val mgr = register("nds_guard_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "GuardClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val members = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>()
        val target = members.first { it.lastName == "Müller" }
        val otherPlaceholderUserId = members.first { it.id != target.id }.userId!!

        // Provisional placeholder as the link target -> 400.
        val provisional = createJsonClient().post("/teams/$teamId/nds/members/${target.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = otherPlaceholderUserId.toString()))
        }
        assertEquals(HttpStatusCode.BadRequest, provisional.status)
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
            val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("persons-1"))
            val parsed = parseResponse.anwesenheitsliste!!
            val res = createJsonClient().post("/clubs/$clubId/nds/import") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
                contentType(ContentType.Application.Json)
                setBody(NdsImportRequest(
                    createTeamName = "Persons Team",
                    nutzergruppe = "NG2",
                    parsed = parsed,
                    persons = players + coaches,
                    importEvents = true,
                    attendanceMode = "keep",
                    seriesTimes = defaultSeriesTimes(parseResponse.series)
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

            // Coach attendance still imported (matched by name despite birthdate difference):
            // 6 confirmed responses across all matched members.
            val confirmedCount = transaction {
                val ids = EventTeamsTable.select(EventTeamsTable.eventId)
                    .where { EventTeamsTable.teamId eq teamId }.map { it[EventTeamsTable.eventId] }
                AttendanceResponsesTable.selectAll()
                    .where { (AttendanceResponsesTable.eventId inList ids) and (AttendanceResponsesTable.status eq "confirmed") }
                    .count()
            }
            assertEquals(6, confirmedCount)

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

    @Test
    fun `re-import with createTeamName is rejected without creating an orphan team`() =
        withTeamorgTestApplication {
            val mgr = register("nds_reimport_conflict@example.com"); promoteToSuperAdmin(mgr.userId)
            val clubId = createClub(mgr.token, "ReimportConflictClub")
            val angebot = "753813-conflict"
            val bytes = NdsTestFixtures.anwesenheitslisteBytes(angebot)
            val parsed = parseFile(mgr.token, clubId, bytes)
            val first = createJsonClient().post("/clubs/$clubId/nds/import") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
                contentType(ContentType.Application.Json)
                setBody(NdsImportRequest(createTeamName = "NDS Team", parsed = parsed, importEvents = false))
            }
            assertEquals(HttpStatusCode.OK, first.status)

            val teamsBefore = transaction {
                TeamsTable.selectAll().where { TeamsTable.clubId eq UUID.fromString(clubId) }.count()
            }
            // Regression: the conflict used to be checked AFTER team creation, leaving an
            // orphan empty team behind for every rejected re-import attempt.
            val second = createJsonClient().post("/clubs/$clubId/nds/import") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
                contentType(ContentType.Application.Json)
                setBody(NdsImportRequest(createTeamName = "NDS Team again", parsed = parsed, importEvents = false))
            }
            assertEquals(HttpStatusCode.Conflict, second.status)
            val teamsAfter = transaction {
                TeamsTable.selectAll().where { TeamsTable.clubId eq UUID.fromString(clubId) }.count()
            }
            assertEquals(teamsBefore, teamsAfter, "rejected re-import must not create a team")
        }

    @Test
    fun `parse exposes the linked team and re-import into it succeeds`() =
        withTeamorgTestApplication {
            val mgr = register("nds_reimport_ok@example.com"); promoteToSuperAdmin(mgr.userId)
            val clubId = createClub(mgr.token, "ReimportOkClub")
            val angebot = "753813-relink"
            val bytes = NdsTestFixtures.anwesenheitslisteBytes(angebot)
            val parseResponse = parseFileRaw(mgr.token, clubId, bytes).body<NdsParseResponse>()
            assertEquals(null, parseResponse.linkedTeamId, "unlinked Angebot must not report a linked team")
            val parsed = parseResponse.anwesenheitsliste!!
            val first: NdsImportResponse = createJsonClient().post("/clubs/$clubId/nds/import") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
                contentType(ContentType.Application.Json)
                setBody(NdsImportRequest(
                    createTeamName = "NDS Team", parsed = parsed, importEvents = true, attendanceMode = "keep",
                    seriesTimes = defaultSeriesTimes(parseResponse.series)
                ))
            }.body()

            // Second parse now reports the linked team (same club) …
            val reparseResponse = parseFileRaw(mgr.token, clubId, bytes).body<NdsParseResponse>()
            assertEquals(first.teamId, reparseResponse.linkedTeamId)
            assertEquals("NDS Team", reparseResponse.linkedTeamName)
            val reparsed = reparseResponse.anwesenheitsliste!!

            // … and importing with that teamId succeeds (the re-import path).
            val second = createJsonClient().post("/clubs/$clubId/nds/import") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
                contentType(ContentType.Application.Json)
                setBody(NdsImportRequest(
                    teamId = reparseResponse.linkedTeamId, parsed = reparsed, importEvents = true, attendanceMode = "keep",
                    seriesTimes = defaultSeriesTimes(reparseResponse.series)
                ))
            }
            assertEquals(HttpStatusCode.OK, second.status)
            val res: NdsImportResponse = second.body()
            assertEquals(first.teamId, res.teamId)
        }

    @Test
    fun `two clubs can independently import the same NDS Angebot without a 500`() = withTeamorgTestApplication {
        val mgrA = register("nds_crossclub_angebot_a@example.com"); promoteToSuperAdmin(mgrA.userId)
        val clubA = createClub(mgrA.token, "CrossClubAngebotA")
        val mgrB = register("nds_crossclub_angebot_b@example.com"); promoteToSuperAdmin(mgrB.userId)
        val clubB = createClub(mgrB.token, "CrossClubAngebotB")

        val angebot = "shared-angebot-${UUID.randomUUID().toString().take(6)}"
        val bytes = NdsTestFixtures.anwesenheitslisteBytes(angebot)

        val parsedA = parseFile(mgrA.token, clubA, bytes)
        val respA = createJsonClient().post("/clubs/$clubA/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgrA.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(createTeamName = "Team A", parsed = parsedA, importEvents = false))
        }
        assertEquals(HttpStatusCode.OK, respA.status)

        val parsedB = parseFile(mgrB.token, clubB, bytes)
        val respB = createJsonClient().post("/clubs/$clubB/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgrB.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(createTeamName = "Team B", parsed = parsedB, importEvents = false))
        }
        assertEquals(HttpStatusCode.OK, respB.status, "club B must be able to link the same Angebot independently")

        val teamAId = respA.body<NdsImportResponse>().teamId
        val teamBId = respB.body<NdsImportResponse>().teamId
        assertTrue(teamAId != teamBId)

        // Both clubs keep their own link — re-parsing within each club reports its own team as linked.
        val reparseA = parseFileRaw(mgrA.token, clubA, bytes).body<NdsParseResponse>()
        assertEquals(teamAId, reparseA.linkedTeamId)
        val reparseB = parseFileRaw(mgrB.token, clubB, bytes).body<NdsParseResponse>()
        assertEquals(teamBId, reparseB.linkedTeamId)
    }

    private suspend fun ApplicationTestBuilder.createTeam(token: String, clubId: String, name: String): String =
        createJsonClient().post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name))
        }.body<ch.teamorg.domain.models.Team>().id

    private suspend fun ApplicationTestBuilder.addTeamMember(mgrToken: String, teamId: String, userId: String, role: String) {
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer $mgrToken")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId, role))
        }
    }

    @Test
    fun `parse with only a teilnehmende CSV returns persons and no series`() = withTeamorgTestApplication {
        val mgr = register("nds_parse_persons_only@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "PersonsOnlyClub")

        val response = createJsonClient().post("/clubs/$clubId/nds/parse") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            setBody(MultiPartFormDataContent(formData {
                append("teilnehmende", NdsTestFixtures.teilnehmendeCsvBytes(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"teilnehmende.csv\"")
                })
            }))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<NdsParseResponse>()
        assertEquals(null, body.anwesenheitsliste)
        assertEquals(2, body.persons.size)
        assertTrue(body.series.isEmpty())
    }

    @Test
    fun `parse with no files returns 400`() = withTeamorgTestApplication {
        val mgr = register("nds_parse_nofile@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "NoFileClub")

        val response = createJsonClient().post("/clubs/$clubId/nds/parse") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            setBody(MultiPartFormDataContent(formData {}))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `coach of the target team can parse with teamId`() = withTeamorgTestApplication {
        val mgr = register("nds_parse_coach_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "CoachParseClub")
        val teamId = createTeam(mgr.token, clubId, "Coach Team")
        val coach = register("nds_parse_coach@example.com")
        addTeamMember(mgr.token, teamId, coach.userId, "coach")

        val response = parseFileRaw(coach.token, clubId, NdsTestFixtures.anwesenheitslisteBytes(), teamId = teamId)
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `coach of a different team is forbidden from parsing with that teamId`() = withTeamorgTestApplication {
        val mgr = register("nds_parse_other_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "OtherTeamClub")
        val teamA = createTeam(mgr.token, clubId, "Team A")
        val teamB = createTeam(mgr.token, clubId, "Team B")
        val coach = register("nds_parse_other_coach@example.com")
        addTeamMember(mgr.token, teamA, coach.userId, "coach")

        val response = parseFileRaw(coach.token, clubId, NdsTestFixtures.anwesenheitslisteBytes(), teamId = teamB)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `cross-club teamId is forbidden even for a club manager`() = withTeamorgTestApplication {
        val mgrA = register("nds_parse_crossclub_a@example.com"); promoteToSuperAdmin(mgrA.userId)
        val clubA = createClub(mgrA.token, "CrossClubA")
        val mgrB = register("nds_parse_crossclub_b@example.com"); promoteToSuperAdmin(mgrB.userId)
        val clubB = createClub(mgrB.token, "CrossClubB")
        val teamB = createTeam(mgrB.token, clubB, "Team In B")

        // mgrA is club_manager of clubA only; teamB belongs to clubB → cross-club, must be forbidden
        // even though the path clubId is clubA and mgrA has no role on teamB at all.
        val response = parseFileRaw(mgrA.token, clubA, NdsTestFixtures.anwesenheitslisteBytes(), teamId = teamB)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `parse with teamId returns a preselected suggestion for an exact-name roster member`() = withTeamorgTestApplication {
        val mgr = register("nds_parse_suggest_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "SuggestClub")
        val teamId = createTeam(mgr.token, clubId, "Suggest Team")

        // Register a real user whose display name exactly matches the fixture coach ("Anna Trainer")
        // and add them to the team, so the matcher should surface a unique HIGH/preselected candidate.
        val anna = createJsonClient().post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("anna_trainer@example.com", "password123", "Anna Trainer"))
        }.body<AuthResponse>()
        addTeamMember(mgr.token, teamId, anna.userId, "coach")

        val response = parseFileRaw(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes(), teamId = teamId)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<NdsParseResponse>()

        val leiterRowKey = body.memberSuggestions.map { it.rowKey }.first { it.startsWith("L:") }
        val suggestion = body.memberSuggestions.single { it.rowKey == leiterRowKey }
        assertEquals(anna.userId, suggestion.preselectedUserId)
    }

    // ---- Task 3: import v2 — mappings, wizard times, conflict resolution ----

    private fun rowKeyFor(parsed: ParsedAnwesenheitsliste, lastName: String): String {
        val m = parsed.members.single { it.lastName == lastName }
        return NdsMemberMatcher.rowKey(m.funktion, m.lastName, m.firstName)
    }

    private suspend fun ApplicationTestBuilder.makeClubMember(mgrToken: String, clubId: String, userId: String): String {
        val holdingTeam = createTeam(mgrToken, clubId, "Holding Team")
        addTeamMember(mgrToken, holdingTeam, userId, "player")
        return holdingTeam
    }

    @Test
    fun `map action links user adds role overwrites NDS fields and leaves profile untouched`() = withTeamorgTestApplication {
        val mgr = register("nds_map1@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "MapClub")
        val teamId = createTeam(mgr.token, clubId, "Map Team")

        val real = register("map_real@example.com")
        makeClubMember(mgr.token, clubId, real.userId)

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-0"))
        val parsed = parseResponse.anwesenheitsliste!!
        val rowKey = rowKeyFor(parsed, "Trainer")

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                persons = listOf(NdsMemberInput("Trainer", "Anna", null, "999999999", "Leiter/in")),
                importEvents = true,
                attendanceMode = "discard",
                mappings = listOf(NdsMapping(rowKey = rowKey, action = "map", userId = real.userId)),
                seriesTimes = defaultSeriesTimes(parseResponse.series)
            ))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val members = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>()
        val trainer = members.single { it.lastName == "Trainer" }
        assertEquals(real.userId, trainer.userId.toString())
        assertTrue(trainer.claimed)
        assertEquals("999999999", trainer.personNumber) // overwritten from the mapped row

        val role = transaction {
            TeamRolesTable.selectAll()
                .where { (TeamRolesTable.userId eq UUID.fromString(real.userId)) and (TeamRolesTable.teamId eq UUID.fromString(teamId)) }
                .map { it[TeamRolesTable.role] }.singleOrNull()
        }
        assertEquals("coach", role) // funktion "Leiter/in" → coach

        val displayNameAfter = transaction {
            UsersTable.select(UsersTable.displayName).where { UsersTable.id eq UUID.fromString(real.userId) }
                .single()[UsersTable.displayName]
        }
        assertEquals("User map_real@example.com", displayNameAfter) // profile untouched
    }

    @Test
    fun `map to a user already on the team keeps the existing role row`() = withTeamorgTestApplication {
        val mgr = register("nds_map2@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "MapClub2")
        val teamId = createTeam(mgr.token, clubId, "Map Team 2")

        val real = register("map_real2@example.com")
        addTeamMember(mgr.token, teamId, real.userId, "coach") // already a coach on THIS team

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-1"))
        val parsed = parseResponse.anwesenheitsliste!!
        val rowKey = rowKeyFor(parsed, "Müller") // Teilnehmer/in → would default to "player"

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                importEvents = false,
                mappings = listOf(NdsMapping(rowKey = rowKey, action = "map", userId = real.userId))
            ))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val roles = transaction {
            TeamRolesTable.selectAll()
                .where { (TeamRolesTable.userId eq UUID.fromString(real.userId)) and (TeamRolesTable.teamId eq UUID.fromString(teamId)) }
                .map { it[TeamRolesTable.role] }
        }
        assertEquals(listOf("coach"), roles) // no additional "player" role added
    }

    @Test
    fun `skip writes nothing for that row`() = withTeamorgTestApplication {
        val mgr = register("nds_skip@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "SkipClub")
        val teamId = createTeam(mgr.token, clubId, "Skip Team")

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-2"))
        val parsed = parseResponse.anwesenheitsliste!!
        val rowKey = rowKeyFor(parsed, "Meier")

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                importEvents = false,
                mappings = listOf(NdsMapping(rowKey = rowKey, action = "skip"))
            ))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val skippedRowExists = transaction {
            !NdsMembersTable.selectAll()
                .where { (NdsMembersTable.teamId eq UUID.fromString(teamId)) and (NdsMembersTable.lastName eq "Meier") }
                .empty()
        }
        assertFalse(skippedRowExists)
        // The other two rows (not skipped) were still created.
        val memberCount = transaction {
            NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq UUID.fromString(teamId) }.count()
        }
        assertEquals(2, memberCount)
    }

    @Test
    fun `two mappings to the same userId is rejected`() = withTeamorgTestApplication {
        val mgr = register("nds_dupmap@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "DupMapClub")
        val teamId = createTeam(mgr.token, clubId, "DupMap Team")

        val real = register("dupmap_real@example.com")
        makeClubMember(mgr.token, clubId, real.userId)

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-3"))
        val parsed = parseResponse.anwesenheitsliste!!

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                importEvents = false,
                mappings = listOf(
                    NdsMapping(rowKey = rowKeyFor(parsed, "Trainer"), action = "map", userId = real.userId),
                    NdsMapping(rowKey = rowKeyFor(parsed, "Müller"), action = "map", userId = real.userId)
                )
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `createTeamName with a failing validation creates no orphan team`() = withTeamorgTestApplication {
        val mgr = register("nds_orphan@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "OrphanClub")

        val real = register("orphan_real@example.com")
        makeClubMember(mgr.token, clubId, real.userId)

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-orphan"))
        val parsed = parseResponse.anwesenheitsliste!!

        val teamsBefore = transaction {
            TeamsTable.selectAll().where { TeamsTable.clubId eq UUID.fromString(clubId) }.count()
        }

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                createTeamName = "Orphan Team",
                parsed = parsed,
                importEvents = false,
                // Double-map — the same validation failure as the existing-team test above, but here
                // there is no teamId yet: the team must never get created before this check fails.
                mappings = listOf(
                    NdsMapping(rowKey = rowKeyFor(parsed, "Trainer"), action = "map", userId = real.userId),
                    NdsMapping(rowKey = rowKeyFor(parsed, "Müller"), action = "map", userId = real.userId)
                )
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)

        val teamsAfter = transaction {
            TeamsTable.selectAll().where { TeamsTable.clubId eq UUID.fromString(clubId) }.count()
        }
        assertEquals(teamsBefore, teamsAfter, "a failed validation must not create an orphan team")
    }

    @Test
    fun `mapping to a user outside the club is rejected`() = withTeamorgTestApplication {
        val mgr = register("nds_foreign@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ForeignClub")
        val teamId = createTeam(mgr.token, clubId, "Foreign Team")

        val outsider = register("foreign_outsider@example.com") // no club/team relation at all

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-4"))
        val parsed = parseResponse.anwesenheitsliste!!

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                importEvents = false,
                mappings = listOf(NdsMapping(rowKey = rowKeyFor(parsed, "Trainer"), action = "map", userId = outsider.userId))
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `missing seriesTime for an importing series is rejected`() = withTeamorgTestApplication {
        val mgr = register("nds_notime@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "NoTimeClub")
        val teamId = createTeam(mgr.token, clubId, "NoTime Team")

        val parsed = parseFile(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-5"))

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(teamId = teamId, parsed = parsed, importEvents = true, attendanceMode = "discard"))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `persons-only import creates members and zero events`() = withTeamorgTestApplication {
        val mgr = register("nds_personsonly@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "PersonsOnlyImportClub")
        val teamId = createTeam(mgr.token, clubId, "PersonsOnly Team")

        val res = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = null,
                persons = listOf(
                    NdsMemberInput("Trainer", "Anna", null, "111", "Leiter/in"),
                    NdsMemberInput("Müller", "Lara", null, "222", "Teilnehmer/in")
                ),
                importEvents = false
            ))
        }.body<NdsImportResponse>()
        assertEquals(2, res.membersImported)
        assertEquals(0, res.eventsCreated)
    }

    // Pre-creates a conflicting TeamOrg training event on the given Monday activity date (series
    // "0-T-90") and returns its id.
    private fun createConflictingEvent(
        teamId: String,
        date: java.time.LocalDate,
        createdBy: String,
        title: String = "Bestehendes Training"
    ): UUID = transaction {
        val eventId = EventsTable.insert {
            it[EventsTable.title] = title
            it[EventsTable.type] = ch.teamorg.db.tables.EventType.training
            it[EventsTable.startAt] = date.atTime(19, 0).toInstant(java.time.ZoneOffset.UTC)
            it[EventsTable.endAt] = date.atTime(20, 0).toInstant(java.time.ZoneOffset.UTC)
            it[EventsTable.createdBy] = UUID.fromString(createdBy)
        } get EventsTable.id
        EventTeamsTable.insert {
            it[EventTeamsTable.eventId] = eventId
            it[EventTeamsTable.teamId] = UUID.fromString(teamId)
        }
        eventId
    }

    @Test
    fun `conflict keep-teamorg skips the new event and attaches attendance to the existing one`() = withTeamorgTestApplication {
        val mgr = register("nds_conflict_keep@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ConflictKeepClub")
        val teamId = createTeam(mgr.token, clubId, "ConflictKeep Team")

        val real = register("conflictkeep_real@example.com")
        makeClubMember(mgr.token, clubId, real.userId)

        val existingEventId = createConflictingEvent(teamId, NdsTestFixtures.MONDAYS[0], mgr.userId)
        // A manual RSVP that must survive the import untouched (insertIgnore semantics).
        transaction {
            AttendanceResponsesTable.insert {
                it[AttendanceResponsesTable.eventId] = existingEventId
                it[AttendanceResponsesTable.userId] = UUID.fromString(real.userId)
                it[AttendanceResponsesTable.status] = "declined"
                it[AttendanceResponsesTable.unexcused] = true
                it[AttendanceResponsesTable.manualOverride] = true
            }
        }

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-6"), teamId)
        val parsed = parseResponse.anwesenheitsliste!!
        val conflictGroup = parseResponse.conflicts.single { it.dates.any { d -> d.date == NdsTestFixtures.MONDAYS[0] } }
        assertEquals(existingEventId.toString(), conflictGroup.dates.single { it.date == NdsTestFixtures.MONDAYS[0] }.existingEventId)

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                importEvents = true,
                attendanceMode = "keep",
                mappings = listOf(NdsMapping(rowKey = rowKeyFor(parsed, "Trainer"), action = "map", userId = real.userId)),
                seriesTimes = defaultSeriesTimes(parseResponse.series),
                conflictResolutions = listOf(NdsConflictResolution(seriesKey = conflictGroup.seriesKey, keep = "teamorg"))
            ))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val res = resp.body<NdsImportResponse>()
        assertEquals(7, res.eventsCreated) // 8 activities minus the 1 kept-TeamOrg date

        // No new NDS event on the conflict date; the existing event is still active.
        val newEventOnConflictDate = transaction {
            (EventsTable innerJoin EventTeamsTable).selectAll()
                .where {
                    (EventTeamsTable.teamId eq UUID.fromString(teamId)) and
                        (EventsTable.externalSource eq "nds") and
                        (EventsTable.startAt eq NdsTestFixtures.MONDAYS[0].atTime(18, 0).toInstant(java.time.ZoneOffset.UTC))
                }
                .count()
        }
        assertEquals(0, newEventOnConflictDate)
        val existingStatus = transaction {
            EventsTable.selectAll().where { EventsTable.id eq existingEventId }.single()[EventsTable.status]
        }
        assertEquals(EventStatus.active, existingStatus)

        // The pre-existing manual RSVP is untouched despite Anna's J-mark on this date.
        val survivingStatus = transaction {
            AttendanceResponsesTable.selectAll()
                .where { (AttendanceResponsesTable.eventId eq existingEventId) and (AttendanceResponsesTable.userId eq UUID.fromString(real.userId)) }
                .single()[AttendanceResponsesTable.status]
        }
        assertEquals("declined", survivingStatus)
    }

    @Test
    fun `conflict keep-nds cancels the existing event and creates the new one with wizard time`() = withTeamorgTestApplication {
        val mgr = register("nds_conflict_nds@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ConflictNdsClub")
        val teamId = createTeam(mgr.token, clubId, "ConflictNds Team")

        val existingEventId = createConflictingEvent(teamId, NdsTestFixtures.MONDAYS[0], mgr.userId)
        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-7"), teamId)
        val parsed = parseResponse.anwesenheitsliste!!
        val conflictGroup = parseResponse.conflicts.single { it.dates.any { d -> d.date == NdsTestFixtures.MONDAYS[0] } }

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                importEvents = true,
                attendanceMode = "discard",
                seriesTimes = defaultSeriesTimes(parseResponse.series),
                conflictResolutions = listOf(NdsConflictResolution(seriesKey = conflictGroup.seriesKey, keep = "nds"))
            ))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val res = resp.body<NdsImportResponse>()
        assertEquals(8, res.eventsCreated) // all 8, including the conflict date

        val existingStatus = transaction {
            EventsTable.selectAll().where { EventsTable.id eq existingEventId }.single()[EventsTable.status]
        }
        assertEquals(EventStatus.cancelled, existingStatus)

        val newEvent = transaction {
            (EventsTable innerJoin EventTeamsTable).selectAll()
                .where {
                    (EventTeamsTable.teamId eq UUID.fromString(teamId)) and
                        (EventsTable.externalSource eq "nds") and
                        (EventsTable.startAt eq NdsTestFixtures.MONDAYS[0].atTime(18, 0).toInstant(java.time.ZoneOffset.UTC))
                }
                .toList()
        }
        assertEquals(1, newEvent.size)
    }

    @Test
    fun `a per-date override wins over the group keep decision`() = withTeamorgTestApplication {
        val mgr = register("nds_override@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "OverrideClub")
        val teamId = createTeam(mgr.token, clubId, "Override Team")

        // Two conflicting existing events on the SAME series (both Mondays).
        createConflictingEvent(teamId, NdsTestFixtures.MONDAYS[0], mgr.userId, "Bestehendes 1")
        createConflictingEvent(teamId, NdsTestFixtures.MONDAYS[1], mgr.userId, "Bestehendes 2")

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-8"), teamId)
        val parsed = parseResponse.anwesenheitsliste!!
        val conflictGroup = parseResponse.conflicts.single { it.seriesKey.startsWith("0-T-") }
        assertEquals(2, conflictGroup.dates.size)

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                importEvents = true,
                attendanceMode = "discard",
                seriesTimes = defaultSeriesTimes(parseResponse.series),
                conflictResolutions = listOf(
                    NdsConflictResolution(
                        seriesKey = conflictGroup.seriesKey,
                        keep = "teamorg",
                        overrides = listOf(NdsConflictOverride(date = NdsTestFixtures.MONDAYS[0], keep = "nds"))
                    )
                )
            ))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val res = resp.body<NdsImportResponse>()
        // 8 total - 1 (MONDAYS[1] kept TeamOrg) = 7.
        assertEquals(7, res.eventsCreated)

        val newEventOnOverriddenDate = transaction {
            (EventsTable innerJoin EventTeamsTable).selectAll()
                .where {
                    (EventTeamsTable.teamId eq UUID.fromString(teamId)) and
                        (EventsTable.externalSource eq "nds") and
                        (EventsTable.startAt eq NdsTestFixtures.MONDAYS[0].atTime(18, 0).toInstant(java.time.ZoneOffset.UTC))
                }
                .count()
        }
        assertEquals(1, newEventOnOverriddenDate) // override → nds created here
        val newEventOnGroupDate = transaction {
            (EventsTable innerJoin EventTeamsTable).selectAll()
                .where {
                    (EventTeamsTable.teamId eq UUID.fromString(teamId)) and
                        (EventsTable.externalSource eq "nds") and
                        (EventsTable.startAt eq NdsTestFixtures.MONDAYS[1].atTime(18, 0).toInstant(java.time.ZoneOffset.UTC))
                }
                .count()
        }
        assertEquals(0, newEventOnGroupDate) // group default teamorg → not created here
    }

    @Test
    fun `conflict keep-nds on an event shared with another team detaches instead of cancelling`() = withTeamorgTestApplication {
        val mgr = register("nds_shared_conflict@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "SharedConflictClub")
        val teamA = createTeam(mgr.token, clubId, "Shared Team A")
        val teamB = createTeam(mgr.token, clubId, "Shared Team B")

        // One TeamOrg event shared by both teams (e.g. a joint training).
        val sharedEventId = createConflictingEvent(teamA, NdsTestFixtures.MONDAYS[0], mgr.userId, "Gemeinsames Training")
        transaction {
            EventTeamsTable.insert {
                it[EventTeamsTable.eventId] = sharedEventId
                it[EventTeamsTable.teamId] = UUID.fromString(teamB)
            }
        }

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-shared-1"), teamA)
        val parsed = parseResponse.anwesenheitsliste!!
        val conflictGroup = parseResponse.conflicts.single { it.dates.any { d -> d.date == NdsTestFixtures.MONDAYS[0] } }

        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamA,
                parsed = parsed,
                importEvents = true,
                attendanceMode = "discard",
                seriesTimes = defaultSeriesTimes(parseResponse.series),
                conflictResolutions = listOf(NdsConflictResolution(seriesKey = conflictGroup.seriesKey, keep = "nds"))
            ))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        // The shared event stays active — cancelling it would have wrongly affected team B too.
        val sharedStatus = transaction {
            EventsTable.selectAll().where { EventsTable.id eq sharedEventId }.single()[EventsTable.status]
        }
        assertEquals(EventStatus.active, sharedStatus)

        // Team A's link to the shared event is gone; team B's is intact.
        val (teamALinked, teamBLinked) = transaction {
            val teamALinked = !EventTeamsTable.selectAll()
                .where { (EventTeamsTable.eventId eq sharedEventId) and (EventTeamsTable.teamId eq UUID.fromString(teamA)) }
                .empty()
            val teamBLinked = !EventTeamsTable.selectAll()
                .where { (EventTeamsTable.eventId eq sharedEventId) and (EventTeamsTable.teamId eq UUID.fromString(teamB)) }
                .empty()
            teamALinked to teamBLinked
        }
        assertFalse(teamALinked, "team A must be detached from the shared event")
        assertTrue(teamBLinked, "team B's link must survive untouched")

        // A new NDS event was created for team A on that date.
        val newEventForTeamA = transaction {
            (EventsTable innerJoin EventTeamsTable).selectAll()
                .where {
                    (EventTeamsTable.teamId eq UUID.fromString(teamA)) and
                        (EventsTable.externalSource eq "nds") and
                        (EventsTable.startAt eq NdsTestFixtures.MONDAYS[0].atTime(18, 0).toInstant(java.time.ZoneOffset.UTC))
                }
                .count()
        }
        assertEquals(1, newEventForTeamA)
    }

    @Test
    fun `re-import preserves a previously applied mapping`() = withTeamorgTestApplication {
        val mgr = register("nds_reimport_map@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ReimportMapClub")
        val teamId = createTeam(mgr.token, clubId, "ReimportMap Team")

        val real = register("reimportmap_real@example.com")
        makeClubMember(mgr.token, clubId, real.userId)

        val parsed = parseFile(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-9"))
        val rowKey = rowKeyFor(parsed, "Trainer")

        val first = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId, parsed = parsed, importEvents = false,
                mappings = listOf(NdsMapping(rowKey = rowKey, action = "map", userId = real.userId))
            ))
        }
        assertEquals(HttpStatusCode.OK, first.status)

        // Re-import the SAME file with NO mappings — the earlier mapping must not be reset.
        val second = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(teamId = teamId, parsed = parsed, importEvents = false))
        }
        assertEquals(HttpStatusCode.OK, second.status)

        val trainerUserId = transaction {
            NdsMembersTable.selectAll()
                .where { (NdsMembersTable.teamId eq UUID.fromString(teamId)) and (NdsMembersTable.lastName eq "Trainer") }
                .single()[NdsMembersTable.userId]
        }
        assertEquals(real.userId, trainerUserId.toString())
    }

    @Test
    fun `a mid-import mapping conflict rolls back the whole import`() = withTeamorgTestApplication {
        val mgr = register("nds_rollback@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "RollbackClub")
        val teamId = createTeam(mgr.token, clubId, "Rollback Team")

        val real = register("rollback_real@example.com")
        makeClubMember(mgr.token, clubId, real.userId)

        val parseResponse = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes("nds-t3-10"))
        val parsed = parseResponse.anwesenheitsliste!!

        // First import (no events) links `real` to the Trainer row.
        val setup = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId, parsed = parsed, importEvents = false,
                mappings = listOf(NdsMapping(rowKey = rowKeyFor(parsed, "Trainer"), action = "map", userId = real.userId))
            ))
        }
        assertEquals(HttpStatusCode.OK, setup.status)
        val memberCountBefore = transaction {
            NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq UUID.fromString(teamId) }.count()
        }
        // Lara already got a provisional user from `setup`'s default "create" action.
        val laraUserIdBefore = transaction {
            NdsMembersTable.selectAll()
                .where { (NdsMembersTable.teamId eq UUID.fromString(teamId)) and (NdsMembersTable.lastName eq "Müller") }
                .single()[NdsMembersTable.userId]
        }
        val eventCountBefore = transaction {
            EventTeamsTable.select(EventTeamsTable.eventId).where { EventTeamsTable.teamId eq UUID.fromString(teamId) }.count()
        }
        assertEquals(0L, eventCountBefore)

        // Second import attempts to also map `real` to a DIFFERENT identity (Müller/Lara) — conflict.
        // importEvents=true with valid seriesTimes so a successful run WOULD create 8 events.
        val resp = createJsonClient().post("/clubs/$clubId/nds/import") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsImportRequest(
                teamId = teamId,
                parsed = parsed,
                importEvents = true,
                attendanceMode = "discard",
                mappings = listOf(NdsMapping(rowKey = rowKeyFor(parsed, "Müller"), action = "map", userId = real.userId)),
                seriesTimes = defaultSeriesTimes(parseResponse.series)
            ))
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)

        val eventCountAfter = transaction {
            EventTeamsTable.select(EventTeamsTable.eventId).where { EventTeamsTable.teamId eq UUID.fromString(teamId) }.count()
        }
        assertEquals(0L, eventCountAfter, "the whole import (including events) must roll back")

        val memberCountAfter = transaction {
            NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq UUID.fromString(teamId) }.count()
        }
        assertEquals(memberCountBefore, memberCountAfter)

        val laraUserIdAfter = transaction {
            NdsMembersTable.selectAll()
                .where { (NdsMembersTable.teamId eq UUID.fromString(teamId)) and (NdsMembersTable.lastName eq "Müller") }
                .single()[NdsMembersTable.userId]
        }
        assertEquals(laraUserIdBefore, laraUserIdAfter) // the attempted (failed) mapping never committed
    }

    @Test
    fun `link carries subgroup memberships and absence rules to the real account`() = withTeamorgTestApplication {
        val mgr = register("nds_merge_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "MergeClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val lara = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }
        val provisionalUserId = lara.userId!!

        // The generic-join scenario: Lara registers herself and is added to the team directly,
        // so she is a club member with her own account, beside her placeholder.
        val laraUser = register("lara_generic@example.com")
        val realUserId = UUID.fromString(laraUser.userId)
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = laraUser.userId, role = "player"))
        }

        // Subgroup A: placeholder only -> must be repointed.
        // Subgroup B: placeholder AND real user -> placeholder row must be dropped, not clash on PK.
        val (groupA, groupB) = transaction {
            val a = UUID.randomUUID()
            val b = UUID.randomUUID()
            SubGroupsTable.insert { it[id] = a; it[SubGroupsTable.teamId] = teamId; it[name] = "Gruppe A" }
            SubGroupsTable.insert { it[id] = b; it[SubGroupsTable.teamId] = teamId; it[name] = "Gruppe B" }
            SubGroupMembersTable.insert { it[subGroupId] = a; it[userId] = provisionalUserId }
            SubGroupMembersTable.insert { it[subGroupId] = b; it[userId] = provisionalUserId }
            SubGroupMembersTable.insert { it[subGroupId] = b; it[userId] = realUserId }
            a to b
        }

        // An absence rule on the placeholder, plus a response pointing at it.
        val ruleId = transaction {
            val rid = UUID.randomUUID()
            AbwesenheitRulesTable.insert {
                it[id] = rid
                it[userId] = provisionalUserId
                it[presetType] = PresetType.injury
                it[label] = "Knie"
                it[ruleType] = RuleType.period
                it[startDate] = java.time.LocalDate.of(2026, 1, 1)
                it[endDate] = java.time.LocalDate.of(2026, 12, 31)
            }
            val anyEventId = AttendanceResponsesTable.select(AttendanceResponsesTable.eventId)
                .where { AttendanceResponsesTable.userId eq provisionalUserId }
                .map { it[AttendanceResponsesTable.eventId] }
                .first()
            AttendanceResponsesTable.update({
                (AttendanceResponsesTable.userId eq provisionalUserId) and
                    (AttendanceResponsesTable.eventId eq anyEventId)
            }) { it[abwesenheitRuleId] = rid }
            rid
        }

        val link = createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = laraUser.userId))
        }
        assertEquals(HttpStatusCode.OK, link.status)

        transaction {
            // Real user is now in both subgroups; nothing left on the placeholder.
            val realGroups = SubGroupMembersTable.select(SubGroupMembersTable.subGroupId)
                .where { SubGroupMembersTable.userId eq realUserId }
                .map { it[SubGroupMembersTable.subGroupId] }
                .toSet()
            assertEquals(setOf(groupA, groupB), realGroups)
            assertEquals(
                0,
                SubGroupMembersTable.selectAll()
                    .where { SubGroupMembersTable.userId eq provisionalUserId }.count().toInt()
            )

            // The absence rule moved rather than being CASCADE-deleted, and the moved response
            // still references it.
            assertEquals(
                realUserId,
                AbwesenheitRulesTable.select(AbwesenheitRulesTable.userId)
                    .where { AbwesenheitRulesTable.id eq ruleId }
                    .map { it[AbwesenheitRulesTable.userId] }
                    .single()
            )
            assertEquals(
                1,
                AttendanceResponsesTable.selectAll()
                    .where {
                        (AttendanceResponsesTable.userId eq realUserId) and
                            (AttendanceResponsesTable.abwesenheitRuleId eq ruleId)
                    }.count().toInt()
            )
        }
    }

    @Test
    fun `duplicate suggestions match a self-registered account to its imported roster row`() = withTeamorgTestApplication {
        val mgr = register("nds_sugg_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "SuggClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        // Before any real account joins: placeholders must not suggest each other.
        val empty = createJsonClient().get("/teams/$teamId/nds/duplicate-suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<DuplicateSuggestion>>()
        assertTrue(empty.isEmpty(), "placeholders must not be matching candidates")

        val lara = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }

        // Lara joins generically: her display name matches the roster row exactly.
        val laraUser = createJsonClient().post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("lara_sugg@example.com", "password123", "${lara.firstName} ${lara.lastName}"))
        }.body<AuthResponse>()
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = laraUser.userId, role = "player"))
        }

        val suggestions = createJsonClient().get("/teams/$teamId/nds/duplicate-suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<DuplicateSuggestion>>()

        val forLara = suggestions.single { it.memberId == lara.id }
        assertEquals(laraUser.userId, forLara.candidates.single().userId.toString())
        assertEquals("HIGH", forLara.candidates.single().score)
        // The import writes a response per activity (confirmed when attended, declined otherwise),
        // so assert non-zero rather than a fixture-coupled count.
        assertTrue(forLara.willMove.attendance > 0)

        // After the merge the row is resolved and drops out of the list.
        createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = laraUser.userId))
        }
        val after = createJsonClient().get("/teams/$teamId/nds/duplicate-suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<DuplicateSuggestion>>()
        assertTrue(after.none { it.memberId == lara.id })
    }

    @Test
    fun `duplicate suggestions require an elevated role`() = withTeamorgTestApplication {
        val mgr = register("nds_sugg_guard@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "SuggGuardClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val player = register("sugg_player@example.com")
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = player.userId, role = "player"))
        }

        val res2 = createJsonClient().get("/teams/$teamId/nds/duplicate-suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.Forbidden, res2.status)
    }

    @Test
    fun `parse with teamId keeps offering no lossy map for a placeholder whose real account has joined`() =
        withTeamorgTestApplication {
            val mgr = register("nds_wizard_unaffected_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
            val clubId = createClub(mgr.token, "WizardUnaffectedClub")
            val res = importAll(mgr.token, clubId)
            val teamId = UUID.fromString(res.teamId)

            val lara = createJsonClient().get("/teams/$teamId/nds/members") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            }.body<List<NdsMember>>().single { it.lastName == "Müller" }

            // Lara's real account joins the team under the same name — same setup that would make
            // the duplicate-suggestions endpoint surface her, but this is the wizard's *parse* path.
            val laraUser = createJsonClient().post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("lara_wizard@example.com", "password123", "${lara.firstName} ${lara.lastName}"))
            }.body<AuthResponse>()
            createJsonClient().post("/teams/$teamId/members") {
                header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
                contentType(ContentType.Application.Json)
                setBody(AddMemberRequest(userId = laraUser.userId, role = "player"))
            }

            // Re-parsing with teamId must still short-circuit on the placeholder's own linked
            // identity (alreadyLinkedUserId) rather than preselect the real account as a "map"
            // candidate — "map" doesn't move attendance/subgroups/rules like /link does.
            val response = parseFull(mgr.token, clubId, NdsTestFixtures.anwesenheitslisteBytes(), teamId = teamId.toString())
            val laraRowKey = NdsMemberMatcher.rowKey(lara.funktion, lara.lastName, lara.firstName)
            val suggestion = response.memberSuggestions.single { it.rowKey == laraRowKey }
            assertEquals(lara.userId.toString(), suggestion.alreadyLinkedUserId)
            assertNull(suggestion.preselectedUserId)
            assertTrue(suggestion.candidates.isEmpty())
        }
}
