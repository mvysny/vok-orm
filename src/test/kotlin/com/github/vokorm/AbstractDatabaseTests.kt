package com.github.vokorm

import org.jdbi.v3.core.Handle
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.test.expect

abstract class AbstractDatabaseTests(val info: DatabaseInfo) {
    @Nested inner class DbFunTests : AbstractDbFunTests()
    @Nested inner class MappingTests : AbstractDbMappingTests()
}

/**
 * Tests the `db{}` method whether it manages transactions properly.
 */
abstract class AbstractDbFunTests() {
    @Test fun verifyEntityManagerClosed() {
        val em: Handle = db { handle }
        expect(true) { em.connection.isClosed }
    }
    @Test fun exceptionRollsBack() {
        assertThrows<IOException> {
            db {
                Person(name = "foo", age = 25).save()
                expect(listOf(25)) { db { Person.findAll().map { it.age } } }
                throw IOException("simulated")
            }
        }
        expect(listOf()) { db { Person.findAll() } }
    }
    @Test fun commitInNestedDbBlocks() {
        val person = db {
            db {
                db {
                    Person(name = "foo", age = 25).apply { save() }
                }
            }
        }
        expect(listOf(person)) { db { Person.findAll() } }
    }
    @Test fun exceptionRollsBackInNestedDbBlocks() {
        assertThrows<IOException> {
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
