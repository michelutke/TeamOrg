package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.PasswordResetTokensTable
import ch.teamorg.db.tables.UsersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/** User whose password was just reset; carried back for the audit entry. */
data class PasswordResetResult(val userId: UUID, val email: String)

interface PasswordResetRepository {
    /** Stores the token hash and invalidates every older unused token of the user. */
    fun createToken(userId: UUID, tokenHash: String, expiresAt: Instant, now: Instant)

    /**
     * Consumes a valid (known, unused, unexpired) token, sets the new password hash and bumps
     * the user's token version so all existing JWTs stop working. Null if the token is invalid.
     */
    fun consumeAndResetPassword(tokenHash: String, newPasswordHash: String, now: Instant): PasswordResetResult?
}

class PasswordResetRepositoryImpl : PasswordResetRepository {
    override fun createToken(userId: UUID, tokenHash: String, expiresAt: Instant, now: Instant): Unit = transaction {
        PasswordResetTokensTable.update({
            (PasswordResetTokensTable.userId eq userId) and PasswordResetTokensTable.usedAt.isNull()
        }) {
            it[usedAt] = now
        }
        PasswordResetTokensTable.insert {
            it[PasswordResetTokensTable.userId] = userId
            it[PasswordResetTokensTable.tokenHash] = tokenHash
            it[PasswordResetTokensTable.expiresAt] = expiresAt
        }
    }

    override fun consumeAndResetPassword(
        tokenHash: String,
        newPasswordHash: String,
        now: Instant
    ): PasswordResetResult? = transaction {
        // The conditional update is the atomic claim: of two concurrent requests with the same
        // token only one sees a row count of 1.
        val claimed = PasswordResetTokensTable.update({
            (PasswordResetTokensTable.tokenHash eq tokenHash) and
                PasswordResetTokensTable.usedAt.isNull() and
                (PasswordResetTokensTable.expiresAt greater now)
        }) {
            it[usedAt] = now
        }
        if (claimed != 1) return@transaction null

        val userId = PasswordResetTokensTable.select(PasswordResetTokensTable.userId)
            .where { PasswordResetTokensTable.tokenHash eq tokenHash }
            .single()[PasswordResetTokensTable.userId]
        val email = UsersTable.select(UsersTable.email, UsersTable.deletedAt)
            .where { UsersTable.id eq userId }
            .single()
            .let { row -> if (row[UsersTable.deletedAt] != null) return@transaction null else row[UsersTable.email] }

        UsersTable.update({ UsersTable.id eq userId }) {
            it[passwordHash] = newPasswordHash
            it[tokenVersion] = tokenVersion + 1
        }
        PasswordResetResult(userId, email)
    }
}
