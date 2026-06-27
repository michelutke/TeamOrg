package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object NdsMembersTable : Table("nds_members") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val teamId = uuid("team_id").references(TeamsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val lastName = text("last_name")
    val firstName = text("first_name")
    val birthDate = date("birth_date").nullable()
    val personNumber = text("person_number").nullable()
    val funktion = text("funktion") // 'Teilnehmer/in' | 'Leiter/in'
    val sourceKind = text("source").default("nds_import")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("nds_members_team_id_last_name_first_name_birth_date_key", teamId, lastName, firstName, birthDate)
    }
}
