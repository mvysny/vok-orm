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
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.util.*

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
        container = PostgreSQLContainer("postgres:10.3")
        container.start()
    }
    beforeGroup {
        hikari {
            minimumIdle = 0
            maximumPoolSize = 30
            // stringtype=unspecified : see https://github.com/mvysny/vok-orm/issues/12 for more details.
            jdbcUrl = container.jdbcUrl.removeSuffix("loggerLevel=OFF") + "stringtype=unspecified"
            username = "test"
            password = "test"
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

@DynaTestDsl
fun DynaNodeGroup.usingDockerizedMysql() {
    check(DockerClientFactory.instance().isDockerAvailable()) { "Docker not available" }
    lateinit var container: MySQLContainer<*>
    beforeGroup {
        container = MySQLContainer("mysql:8.0.31")
        // disable SSL, to avoid SSL-related exceptions on github actions:
        // javax.net.ssl.SSLHandshakeException: No appropriate protocol (protocol is disabled or cipher suites are inappropriate)
        container.withUrlParam("useSSL", "false")
        container.start()
    }
    beforeGroup {
        hikari {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = container.jdbcUrl
            username = "test"
            password = "test"
        }
        db {
            ddl("""create table if not exists Test (
                id bigint primary key auto_increment,
                name varchar(400) not null,
                age integer not null,
                dateOfBirth date,
                created timestamp(3) NULL,
                modified timestamp(3) NULL,
                alive boolean,
                maritalStatus varchar(200),
                FULLTEXT index (name)
                 )""")
            ddl("""create table if not exists EntityWithAliasedId(myid bigint primary key auto_increment, name varchar(400) not null)""")
            ddl("""create table if not exists NaturalPerson(id varchar(10) primary key, name varchar(400) not null, bytes binary(16) not null)""")
            ddl("""create table if not exists LogRecord(id binary(16) primary key, text varchar(400) not null)""")
            ddl("""create table TypeMappingEntity(id bigint primary key auto_increment, enumTest ENUM('Single', 'Married', 'Divorced', 'Widowed'))""")
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

@DynaTestDsl
fun DynaNodeGroup.usingH2Database() {
    beforeGroup {
        hikari {
            jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
        }
    }

    afterGroup { JdbiOrm.destroy() }

    beforeEach {
        db {
            ddl("DROP ALL OBJECTS")
            ddl("""CREATE ALIAS IF NOT EXISTS FTL_INIT FOR "org.h2.fulltext.FullTextLucene.init";CALL FTL_INIT();""")
            ddl("""create table Test (
                id bigint primary key auto_increment,
                name varchar not null,
                age integer not null,
                dateOfBirth date,
                created timestamp,
                modified timestamp,
                alive boolean,
                maritalStatus varchar
                 )""")
            ddl("""create table EntityWithAliasedId(myid bigint primary key auto_increment, name varchar not null)""")
            ddl("""create table NaturalPerson(id varchar(10) primary key, name varchar(400) not null, bytes binary(16) not null)""")
            ddl("""create table LogRecord(id UUID primary key, text varchar(400) not null)""")
            ddl("""create table TypeMappingEntity(id bigint primary key auto_increment, enumTest ENUM('Single', 'Married', 'Divorced', 'Widowed'))""")
            ddl("""CALL FTL_CREATE_INDEX('PUBLIC', 'TEST', 'NAME');""")
        }
    }
    afterEach {
        db { ddl("DROP ALL OBJECTS") }
    }
}

fun PersistenceContext.ddl(@Language("sql") sql: String) {
    handle.createUpdate(sql).execute()
}

@DynaTestDsl
private fun DynaNodeGroup.usingDockerizedMariaDB() {
    check(DockerClientFactory.instance().isDockerAvailable()) { "Docker not available" }
    lateinit var container: MariaDBContainer<*>
    beforeGroup {
        container = MariaDBContainer("mariadb:10.10.2")
        container.start()
    }
    beforeGroup {
        hikari {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = container.jdbcUrl
            username = "test"
            password = "test"
        }
        db {
            ddl(
                """create table if not exists Test (
                id bigint primary key auto_increment,
                name varchar(400) not null,
                age integer not null,
                dateOfBirth date,
                created timestamp(3) NULL,
                modified timestamp(3) NULL,
                alive boolean,
                maritalStatus varchar(200),
                FULLTEXT index (name)
                 )"""
            )
            ddl("""create table if not exists EntityWithAliasedId(myid bigint primary key auto_increment, name varchar(400) not null)""")
            ddl("""create table if not exists NaturalPerson(id varchar(10) primary key, name varchar(400) not null, bytes binary(16) not null)""")
            ddl("""create table if not exists LogRecord(id binary(16) primary key, text varchar(400) not null)""")
            ddl("""create table TypeMappingEntity(id bigint primary key auto_increment, enumTest ENUM('Single', 'Married', 'Divorced', 'Widowed'))""")
        }
    }

    afterGroup { JdbiOrm.destroy() }
    afterGroup { container.stop() }

    beforeEach { clearDb() }
    afterEach { clearDb() }
}

private fun clearDb() {
    Person.deleteAll()
    EntityWithAliasedId.deleteAll()
    NaturalPerson.deleteAll()
    LogRecord.deleteAll()
    TypeMappingEntity.deleteAll()
}

@DynaTestDsl
private fun DynaNodeGroup.usingDockerizedMSSQL() {
    check(DockerClientFactory.instance().isDockerAvailable()) { "Docker not available" }
    lateinit var container: MSSQLServerContainer<*>
    beforeGroup {
        container = MSSQLServerContainer("mcr.microsoft.com/mssql/server:2017-latest-ubuntu")
        container.start()
    }
    beforeGroup {
        hikari {
            minimumIdle = 0
            maximumPoolSize = 30
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
        }
        db {
            // otherwise the CREATE FULLTEXT CATALOG would fail: Cannot use full-text search in master, tempdb, or model database.
            ddl("CREATE DATABASE foo")
            ddl("USE foo")
            ddl("CREATE FULLTEXT CATALOG AdvWksDocFTCat")

            ddl(
                    """create table Test (
                id bigint primary key IDENTITY(1,1) not null,
                name varchar(400) not null,
                age integer not null,
                dateOfBirth datetime,
                created datetime NULL,
                modified datetime NULL,
                alive bit,
                maritalStatus varchar(200)
                 )"""
            )
            // unfortunately the default Docker image doesn't support the FULLTEXT index:
            // https://stackoverflow.com/questions/60489784/installing-mssql-server-express-using-docker-with-full-text-search-support
            // just skip the tests for now
/*
            ddl("CREATE UNIQUE INDEX ui_ukDoc ON Test(name);")
            ddl("""CREATE FULLTEXT INDEX ON Test
(  
    Test                         --Full-text index column name   
        TYPE COLUMN name    --Name of column that contains file type information  
        Language 2057                 --2057 is the LCID for British English  
)  
KEY INDEX ui_ukDoc ON AdvWksDocFTCat --Unique index  
WITH CHANGE_TRACKING AUTO            --Population type;  """)
*/

            ddl("""create table EntityWithAliasedId(myid bigint primary key IDENTITY(1,1) not null, name varchar(400) not null)""")
            ddl("""create table NaturalPerson(id varchar(10) primary key not null, name varchar(400) not null, bytes binary(16) not null)""")
            ddl("""create table LogRecord(id uniqueidentifier primary key not null, text varchar(400) not null)""")
            ddl("""create table TypeMappingEntity(id bigint primary key IDENTITY(1,1) not null, enumTest varchar(10))""")
        }
    }

    afterGroup { JdbiOrm.destroy() }
    afterGroup { container.stop() }

    beforeEach { clearDb() }
    afterEach { clearDb() }
}

@DynaTestDsl
fun DynaNodeGroup.withAllDatabases(block: DynaNodeGroup.(DatabaseInfo)->Unit) {
    group("H2") {
        usingH2Database()
        block(DatabaseInfo(DatabaseVariant.H2))
    }

    if (System.getProperty("h2only").toBoolean()) {
        println("`h2only` system property specified, skipping PostgreSQL/MySQL/MariaDB/MSSQL tests")
    } else if (!DockerClientFactory.instance().isDockerAvailable) {
        println("Docker is not available, not running PostgreSQL/MySQL/MariaDB/MSSQL tests")
    } else {
        println("Docker is available, running PostgreSQL/MySQL/MariaDB tests")
        group("PostgreSQL 10.3") {
            usingDockerizedPosgresql()
            block(DatabaseInfo(DatabaseVariant.PostgreSQL))
        }

        group("MySQL 8.0.25") {
            usingDockerizedMysql()
            block(DatabaseInfo(DatabaseVariant.MySQLMariaDB))
        }

        group("MariaDB 10.1.31") {
            usingDockerizedMariaDB()
            block(DatabaseInfo(DatabaseVariant.MySQLMariaDB))
        }

        group("MSSQL 2017 Express") {
            usingDockerizedMSSQL()
            block(DatabaseInfo(DatabaseVariant.MSSQL))
        }
    }
}

// unfortunately the default Docker image doesn't support the FULLTEXT index:
// https://stackoverflow.com/questions/60489784/installing-mssql-server-express-using-docker-with-full-text-search-support
val DatabaseVariant.supportsFullText: Boolean get() = this != DatabaseVariant.MSSQL

data class DatabaseInfo(val variant: DatabaseVariant)
