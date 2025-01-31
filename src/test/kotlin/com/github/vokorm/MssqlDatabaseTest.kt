package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import org.junit.jupiter.api.*
import org.testcontainers.containers.MSSQLServerContainer
import kotlin.test.expect

class MssqlDatabaseTest {
    companion object {
        private lateinit var container: MSSQLServerContainer<*>
        @BeforeAll @JvmStatic
        fun setup() {
            assumeDockerAvailable()
            Assumptions.assumeTrue(isX86_64) { "MSSQL is only available on amd64: https://hub.docker.com/_/microsoft-mssql-server/ " }
            Assumptions.assumeTrue(false) { "MSSQL tests fail to run on Github; don't know why, don't care" }

            container =
                MSSQLServerContainer("mcr.microsoft.com/mssql/server:${DatabaseVersions.mssql}")
            container.start()
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

        @AfterAll @JvmStatic
        fun teardown() {
            JdbiOrm.destroy()
            if (this::container.isInitialized) {
                container.stop()
            }
        }
    }

    @BeforeEach @AfterEach fun purgeDb() { clearDb() }

    @Test fun `expect MSSQL variant`() {
        expect(DatabaseVariant.MSSQL) {
            db {
                DatabaseVariant.from(handle)
            }
        }
    }

    // unfortunately the default Docker image doesn't support the FULLTEXT index:
    // https://stackoverflow.com/questions/60489784/installing-mssql-server-express-using-docker-with-full-text-search-support
    @Nested
    inner class AllDatabaseTests : AbstractDatabaseTests(DatabaseInfo(DatabaseVariant.MSSQL, supportsFullText = false))
}
