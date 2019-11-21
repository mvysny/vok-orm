package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectList
import com.github.mvysny.dynatest.expectThrows
import org.jdbi.v3.core.Handle
import java.io.IOException
import kotlin.test.expect

/**
 * Tests the `db{}` method whether it manages transactions properly.
 */
class DBTest : DynaTest({
    withAllDatabases {
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
                        Person(name = "foo", age = 25).apply { save(); modified = modified!!.withZeroNanos }
                    }
                }
            }
            expect(listOf(person)) { db { Person.findAll() } }
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
})
