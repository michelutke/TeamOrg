package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

interface UserRepository {
    fun findByEmail(email: String): User?
    fun findById(id: UUID): User?
    fun create(email: String, passwordHash: String, displayName: String): User
    fun existsByEmail(email: String): Boolean
    fun getPasswordHash(email: String): String?
    fun updateAvatarUrl(userId: UUID, avatarUrl: String?): User
    fun findAll(page: Int, pageSize: Int): List<User>
    fun countAll(): Long
    fun searchByNameOrEmail(query: String): List<User>
}

class UserRepositoryImpl : UserRepository {
    override fun findByEmail(email: String): User? = transaction {
        UsersTable.selectAll().where { UsersTable.email eq email }
            .map(::rowToUser)
            .singleOrNull()
    }

    override fun findById(id: UUID): User? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq id }
            .map(::rowToUser)
            .singleOrNull()
    }

    override fun create(email: String, passwordHash: String, displayName: String): User = transaction {
        val insertedId = UsersTable.insert {
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.displayName] = displayName
        } get UsersTable.id

        User(
            id = insertedId.toString(),
            email = email,
            displayName = displayName,
            avatarUrl = null,
            isSuperAdmin = false
        )
    }

    override fun existsByEmail(email: String): Boolean = transaction {
        !UsersTable.selectAll().where { UsersTable.email eq email }.empty()
    }

    override fun getPasswordHash(email: String): String? = transaction {
        UsersTable.select(UsersTable.passwordHash).where { UsersTable.email eq email }
            .map { it[UsersTable.passwordHash] }
            .singleOrNull()
    }

    override fun updateAvatarUrl(userId: UUID, avatarUrl: String?): User = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.avatarUrl] = avatarUrl
        }
        UsersTable.selectAll().where { UsersTable.id eq userId }
            .map(::rowToUser)
            .single()
    }

    override fun findAll(page: Int, pageSize: Int): List<User> = transaction {
        UsersTable.selectAll()
            .orderBy(UsersTable.createdAt, SortOrder.DESC)
            .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
            .map(::rowToUser)
    }

    override fun countAll(): Long = transaction {
        UsersTable.selectAll().count()
    }

    override fun searchByNameOrEmail(query: String): List<User> = transaction {
        val lowerQuery = "%${query.lowercase()}%"
        UsersTable.selectAll()
            .where {
                (UsersTable.displayName.lowerCase() like lowerQuery) or
                (UsersTable.email.lowerCase() like lowerQuery)
            }
            .orderBy(UsersTable.createdAt, SortOrder.DESC)
            .map(::rowToUser)
    }

    private fun rowToUser(row: ResultRow) = User(
        id = row[UsersTable.id].toString(),
        email = row[UsersTable.email],
        displayName = row[UsersTable.displayName],
        avatarUrl = row[UsersTable.avatarUrl],
        isSuperAdmin = row[UsersTable.isSuperAdmin]
    )
}
