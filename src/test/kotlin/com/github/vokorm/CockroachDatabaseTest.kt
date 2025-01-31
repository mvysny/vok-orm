package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import org.junit.jupiter.api.*
import org.testcontainers.containers.CockroachContainer
import kotlin.test.expect

class CockroachDatabaseTest {
    companion object {
        private lateinit var container: CockroachContainer
        @BeforeAll
        @JvmStatic
        fun setup() {
            assumeDockerAvailable()

            container =
                CockroachContainer("cockroachdb/cockroach:${DatabaseVersions.cockroach}")
            container.start()

            hikari {
                minimumIdle = 0
                maximumPoolSize = 30
                jdbcUrl = container.jdbcUrl
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
                // full-text search not yet supported: https://github.com/cockroachdb/cockroach/issues/41288
//            ddl("""CREATE INDEX pgweb_idx ON Test USING GIN (to_tsvector('english', name));""")
                ddl("""create table if not exists EntityWithAliasedId(myid bigserial primary key, name varchar(400) not null)""")
                ddl("""create table if not exists NaturalPerson(id varchar(10) primary key, name varchar(400) not null, bytes bytea not null)""")
                ddl("""create table if not exists LogRecord(id UUID primary key, text varchar(400) not null)""")
                ddl("""CREATE TYPE marital_status AS ENUM ('Single', 'Married', 'Widowed', 'Divorced')""")
                ddl("""CREATE TABLE IF NOT EXISTS TypeMappingEntity(id bigserial primary key, enumTest marital_status)""")
            }
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            JdbiOrm.destroy()
            if (::container.isInitialized) {
                container.stop()
            }
        }
    }

    @BeforeEach @AfterEach fun purgeDb() { clearDb() }

    @Test fun `expect PostgreSQL variant`() {
        expect(DatabaseVariant.PostgreSQL) {
            db {
                DatabaseVariant.from(handle)
            }
        }
    }
    // full-text search not yet supported: https://github.com/cockroachdb/cockroach/issues/41288
    @Nested
    inner class AllDatabaseTests : AbstractDatabaseTests(DatabaseInfo(DatabaseVariant.PostgreSQL, supportsFullText = false))
}
