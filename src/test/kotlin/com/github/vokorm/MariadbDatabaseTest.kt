package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import org.junit.jupiter.api.*
import org.testcontainers.containers.MariaDBContainer
import kotlin.test.expect

class MariaDBDatabaseTest {
    companion object {
        private lateinit var container: MariaDBContainer<*>
        @BeforeAll @JvmStatic
        fun setup() {
            Assumptions.assumeTrue(!h2only) { "Only H2 tests are running now" }
            assumeDockerAvailable()

            container = MariaDBContainer("mariadb:${DatabaseVersions.mariadb}")
            container.start()

            hikari {
                minimumIdle = 0
                maximumPoolSize = 30
                jdbcUrl = container.jdbcUrl
                username = container.username
                password = container.password
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

        @AfterAll @JvmStatic
        fun tearDown() {
            JdbiOrm.destroy()
            if (this::container.isInitialized) {
                container.stop()
            }
        }
    }

    @BeforeEach @AfterEach fun purgeDb() { clearDb() }

    @Test fun `expect MySQL variant`() {
        expect(DatabaseVariant.MySQLMariaDB) { db { DatabaseVariant.from(handle) } }
    }

    @Nested
    inner class AllDatabaseTests : AbstractDatabaseTests(DatabaseInfo(DatabaseVariant.MySQLMariaDB))
}
