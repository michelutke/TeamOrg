package ch.teamorg.nds

import ch.teamorg.infra.nds.AnwesenheitslisteParser
import ch.teamorg.infra.nds.NdsParseException
import java.io.ByteArrayInputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnwesenheitslisteParserTest {

    private fun parse() =
        AnwesenheitslisteParser.parse(ByteArrayInputStream(NdsTestFixtures.anwesenheitslisteBytes()))

    @Test
    fun `parses course metadata`() {
        val r = parse()
        assertEquals("753813", r.angebotId)
        assertEquals("Test Kurs", r.kursName)
        assertEquals("Volleyball", r.hauptsportart)
        assertEquals("12/12", r.gruppengroesse)
    }

    @Test
    fun `parses all activity columns with date duration and symbol`() {
        val r = parse()
        assertEquals(8, r.activities.size)
        assertEquals(NdsTestFixtures.ACTIVITY_DATES.toSet(), r.activities.map { it.date }.toSet())
        // '1,5' hours → 90 minutes.
        assertTrue(r.activities.all { it.durationMin == 90 })
        assertTrue(r.activities.all { it.symbol == "T" })
    }

    @Test
    fun `splits leaders and participants with correct funktion`() {
        val r = parse()
        assertEquals(3, r.members.size)
        val coach = r.members.single { it.lastName == "Trainer" }
        assertEquals("Leiter/in", coach.funktion)
        assertNull(coach.birthDate) // leaders carry function text in the birthday column
        val lara = r.members.single { it.lastName == "Müller" }
        assertEquals("Teilnehmer/in", lara.funktion)
        assertEquals(LocalDate.of(2008, 5, 20), lara.birthDate)
    }

    @Test
    fun `maps J markers to attended dates per member`() {
        val r = parse()
        val coach = r.members.single { it.lastName == "Trainer" }
        assertEquals(setOf(NdsTestFixtures.ACTIVITY_DATES[0], NdsTestFixtures.ACTIVITY_DATES[2]), coach.attendedDates.toSet())
        val lara = r.members.single { it.lastName == "Müller" }
        assertEquals(
            setOf(NdsTestFixtures.ACTIVITY_DATES[0], NdsTestFixtures.ACTIVITY_DATES[1], NdsTestFixtures.ACTIVITY_DATES[2]),
            lara.attendedDates.toSet()
        )
        val tim = r.members.single { it.lastName == "Meier" }
        assertEquals(setOf(NdsTestFixtures.ACTIVITY_DATES[1]), tim.attendedDates.toSet())
    }

    @Test
    fun `rejects a non-Anwesenheitsliste file`() {
        val notAList = run {
            org.apache.poi.xssf.usermodel.XSSFWorkbook().use { wb ->
                wb.createSheet("x").createRow(0).createCell(0).setCellValue("Something else")
                val out = java.io.ByteArrayOutputStream(); wb.write(out); out.toByteArray()
            }
        }
        assertFailsWith<NdsParseException> {
            AnwesenheitslisteParser.parse(ByteArrayInputStream(notAList))
        }
    }
}
