package ch.teamorg.nds

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Builds a synthetic NDS "Anwesenheitsliste" .xlsx mirroring the real export layout
 * (see docs/nds-import-export-design.md §0.1). No real PII — used by parser/route tests.
 */
object NdsTestFixtures {

    // 4 Mondays + 4 Wednesdays starting Mon 2026-08-03 → two clean weekly series.
    val MONDAYS = listOf(
        LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10),
        LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 24)
    )
    val WEDNESDAYS = listOf(
        LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 12),
        LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 26)
    )
    // Interleaved in column order: MO, MI, MO, MI, ... matching the real sheet.
    val ACTIVITY_DATES: List<LocalDate> = MONDAYS.zip(WEDNESDAYS).flatMap { listOf(it.first, it.second) }

    fun anwesenheitslisteBytes(angebotId: String = "753813"): ByteArray {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Anwesenheitsliste")
            val firstCol = 5

            fun row(idx: Int): Row = sheet.createRow(idx)
            fun label(r: Row, text: String) { r.createCell(0).setCellValue(text) }

            fun meta(idx: Int, key: String, value: String) {
                val r = row(idx); label(r, key); r.createCell(1).setCellValue(value)
            }
            meta(0, "Angebot", angebotId)
            meta(1, "Kurs", "Test Kurs")
            meta(2, "Hauptsportart", "Volleyball")
            meta(3, "Gruppengrösse", "12/12")
            meta(4, "Kursstatus", "Durchführung")

            val weekdayRow = row(5); label(weekdayRow, "Wochentag")
            val dateRow = row(6); label(dateRow, "Datum")
            val kwRow = row(7); label(kwRow, "Kalenderwoche")
            val symbolRow = row(8); label(symbolRow, "Symbol der Tagesaktivität")
            val durationRow = row(9); label(durationRow, "Dauer der Tagesaktivität")
            val athRow = row(10); label(athRow, "Trainings Athletik und Psyche")

            ACTIVITY_DATES.forEachIndexed { i, date ->
                val col = firstCol + i
                weekdayRow.createCell(col).setCellValue(if (date.dayOfWeek.value == 1) "MO" else "MI")
                dateRow.createCell(col).setCellValue(date) // numeric serial
                kwRow.createCell(col).setCellValue(0.0)
                symbolRow.createCell(col).setCellValue("T")
                durationRow.createCell(col).setCellValue("1,5") // comma decimal, hours
                athRow.createCell(col).setCellValue("Normal")
            }

            // member-table header
            val header = row(11)
            header.createCell(0).setCellValue("Nummer")
            header.createCell(1).setCellValue("Name")
            header.createCell(2).setCellValue("Vorname")
            header.createCell(3).setCellValue("Funktion / Geburtstag")
            header.createCell(4).setCellValue("Zusätze / Alter")

            // Leiter section: one coach attending activities 0 and 2 (cols 5 and 7).
            label(row(12), "Leiter/-in(1):")
            val coach = row(13)
            coach.createCell(0).setCellValue(1.0)
            coach.createCell(1).setCellValue("Trainer")
            coach.createCell(2).setCellValue("Anna")
            coach.createCell(3).setCellValue("J+S-Leiter/-in (J)")
            coach.createCell(firstCol + 0).setCellValue("J")
            coach.createCell(firstCol + 2).setCellValue("J")

            // Teilnehmer section: two players with birthdates.
            label(row(14), "Teilnehmer/-in(2):")
            val p1 = row(15)
            p1.createCell(0).setCellValue(1.0)
            p1.createCell(1).setCellValue("Müller")
            p1.createCell(2).setCellValue("Lara")
            p1.createCell(3).setCellValue(LocalDate.of(2008, 5, 20))
            p1.createCell(4).setCellValue(18.0)
            // attends activities 0,1,2 (cols 5,6,7)
            p1.createCell(firstCol + 0).setCellValue("J")
            p1.createCell(firstCol + 1).setCellValue("J")
            p1.createCell(firstCol + 2).setCellValue("J")

            val p2 = row(16)
            p2.createCell(0).setCellValue(2.0)
            p2.createCell(1).setCellValue("Meier")
            p2.createCell(2).setCellValue("Tim")
            p2.createCell(3).setCellValue(LocalDate.of(2009, 1, 15))
            p2.createCell(4).setCellValue(17.0)
            // attends activity 1 only (col 6)
            p2.createCell(firstCol + 1).setCellValue("J")

            val out = ByteArrayOutputStream()
            wb.write(out)
            return out.toByteArray()
        }
    }
}
