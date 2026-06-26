package ch.teamorg.infra.nds

import ch.teamorg.domain.models.ParsedActivity
import ch.teamorg.domain.models.ParsedAnwesenheitsliste
import ch.teamorg.domain.models.ParsedMember
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.time.LocalDate

class NdsParseException(message: String) : Exception(message)

/**
 * Parses an NDS J+S "Anwesenheitsliste" .xlsx export into a typed model.
 * The sheet is a human-readable attendance matrix (see docs/nds-import-export-design.md §0.1):
 * metadata rows, then per-activity columns (date/weekday/symbol/duration), then a roster split
 * into Leiter/-in and Teilnehmer/-in sections with a 'J' marker per attended activity.
 *
 * Parsing scans column A by label (not fixed row numbers) so it tolerates varying roster sizes.
 */
object AnwesenheitslisteParser {

    fun parse(input: InputStream): ParsedAnwesenheitsliste {
        WorkbookFactory.create(input).use { workbook ->
            val sheet = workbook.getSheetAt(0)
                ?: throw NdsParseException("Datei enthält kein Tabellenblatt")

            // Index rows by their column-A label.
            val labelRows = HashMap<String, Row>()
            for (row in sheet) {
                val label = cellString(row.getCell(0))?.lowercase() ?: continue
                labelRows.putIfAbsent(label, row)
            }

            val angebotId = cellString(labelRows["angebot"]?.getCell(1))?.let { stripTrailingZero(it) }
                ?: throw NdsParseException("Keine gültige Anwesenheitsliste: 'Angebot' nicht gefunden")

            val kursName = cellString(labelRows["kurs"]?.getCell(1))
            val hauptsportart = cellString(labelRows["hauptsportart"]?.getCell(1))
            val gruppengroesse = cellString(labelRows["gruppengrösse"]?.getCell(1))
                ?: cellString(labelRows["gruppengroesse"]?.getCell(1))
            val kursstatus = cellString(labelRows["kursstatus"]?.getCell(1))

            val activities = parseActivities(labelRows)
            val members = parseMembers(sheet, activities)

            return ParsedAnwesenheitsliste(
                angebotId = angebotId,
                kursName = kursName,
                hauptsportart = hauptsportart,
                gruppengroesse = gruppengroesse,
                kursstatus = kursstatus,
                activities = activities,
                members = members
            )
        }
    }

    private fun parseActivities(labelRows: Map<String, Row>): List<ParsedActivity> {
        val dateRow = labelRows["datum"] ?: throw NdsParseException("Aktivitätszeile 'Datum' nicht gefunden")
        val weekdayRow = labelRows["wochentag"]
        val kwRow = labelRows["kalenderwoche"]
        val symbolRow = labelRows["symbol der tagesaktivität"]
        val durationRow = labelRows["dauer der tagesaktivität"]

        val activities = mutableListOf<ParsedActivity>()
        // Activity columns are everything from col 1 onward that carries a date in the Datum row.
        val lastCol = dateRow.lastCellNum.toInt()
        for (col in 1 until lastCol) {
            val date = cellDate(dateRow.getCell(col)) ?: continue
            val symbol = cellString(symbolRow?.getCell(col)) ?: "T"
            val durationMin = cellDouble(durationRow?.getCell(col))?.let { (it * 60).toInt() }
            activities.add(
                ParsedActivity(
                    date = date,
                    weekday = cellString(weekdayRow?.getCell(col)),
                    kw = cellDouble(kwRow?.getCell(col))?.toInt(),
                    symbol = symbol,
                    durationMin = durationMin,
                    fokus = null,
                    columnIndex = col
                )
            )
        }
        if (activities.isEmpty()) throw NdsParseException("Keine Aktivitäten in der Datei gefunden")
        return activities
    }

    private fun parseMembers(sheet: Sheet, activities: List<ParsedActivity>): List<ParsedMember> {
        val members = mutableListOf<ParsedMember>()
        var currentFunktion: String? = null

        for (row in sheet) {
            val col0 = cellString(row.getCell(0))
            val col0Lower = col0?.lowercase()

            // Section headers, e.g. "Leiter/-in(2):" / "Teilnehmer/-in(14):"
            if (col0Lower != null && col0Lower.startsWith("leiter")) {
                currentFunktion = "Leiter/in"; continue
            }
            if (col0Lower != null && col0Lower.startsWith("teilnehmer")) {
                currentFunktion = "Teilnehmer/in"; continue
            }
            if (currentFunktion == null) continue

            val lastName = cellString(row.getCell(1)) ?: continue
            val firstName = cellString(row.getCell(2)) ?: ""
            val nummer = cellDouble(row.getCell(0))?.toInt()

            // Participants carry a birthdate in col 3; leaders carry their function text there.
            val birthDate = if (currentFunktion == "Teilnehmer/in") cellDate(row.getCell(3)) else null

            val attended = activities.filter { isPresent(row.getCell(it.columnIndex)) }.map { it.date }

            members.add(
                ParsedMember(
                    funktion = currentFunktion,
                    nummer = nummer,
                    lastName = lastName,
                    firstName = firstName,
                    birthDate = birthDate,
                    attendedDates = attended
                )
            )
        }
        return members
    }

    private fun isPresent(cell: Cell?): Boolean =
        cellString(cell)?.trim()?.equals("J", ignoreCase = true) == true

    // --- typed cell readers -------------------------------------------------

    private fun cellString(cell: Cell?): String? {
        if (cell == null) return null
        val raw = when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val d = cell.numericCellValue
                if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> runCatching { cell.stringCellValue }.getOrNull()
            else -> null
        }
        return raw?.trim()?.ifBlank { null }
    }

    private fun cellDouble(cell: Cell?): Double? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
    }

    private fun cellDate(cell: Cell?): LocalDate? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> runCatching { DateUtil.getLocalDateTime(cell.numericCellValue).toLocalDate() }.getOrNull()
            CellType.STRING -> {
                val s = cell.stringCellValue.trim()
                // Excel serial as text, or dd.MM.yyyy.
                s.toDoubleOrNull()?.let { return runCatching { DateUtil.getLocalDateTime(it).toLocalDate() }.getOrNull() }
                runCatching {
                    val (d, m, y) = s.split(".").map { it.trim().toInt() }
                    LocalDate.of(y, m, d)
                }.getOrNull()
            }
            else -> null
        }
    }

    private fun stripTrailingZero(s: String): String =
        if (s.endsWith(".0")) s.dropLast(2) else s
}
