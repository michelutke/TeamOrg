package ch.teamorg.nds

import ch.teamorg.infra.nds.AktivitaetRow
import ch.teamorg.infra.nds.NdsCsvWriter
import ch.teamorg.infra.nds.NdsRules
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NdsRulesAndCsvTest {

    @Test
    fun `symbol maps to aktivitaetstyp and event type`() {
        assertEquals("Training", NdsRules.symbolToAktivitaetstyp("T"))
        assertEquals("Wettkampf", NdsRules.symbolToAktivitaetstyp("W"))
        assertEquals("Trainingstag", NdsRules.symbolToAktivitaetstyp("TT"))
        assertEquals("Lagertag", NdsRules.symbolToAktivitaetstyp("L"))
        assertEquals("training", NdsRules.aktivitaetstypToEventType("Training"))
        assertEquals("match", NdsRules.aktivitaetstypToEventType("Wettkampf"))
        assertEquals("other", NdsRules.aktivitaetstypToEventType("Trainingstag"))
    }

    @Test
    fun `time and location required only for training`() {
        assertTrue(NdsRules.requiresTimeAndLocation("Training"))
        assertTrue(!NdsRules.requiresTimeAndLocation("Wettkampf"))
        assertTrue(!NdsRules.requiresTimeAndLocation("Trainingstag"))
    }

    @Test
    fun `duration snaps to nearest allowed and is null when unknown NG`() {
        // NG2 training allows 60,90,...; 100 → 90.
        assertEquals(90, NdsRules.snapDuration("NG2", "Training", 100))
        assertEquals(120, NdsRules.snapDuration("2", "Training", 115))
        // Unknown NG → no validation, value passes through.
        assertEquals(105, NdsRules.snapDuration(null, "Training", 105))
        // Lagertag forbids duration → null.
        assertNull(NdsRules.snapDuration("NG2", "Lagertag", 240))
    }

    @Test
    fun `aktivitaeten csv has BOM exact header and dd_MM_yyyy dates`() {
        val csv = NdsCsvWriter.aktivitaeten(
            listOf(
                AktivitaetRow("Training", LocalDate.of(2026, 8, 3), LocalTime.of(18, 0), 90, "Halle Thun"),
                AktivitaetRow("Wettkampf", LocalDate.of(2026, 8, 5), null, null, null)
            )
        )
        assertTrue(csv.startsWith("﻿"), "must start with UTF-8 BOM")
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertEquals("AKTIVITAETSTYP;DATUM;ZEIT;DAUER;ORT;FOKUS", lines[0])
        assertEquals("Training;03.08.2026;18:00;90;Halle Thun;", lines[1])
        // Wettkampf: ZEIT and ORT forbidden → blank.
        assertEquals("Wettkampf;05.08.2026;;;;", lines[2])
    }

    @Test
    fun `awk csv reuses activity fields verbatim (consistency invariant)`() {
        val eventId = java.util.UUID.randomUUID()
        val rowByEvent = mapOf(eventId to AktivitaetRow("Training", LocalDate.of(2026, 8, 3), LocalTime.of(18, 0), 90, "Halle Thun"))
        val csv = NdsCsvWriter.awk(
            listOf(ch.teamorg.domain.repositories.ExportAttendance(eventId, "123456789", "Teilnehmer/in", "Müller", "Lara")),
            rowByEvent
        )
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertEquals("PERSONENNUMMER;FUNKTION;DATUM;AKTIVITAETSTYP;ZEIT;DAUER;ORT", lines[0])
        assertEquals("123456789;Teilnehmer/in;03.08.2026;Training;18:00;90;Halle Thun", lines[1])
    }

    @Test
    fun `csv escapes separators and quotes`() {
        val csv = NdsCsvWriter.aktivitaeten(
            listOf(AktivitaetRow("Training", LocalDate.of(2026, 8, 3), LocalTime.of(18, 0), 90, "Halle; \"A\""))
        )
        val line = csv.removePrefix("﻿").split("\r\n")[1]
        assertTrue(line.endsWith("\"Halle; \"\"A\"\"\";"))
    }
}
