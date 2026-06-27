package ch.teamorg.nds

import ch.teamorg.infra.nds.NdsParseException
import ch.teamorg.infra.nds.RosterFileParser
import java.io.ByteArrayInputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RosterFileParserTest {

    @Test
    fun `parses Teilnehmende CSV first three columns with person number`() {
        val list = RosterFileParser.parseTeilnehmendeCsv(ByteArrayInputStream(NdsTestFixtures.teilnehmendeCsvBytes()))
        assertEquals(2, list.size)
        val lara = list.single { it.lastName == "Müller" }
        assertEquals("Lara", lara.firstName)
        assertEquals("111111111", lara.personNumber)
        assertEquals("Teilnehmer/in", lara.funktion)
        assertNull(lara.birthDate) // GEBURTSDATUM empty in the export
    }

    @Test
    fun `parses Leiter xlsx with person number and birthdate`() {
        val list = RosterFileParser.parseLeiterXlsx(ByteArrayInputStream(NdsTestFixtures.leiterXlsxBytes()))
        assertEquals(1, list.size)
        val coach = list.single()
        assertEquals("Trainer", coach.lastName)
        assertEquals("Anna", coach.firstName)
        assertEquals("100383194", coach.personNumber)
        assertEquals(LocalDate.of(2007, 5, 13), coach.birthDate)
        assertEquals("Leiter/in", coach.funktion)
    }

    @Test
    fun `rejects a CSV without the required columns`() {
        val bad = "FOO,BAR\n1,2".toByteArray()
        assertFailsWith<NdsParseException> {
            RosterFileParser.parseTeilnehmendeCsv(ByteArrayInputStream(bad))
        }
    }
}
