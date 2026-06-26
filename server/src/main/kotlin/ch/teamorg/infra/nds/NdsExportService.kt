package ch.teamorg.infra.nds

import ch.teamorg.domain.repositories.ExportActivity
import ch.teamorg.domain.repositories.NdsRepository
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
data class NdsPreflightIssue(val severity: String, val code: String, val message: String)

@Serializable
data class NdsPreflightReport(val ok: Boolean, val issues: List<NdsPreflightIssue>)

/** The exact field values emitted for one Aktivitäten row; AWK rows reuse these verbatim. */
data class AktivitaetRow(
    val typ: String,
    val date: LocalDate,
    val zeit: LocalTime?,
    val dauerMin: Int?,
    val ort: String?
)

data class NdsCsvBundle(val aktivitaetenCsv: String, val awkCsv: String)

/**
 * Builds the two NDS import CSVs from TeamOrg events + present attendance records, and validates
 * the data before allowing an export. See docs/nds-import-export-design.md §8 & §10.
 */
class NdsExportService(private val ndsRepository: NdsRepository) {

    suspend fun preflight(teamId: UUID): NdsPreflightReport {
        val issues = mutableListOf<NdsPreflightIssue>()
        val nds = ndsRepository.getTeamNds(teamId)

        if (nds?.angebotId.isNullOrBlank()) {
            issues.add(NdsPreflightIssue("error", "no_angebot", "Kein NDS-Angebot mit diesem Team verknüpft."))
        }
        if (nds?.nutzergruppe.isNullOrBlank()) {
            issues.add(NdsPreflightIssue("warning", "no_ng", "Keine Nutzergruppe gesetzt – die Dauer wird nicht geprüft."))
        }

        val activities = ndsRepository.listExportActivities(teamId)
        val attendances = ndsRepository.listExportAttendances(teamId)

        // Members with a present record but no PERSONENNUMMER → cannot export them.
        val missing = attendances.filter { it.personNumber.isNullOrBlank() }
            .map { "${it.firstName} ${it.lastName}".trim() }
            .distinct()
        if (missing.isNotEmpty()) {
            issues.add(
                NdsPreflightIssue(
                    "error", "missing_person_number",
                    "Personennummer fehlt für: ${missing.joinToString(", ")}"
                )
            )
        }

        val rows = activities.map { it.eventId to buildRow(it, nds?.nutzergruppe) }
        // Trainings without a location → NDS rejects them.
        val trainingsNoOrt = rows.count { (_, r) -> r.typ == NdsRules.TYP_TRAINING && r.ort.isNullOrBlank() }
        if (trainingsNoOrt > 0) {
            issues.add(
                NdsPreflightIssue(
                    "error", "training_missing_location",
                    "$trainingsNoOrt Training(s) ohne Ort. NDS verlangt Zeit und Ort für Trainings."
                )
            )
        }

        // Durations that aren't an allowed NDS value (only checkable when NG is known) → will be snapped.
        if (!nds?.nutzergruppe.isNullOrBlank()) {
            val offCount = activities.count { act ->
                val typ = typForActivity(act)
                val allowed = NdsRules.allowedDurations(nds?.nutzergruppe, typ)
                allowed != null && allowed.isNotEmpty() && durationMinutes(act) !in allowed
            }
            if (offCount > 0) {
                issues.add(
                    NdsPreflightIssue(
                        "warning", "duration_snapped",
                        "$offCount Aktivität(en) mit unzulässiger Dauer werden auf den nächsten erlaubten Wert gerundet."
                    )
                )
            }
        }

        val ok = issues.none { it.severity == "error" }
        return NdsPreflightReport(ok = ok, issues = issues)
    }

