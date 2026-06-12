package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.AuditLogTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

interface AuditLogRepository {
    fun log(
        actorId: UUID,
        actorEmail: String,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        details: String? = null,
        impersonationContext: String? = null
    )
    fun query(
        actionFilter: String? = null,
        actorFilter: String? = null,
        startDate: Instant? = null,
        endDate: Instant? = null,
        page: Int = 1,
        pageSize: Int = 50
    ): AuditLogPage
    fun count(
        actionFilter: String? = null,
        actorFilter: String? = null,
        startDate: Instant? = null,
        endDate: Instant? = null
    ): Long
}

@Serializable
data class AuditLogEntry(
    val id: String,
    val actorId: String,
    val actorEmail: String,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val details: String?,
    val impersonationContext: String?,
    val createdAt: String
)

@Serializable
data class AuditLogPage(
    val entries: List<AuditLogEntry>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
    val totalPages: Int
)

class AuditLogRepositoryImpl : AuditLogRepository {
    override fun log(
        actorId: UUID,
        actorEmail: String,
        action: String,
        targetType: String?,
        targetId: String?,
        details: String?,
        impersonationContext: String?
    ): Unit = transaction {
        AuditLogTable.insert {
            it[AuditLogTable.actorId] = actorId
            it[AuditLogTable.actorEmail] = actorEmail
            it[AuditLogTable.action] = action
            it[AuditLogTable.targetType] = targetType
            it[AuditLogTable.targetId] = targetId
            it[AuditLogTable.details] = details
            it[AuditLogTable.impersonationContext] = impersonationContext
        }
    }

    override fun query(
        actionFilter: String?,
        actorFilter: String?,
        startDate: Instant?,
        endDate: Instant?,
        page: Int,
        pageSize: Int
    ): AuditLogPage {
        val total = count(actionFilter, actorFilter, startDate, endDate)
        val totalPages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 1
        val entries = transaction {
            AuditLogTable.selectAll()
                .applyFilters(actionFilter, actorFilter, startDate, endDate)
                .orderBy(AuditLogTable.createdAt, SortOrder.DESC)
                .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
                .map(::rowToEntry)
        }
        return AuditLogPage(entries, page, pageSize, total, totalPages)
    }

    override fun count(
        actionFilter: String?,
        actorFilter: String?,
        startDate: Instant?,
        endDate: Instant?
    ): Long = transaction {
        AuditLogTable.selectAll()
            .applyFilters(actionFilter, actorFilter, startDate, endDate)
            .count()
    }

    private fun Query.applyFilters(
        actionFilter: String?,
        actorFilter: String?,
        startDate: Instant?,
        endDate: Instant?
    ): Query {
        if (actionFilter != null) andWhere { AuditLogTable.action eq actionFilter }
        if (actorFilter != null) andWhere { AuditLogTable.actorEmail.lowerCase() like "%${actorFilter.lowercase()}%" }
        if (startDate != null) andWhere { AuditLogTable.createdAt greaterEq java.time.Instant.ofEpochMilli(startDate.toEpochMilli()) }
        if (endDate != null) andWhere { AuditLogTable.createdAt lessEq java.time.Instant.ofEpochMilli(endDate.toEpochMilli()) }
        return this
    }

    private fun rowToEntry(row: ResultRow) = AuditLogEntry(
        id = row[AuditLogTable.id].toString(),
        actorId = row[AuditLogTable.actorId].toString(),
        actorEmail = row[AuditLogTable.actorEmail],
        action = row[AuditLogTable.action],
        targetType = row[AuditLogTable.targetType],
        targetId = row[AuditLogTable.targetId],
        details = row[AuditLogTable.details],
        impersonationContext = row[AuditLogTable.impersonationContext],
        createdAt = row[AuditLogTable.createdAt].toString()
    )
}
