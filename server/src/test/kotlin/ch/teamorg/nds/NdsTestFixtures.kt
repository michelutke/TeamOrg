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

    /**
     * Large realistic fixture: 12 activity columns (6 MO + 6 MI, alternating weekly), 2 leaders,
     * 14 participants with birthdates, multi-word names, J marks scattered (not all-J).
     * Activity columns start at POI index 5 to mirror the real NDS export.
     */
    fun largeAnwesenheitslisteBytes(angebot: String = "LARGE-999"): ByteArray {
        // 6 Mondays + 6 Wednesdays starting 2026-09-07 → 12 activity columns interleaved MO/MI.
        val mondays = (0 until 6).map { LocalDate.of(2026, 9, 7).plusWeeks(it.toLong()) }
        val wednesdays = (0 until 6).map { LocalDate.of(2026, 9, 9).plusWeeks(it.toLong()) }
        val activityDates: List<LocalDate> = mondays.zip(wednesdays).flatMap { listOf(it.first, it.second) }

        // 2 leaders: their J marks for each week-index (0-based into activityDates).
        val leaderJmarks = listOf(
            setOf(0, 1, 2, 3, 4, 5, 6, 7),        // coach 1: first 8
            setOf(0, 2, 4, 6, 8, 10)               // coach 2: every even
        )
        // 14 participants: varied scattered attendance (not all-J, none empty).
        val participantJmarks = listOf(
            setOf(0, 1, 2, 3),                     // participant 1
            setOf(1, 2, 3, 4, 5),                  // participant 2
            setOf(0, 2, 4, 6, 8, 10),              // participant 3
            setOf(1, 3, 5, 7, 9, 11),              // participant 4
            setOf(0, 1, 4, 5, 8, 9),               // participant 5
            setOf(2, 3, 6, 7, 10, 11),             // participant 6
            setOf(0, 3, 6, 9),                      // participant 7
            setOf(1, 4, 7, 10),                     // participant 8
            setOf(2, 5, 8, 11),                     // participant 9
            setOf(0, 1, 2, 3, 4, 5),               // participant 10
            setOf(6, 7, 8, 9, 10, 11),             // participant 11
            setOf(0, 2, 4, 6, 8),                  // participant 12
            setOf(1, 3, 5, 7, 9),                  // participant 13
            setOf(0, 1, 3, 5, 7, 10)               // participant 14
        )

        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Anwesenheitsliste")
            val firstCol = 5

            fun row(idx: Int): Row = sheet.createRow(idx)
            fun label(r: Row, text: String) { r.createCell(0).setCellValue(text) }

            fun meta(idx: Int, key: String, value: String) {
                val r = row(idx); label(r, key); r.createCell(1).setCellValue(value)
            }
            meta(0, "Angebot", angebot)
            meta(1, "Kurs", "Grosser Kurs")
            meta(2, "Hauptsportart", "Fussball")
            meta(3, "Gruppengrösse", "16/16")
            meta(4, "Kursstatus", "Durchführung")

            val weekdayRow = row(5); label(weekdayRow, "Wochentag")
            val dateRow = row(6); label(dateRow, "Datum")
            val kwRow = row(7); label(kwRow, "Kalenderwoche")
            val symbolRow = row(8); label(symbolRow, "Symbol der Tagesaktivität")
            val durationRow = row(9); label(durationRow, "Dauer der Tagesaktivität")
            val athRow = row(10); label(athRow, "Trainings Athletik und Psyche")

            activityDates.forEachIndexed { i, date ->
                val col = firstCol + i
                weekdayRow.createCell(col).setCellValue(if (date.dayOfWeek.value == 1) "MO" else "MI")
                dateRow.createCell(col).setCellValue(date)
                kwRow.createCell(col).setCellValue(0.0)
                symbolRow.createCell(col).setCellValue("T")
                durationRow.createCell(col).setCellValue("1,5")
                athRow.createCell(col).setCellValue("Normal")
            }

            // member-table header
            val header = row(11)
            header.createCell(0).setCellValue("Nummer")
            header.createCell(1).setCellValue("Name")
            header.createCell(2).setCellValue("Vorname")
            header.createCell(3).setCellValue("Funktion / Geburtstag")
            header.createCell(4).setCellValue("Zusätze / Alter")

            // Leiter section: 2 coaches.
            var rowIdx = 12
            label(row(rowIdx++), "Leiter/-in(2):")

            val leaderData = listOf(
                Triple("Von der Heydt", "Andreas", "J+S-Leiter/-in (J)"),
                Triple("De La Rosa", "Maria Elena", "J+S-Leiter/-in (J)")
            )
            leaderData.forEachIndexed { i, (last, first, func) ->
                val r = row(rowIdx++)
                r.createCell(0).setCellValue((i + 1).toDouble())
                r.createCell(1).setCellValue(last)
                r.createCell(2).setCellValue(first)
                r.createCell(3).setCellValue(func)
                leaderJmarks[i].forEach { col -> r.createCell(firstCol + col).setCellValue("J") }
            }

            // Teilnehmer section: 14 participants with birthdates.
            label(row(rowIdx++), "Teilnehmer/-in(14):")

            data class Participant(val last: String, val first: String, val birth: LocalDate)
            val participants = listOf(
                Participant("Müller-Schneider", "Lara Sophie", LocalDate.of(2009, 3, 15)),
                Participant("Van den Berg", "Tim Jonas", LocalDate.of(2008, 7, 22)),
                Participant("Schäfer", "Emma Lea", LocalDate.of(2010, 1, 8)),
                Participant("O'Brien", "Patrick", LocalDate.of(2009, 11, 30)),
                Participant("Zimmermann", "Noah", LocalDate.of(2008, 5, 19)),
                Participant("Fischer-Braun", "Mia", LocalDate.of(2010, 9, 4)),
                Participant("Kowalski", "Lukas", LocalDate.of(2009, 6, 12)),
                Participant("Saint-Pierre", "Chloé", LocalDate.of(2008, 12, 3)),
                Participant("Bauer", "Felix Anton", LocalDate.of(2010, 4, 27)),
                Participant("Hoffmann", "Lea Marie", LocalDate.of(2009, 8, 16)),
                Participant("Schmidt-Weber", "Ben", LocalDate.of(2008, 2, 9)),
                Participant("Roth", "Clara", LocalDate.of(2010, 7, 21)),
                Participant("Huber", "Jan Philipp", LocalDate.of(2009, 10, 5)),
                Participant("Meyer", "Anna Lena", LocalDate.of(2008, 4, 14))
            )
            participants.forEachIndexed { i, p ->
                val r = row(rowIdx++)
                r.createCell(0).setCellValue((i + 1).toDouble())
                r.createCell(1).setCellValue(p.last)
                r.createCell(2).setCellValue(p.first)
                r.createCell(3).setCellValue(p.birth)
                r.createCell(4).setCellValue((2026 - p.birth.year).toDouble())
                participantJmarks[i].forEach { col -> r.createCell(firstCol + col).setCellValue("J") }
            }

            val out = ByteArrayOutputStream()
            wb.write(out)
            return out.toByteArray()
        }
    }

    /** Teilnehmende CSV (comma-delimited, PERSONENNUMMER/NAME/VORNAME, no birthdate) for the two players. */
    fun teilnehmendeCsvBytes(): ByteArray {
        val header = "PERSONENNUMMER,NAME,VORNAME,GEBURTSDATUM,GESCHLECHT,AHVN_NR,PEID,NATIONALITÄT," +
            "MUTTERSPRACHE,STRASSE,HAUSNUMMER,PLZ,ORT,LAND"
        val rows = listOf(
            "111111111,Müller,Lara,,,,,,,,,,,",
            "222222222,Meier,Tim,,,,,,,,,,,"
        )
        return (listOf(header) + rows).joinToString("\r\n").toByteArray(Charsets.UTF_8)
    }

    /** Leiterinnen/Leiter xlsx (Personennummer/Name/Vorname/Geburtsdatum/…) for the one coach. */
    fun leiterXlsxBytes(): ByteArray {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Leiter")
            val header = sheet.createRow(0)
            listOf("Personennummer", "Name", "Vorname", "Geburtsdatum", "PLZ", "Ort (rechtl. Sitz)", "Funktion")
                .forEachIndexed { i, h -> header.createCell(i).setCellValue(h) }
            val r = sheet.createRow(1)
            r.createCell(0).setCellValue("100383194")
            r.createCell(1).setCellValue("Trainer")
            r.createCell(2).setCellValue("Anna")
            r.createCell(3).setCellValue("13.5.2007")
            r.createCell(4).setCellValue("3604")
            r.createCell(5).setCellValue("Thun")
            r.createCell(6).setCellValue("J+S-Leiter/-in")
            val out = ByteArrayOutputStream()
            wb.write(out)
            return out.toByteArray()
        }
    }
}
