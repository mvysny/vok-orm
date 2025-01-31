package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.intellij.lang.annotations.Language
import org.jdbi.v3.core.mapper.reflect.ColumnName
import java.util.*

object DatabaseVersions {
    val postgres = "16.3" // https://hub.docker.com/_/postgres/
    val cockroach = "v23.2.6" // https://hub.docker.com/r/cockroachdb/cockroach
    val mysql = "9.0.0" // https://hub.docker.com/_/mysql/
    val mssql = "2017-latest-ubuntu"
    val mariadb = "11.2.4" // https://hub.docker.com/_/mariadb
}

/**
 * Tests for https://github.com/mvysny/vok-orm/issues/7
 */
data class EntityWithAliasedId(
        @field:ColumnName("myid")
        override var id: Long? = null,
        var name: String = ""
) : KEntity<Long> {
    companion object : Dao<EntityWithAliasedId, Long>(EntityWithAliasedId::class.java)
}

/**
 * A table demoing natural person with government-issued ID (birth number, social security number, etc).
 */
data class NaturalPerson(override var id: String? = null, var name: String = "", var bytes: ByteArray = byteArrayOf()) : KEntity<String> {
    companion object : Dao<NaturalPerson, String>(NaturalPerson::class.java)
}

interface UuidEntity : KEntity<UUID> {
    override fun create(validate: Boolean) {
        id = UUID.randomUUID()
        super.create(validate)
    }
}

/**
 * Demoes app-generated UUID ids. Note how [create] is overridden to auto-generate the ID, so that [save] works properly.
 */
data class LogRecord(override var id: UUID? = null, var text: String = "") : UuidEntity {
    companion object : Dao<LogRecord, UUID>(LogRecord::class.java)
}

/**
 * Tests all sorts of type mapping:
 * @property enumTest tests Java Enum mapping to native database enum mapping: https://github.com/mvysny/vok-orm/issues/12
 */
data class TypeMappingEntity(override var id: Long? = null,
                             var enumTest: MaritalStatus? = null
                             ) : KEntity<Long> {
    companion object : Dao<TypeMappingEntity, Long>(TypeMappingEntity::class.java)
}

fun hikari(block: HikariConfig.() -> Unit) {
    JdbiOrm.databaseVariant = null
    JdbiOrm.setDataSource(HikariDataSource(HikariConfig().apply(block)))
}

fun PersistenceContext.ddl(@Language("sql") sql: String) {
    handle.execute(sql)
}

fun clearDb() {
    Person.deleteAll()
    EntityWithAliasedId.deleteAll()
    NaturalPerson.deleteAll()
    LogRecord.deleteAll()
    TypeMappingEntity.deleteAll()
}

data class DatabaseInfo(val variant: DatabaseVariant, val supportsFullText: Boolean = true)
