package ch.teamorg.infra.nds

import ch.teamorg.domain.models.NdsMemberInput
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.time.LocalDate

/**
 * Parses the two dedicated NDS person exports into [NdsMemberInput]s carrying the PERSONENNUMMER:
 *  - "Teilnehmende" → comma-delimited CSV (cols PERSONENNUMMER, NAME, VORNAME, …)
 *  - "Leiterinnen/Leiter" → xlsx (cols Personennummer, Name, Vorname, Geburtsdatum, …)
 * See docs/nds-import-export-design.md §0.5.
 */
object RosterFileParser {

    fun parseTeilnehmendeCsv(input: InputStream): List<NdsMemberInput> {
        val lines = input.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) throw NdsParseException("Leere Datei")

        val header = splitCsv(lines.first()).map { it.trim().uppercase() }
        val pnIdx = header.indexOf("PERSONENNUMMER")
        val nameIdx = header.indexOf("NAME")
        val vornameIdx = header.indexOf("VORNAME")
        if (pnIdx < 0 || nameIdx < 0 || vornameIdx < 0) {
            throw NdsParseException("Keine gültige Teilnehmenden-Datei (Spalten PERSONENNUMMER/NAME/VORNAME fehlen)")
        }
        val gebIdx = header.indexOf("GEBURTSDATUM")

        return lines.drop(1).mapNotNull { line ->
            val cols = splitCsv(line)
            val last = cols.getOrNull(nameIdx)?.trim().orEmpty()
            val first = cols.getOrNull(vornameIdx)?.trim().orEmpty()
            if (last.isEmpty()) return@mapNotNull null
            NdsMemberInput(
                lastName = last,
                firstName = first,
                birthDate = gebIdx.takeIf { it >= 0 }?.let { parseGermanDate(cols.getOrNull(it)) },
                personNumber = cols.getOrNull(pnIdx)?.trim()?.ifBlank { null },
                funktion = "Teilnehmer/in"
            )
        }
    }

    fun parseLeiterXlsx(input: InputStream): List<NdsMemberInput> {
        WorkbookFactory.create(input).use { wb ->
            val sheet = wb.getSheetAt(0) ?: throw NdsParseException("Datei enthält kein Tabellenblatt")
            val headerRow = sheet.firstOrNull { r -> (0..6).any { cellText(r.getCell(it))?.lowercase()?.contains("personennummer") == true } }
                ?: throw NdsParseException("Keine gültige Leiter-Datei (Kopfzeile nicht gefunden)")

            fun col(vararg names: String): Int {
                for (c in 0 until headerRow.lastCellNum) {
                    val h = cellText(headerRow.getCell(c))?.lowercase()?.trim() ?: continue
                    if (names.any { h == it || h.startsWith(it) }) return c
                }
                return -1
            }
            val pn = col("personennummer")
            val name = col("name")
            val vorname = col("vorname")
            val geb = col("geburtsdatum")
            if (pn < 0 || name < 0 || vorname < 0) {
                throw NdsParseException("Keine gültige Leiter-Datei (Spalten Personennummer/Name/Vorname fehlen)")
            }

            val members = mutableListOf<NdsMemberInput>()
            for (row in sheet) {
                if (row.rowNum <= headerRow.rowNum) continue
                val last = cellText(row.getCell(name))?.trim().orEmpty()
                if (last.isEmpty()) continue
                members.add(
                    NdsMemberInput(
                        lastName = last,
                        firstName = cellText(row.getCell(vorname))?.trim().orEmpty(),
                        birthDate = if (geq(geb)) cellDate(row.getCell(geb)) else null,
                        personNumber = cellText(row.getCell(pn))?.trim()?.ifBlank { null },
                        funktion = "Leiter/in"
                    )
                )
            }
            return members
        }
    }

    private fun geq(idx: Int) = idx >= 0

    // Minimal RFC-4180 CSV split (comma delimiter, double-quote escaping).
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun parseGermanDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.ifBlank { null } ?: return null
        // NDS person files use d.M.yyyy (e.g. "13.5.2007").
        return runCatching {
            val (d, m, y) = s.split(".").map { it.trim().toInt() }
            LocalDate.of(y, m, d)
        }.getOrNull()
    }

    private fun cellText(cell: Cell?): String? {
        if (cell == null) return null
        val raw = when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val d = cell.numericCellValue
                if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
            }
            CellType.FORMULA -> runCatching { cell.stringCellValue }.getOrNull()
            else -> null
        }
        return raw?.trim()?.ifBlank { null }
    }

    private fun cellDate(cell: Cell?): LocalDate? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> runCatching { DateUtil.getLocalDateTime(cell.numericCellValue).toLocalDate() }.getOrNull()
            CellType.STRING -> parseGermanDate(cell.stringCellValue)
            else -> null
        }
    }
}
