package com.github.vokorm

import com.github.mvysny.dynatest.*
import com.github.vokorm.dataloader.dbSqlDataLoaderTests
import org.jdbi.v3.core.Handle
import java.io.IOException
import kotlin.test.expect

/**
 * Only start/stop docker databases once, to speed up tests dramatically.
 */
class AllDatabaseTests : DynaTest({
    withAllDatabases { info ->
        group("db{}") {
            dbFunTests()
        }
        group("DB Mapping Tests") {
            dbMappingTests()
        }
        group("DAO") {
            dbDaoTests()
        }
        group("Filters") {
            dbFiltersTest(info)
        }
        group("SqlDataLoader") {
            dbSqlDataLoaderTests(info)
        }
    }
})

/**
 * Tests the `db{}` method whether it manages transactions properly.
 */
@DynaTestDsl
fun DynaNodeGroup.dbFunTests() {
    test("verifyEntityManagerClosed") {
        val em: Handle = db { handle }
        expect(true) { em.connection.isClosed }
    }
    test("exceptionRollsBack") {
        expectThrows(IOException::class) {
            db {
                Person(name = "foo", age = 25).save()
                expectList(25) { db { Person.findAll().map { it.age } } }
                throw IOException("simulated")
            }
        }
        expect(listOf()) { db { Person.findAll() } }
    }
    test("commitInNestedDbBlocks") {
        val person = db {
            db {
                db {
                    Person(name = "foo", age = 25).apply { save() }
                }
            }
        }
        expect(listOf(person.withZeroNanos())) { db { Person.findAll().map { it.withZeroNanos() } } }
    }
    test("exceptionRollsBackInNestedDbBlocks") {
        expectThrows(IOException::class) {
            db {
                db {
                    db {
                        Person(name = "foo", age = 25).save()
                        throw IOException("simulated")
                    }
                }
            }
        }
        expect(listOf()) { Person.findAll() }
    }
}