    suspend fun buildCsvs(teamId: UUID): NdsCsvBundle {
        val nds = ndsRepository.getTeamNds(teamId)
        val activities = ndsRepository.listExportActivities(teamId)
            .sortedBy { it.startAt }
        val rowByEvent = LinkedHashMap<UUID, AktivitaetRow>()
        for (act in activities) rowByEvent[act.eventId] = buildRow(act, nds?.nutzergruppe)

        val aktivitaetenCsv = NdsCsvWriter.aktivitaeten(rowByEvent.values.toList())

        val attendances = ndsRepository.listExportAttendances(teamId)
            .filter { !it.personNumber.isNullOrBlank() }
            .sortedWith(compareBy({ it.personNumber }, { rowByEvent[it.eventId]?.date }))
        val awkCsv = NdsCsvWriter.awk(attendances, rowByEvent)

        return NdsCsvBundle(aktivitaetenCsv, awkCsv)
    }

    private fun buildRow(act: ExportActivity, nutzergruppe: String?): AktivitaetRow {
        val typ = typForActivity(act)
        val date = act.startAt.atZone(ZoneOffset.UTC).toLocalDate()
        val time = act.startAt.atZone(ZoneOffset.UTC).toLocalTime()
        val withTimeLoc = NdsRules.requiresTimeAndLocation(typ)
        val dauer = NdsRules.snapDuration(nutzergruppe, typ, durationMinutes(act))
        return AktivitaetRow(
            typ = typ,
            date = date,
            zeit = if (withTimeLoc) time else null,
            dauerMin = dauer,
            ort = if (withTimeLoc) act.location else null
        )
    }

    private fun typForActivity(act: ExportActivity): String =
        act.ndsSymbol?.let { NdsRules.symbolToAktivitaetstyp(it) }
            ?: NdsRules.eventTypeToAktivitaetstyp(act.eventType)

    private fun durationMinutes(act: ExportActivity): Int =
        Duration.between(act.startAt, act.endAt).toMinutes().toInt()
}

/** Emits NDS-format CSV: `;` delimiter, CRLF, UTF-8 BOM, exact fixed headers. */
object NdsCsvWriter {
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val BOM = "\uFEFF"
    private const val SEP = ";"
    private const val EOL = "\r\n"

    fun aktivitaeten(rows: List<AktivitaetRow>): String {
        val sb = StringBuilder(BOM)
        sb.append(listOf("AKTIVITAETSTYP", "DATUM", "ZEIT", "DAUER", "ORT", "FOKUS").joinToString(SEP)).append(EOL)
        for (r in rows) {
            sb.append(
                listOf(
                    r.typ,
                    r.date.format(DATE_FMT),
                    r.zeit?.format(TIME_FMT) ?: "",
                    r.dauerMin?.toString() ?: "",
                    r.ort ?: "",
                    "" // FOKUS — not modelled yet
                ).joinToString(SEP) { escape(it) }
            ).append(EOL)
        }
        return sb.toString()
    }

    fun awk(
        attendances: List<ch.teamorg.domain.repositories.ExportAttendance>,
        rowByEvent: Map<UUID, AktivitaetRow>
    ): String {
        val sb = StringBuilder(BOM)
        sb.append(
            listOf("PERSONENNUMMER", "FUNKTION", "DATUM", "AKTIVITAETSTYP", "ZEIT", "DAUER", "ORT").joinToString(SEP)
        ).append(EOL)
        for (a in attendances) {
            val row = rowByEvent[a.eventId] ?: continue
            sb.append(
                listOf(
                    a.personNumber ?: "",
                    a.funktion,
                    row.date.format(DATE_FMT),
                    row.typ,
                    row.zeit?.format(TIME_FMT) ?: "",
                    row.dauerMin?.toString() ?: "",
                    row.ort ?: ""
                ).joinToString(SEP) { escape(it) }
            ).append(EOL)
        }
        return sb.toString()
    }

    /** RFC-4180-ish quoting for `;`-separated values. */
    private fun escape(value: String): String {
        if (value.isEmpty()) return value
        val needsQuote = value.contains(SEP) || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
