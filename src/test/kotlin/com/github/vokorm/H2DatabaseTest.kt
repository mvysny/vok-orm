package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import org.junit.jupiter.api.*
import kotlin.test.expect

/**
 * Tests JDBI-ORM on H2.
 */
abstract class AbstractH2DatabaseTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupJdbi() {
            hikari {
                jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
                username = "sa"
                password = ""
            }
        }

        @AfterAll
        @JvmStatic
        fun destroyJdbi() {
            JdbiOrm.destroy()
        }
    }

    @BeforeEach
    fun setupDatabase() {
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
    @AfterEach
    fun dropDatabase() {
        db { ddl("DROP ALL OBJECTS") }
    }

    @Test
    fun expectH2Variant() {
        expect(DatabaseVariant.H2) { db { DatabaseVariant.from(handle) } }
    }
}

class H2DatabaseTest : AbstractH2DatabaseTest() {
    @Nested
    inner class AllDatabaseTests : AbstractDatabaseTests(DatabaseInfo(DatabaseVariant.H2))
}
