package com.github.vokorm

import com.github.mvysny.dynatest.DynaNodeGroup
import com.github.mvysny.dynatest.DynaTestDsl
import com.gitlab.mvysny.jdbiorm.*
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.intellij.lang.annotations.Language
import org.jdbi.v3.core.mapper.reflect.ColumnName
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.CockroachContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
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

@DynaTestDsl
private fun DynaNodeGroup.usingDockerizedPosgresql() {
    check(DockerClientFactory.instance().isDockerAvailable()) { "Docker not available" }
    lateinit var container: PostgreSQLContainer<*>
    beforeGroup {
        container = PostgreSQLContainer("postgres:${DatabaseVersions.postgres}")
        container.start()
    }
    beforeGroup {
        hikari {
            minimumIdle = 0
            maximumPoolSize = 30
            // stringtype=unspecified : see https://github.com/mvysny/vok-orm/issues/12 for more details.
            jdbcUrl = container.jdbcUrl.removeSuffix("loggerLevel=OFF") + "stringtype=unspecified"
            username = container.username
            password = container.password
        }
        db {
            ddl("""create table if not exists Test (
                id bigserial primary key,
                name varchar(400) not null,
                age integer not null,
                dateOfBirth date,
                created timestamp,
                modified timestamp,
                alive boolean,
                maritalStatus varchar(200)
                 )""")
            ddl("""CREATE INDEX pgweb_idx ON Test USING GIN (to_tsvector('english', name));""")
            ddl("""create table if not exists EntityWithAliasedId(myid bigserial primary key, name varchar(400) not null)""")
            ddl("""create table if not exists NaturalPerson(id varchar(10) primary key, name varchar(400) not null, bytes bytea not null)""")
            ddl("""create table if not exists LogRecord(id UUID primary key, text varchar(400) not null)""")
            ddl("""CREATE TYPE marital_status AS ENUM ('Single', 'Married', 'Widowed', 'Divorced')""")
            ddl("""CREATE TABLE IF NOT EXISTS TypeMappingEntity(id bigserial primary key, enumTest marital_status)""")
        }
    }

    afterGroup { JdbiOrm.destroy() }
    afterGroup { container.stop() }

    beforeEach { clearDb() }
    afterEach { clearDb() }
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
