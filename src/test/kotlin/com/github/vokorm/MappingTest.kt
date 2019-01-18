package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectList
import com.github.mvysny.dynatest.expectThrows
import com.google.gson.Gson
import java.lang.IllegalStateException
import java.lang.Long
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.expect

class MappingTest : DynaTest({
    withAllDatabases {
        test("FindAll") {
            expectList() { Person.findAll() }
            val p = Person(name = "Zaphod", age = 42, ignored2 = Object())
            p.save()
            expect(true) { p.id != null }
            p.ignored2 = null
            expectList(p) { Person.findAll() }
        }
        group("Person") {
            group("save") {
                test("Save") {
                    val p = Person(name = "Albedo", age = 130)
                    p.save()
                    expectList("Albedo") { Person.findAll().map { it.name } }
                    p.name = "Rubedo"
                    p.save()
                    expectList("Rubedo") { Person.findAll().map { it.name } }
                    Person(name = "Nigredo", age = 130).save()
                    expectList("Rubedo", "Nigredo") { Person.findAll().map { it.name } }
                }
                test("SaveEnum") {
                    val p = Person(name = "Zaphod", age = 42, maritalStatus = MaritalStatus.Divorced)
                    p.save()
                    class Foo(var maritalStatus: String? = null)
                    expectList("Divorced") {
                        db {
                            con.createQuery("select maritalStatus from Test").executeAndFetch<Foo>(Foo::class.java).map { it.maritalStatus }
                        }
                    }
                    expect(p) { db { com.github.vokorm.Person.findAll()[0] } }
                }
                test("SaveLocalDate") {
                    val p = Person(name = "Zaphod", age = 42, dateOfBirth = LocalDate.of(1990, 1, 14))
                    p.save()
                    expect(LocalDate.of(1990, 1, 14)) { db { com.github.vokorm.Person.findAll()[0].dateOfBirth!! } }
                }
                test("save date and instant") {
                    val p = Person(name = "Zaphod", age = 20, created = Date(1000), modified = Instant.ofEpochMilli(120398123))
                    p.save()
                    expect(1000) { db { com.github.vokorm.Person.findAll()[0].created!!.time } }
                    expect(Instant.ofEpochMilli(120398123)) { db { com.github.vokorm.Person.findAll()[0].modified!! } }
                }
                test("updating non-existing row fails") {
                    val p = Person(id = 15, name = "Zaphod", age = 20, created = Date(1000), modified = Instant.ofEpochMilli(120398123))
                    expectThrows(IllegalStateException::class, "We expected to update only one row but we updated 0 - perhaps there is no row with id 15?") {
                        p.save()
                    }
                }
            }
            test("delete") {
                val p = Person(name = "Albedo", age = 130)
                p.save()
                p.delete()
                expectList() { Person.findAll() }
            }
            test("JsonSerializationIgnoresMeta") {
                expect("""{"name":"Zaphod","age":42}""") { Gson().toJson(Person(name = "Zaphod", age = 42)) }
            }
            test("Meta") {
                val meta = Person.meta
                expect("Test") { meta.databaseTableName }  // since Person is annotated with @Entity("Test")
                expect("id") { meta.idProperty.dbColumnName }
                expect(Person::class.java) { meta.entityClass }
                expect(Long::class.java) { meta.idProperty.valueType }
                expect(
                        setOf(
                                "id",
                                "name",
                                "age",
                                "dateOfBirth",
                                "created",
                                "alive",
                                "maritalStatus",
                                "modified"
                        )
                ) { meta.persistedFieldDbNames }
            }
        }
        group("EntityWithAliasedId") {
            test("Save") {
                val p = EntityWithAliasedId(name = "Albedo")
                p.save()
                expectList("Albedo") { EntityWithAliasedId.findAll().map { it.name } }
                p.name = "Rubedo"
                p.save()
                expectList("Rubedo") { EntityWithAliasedId.findAll().map { it.name } }
                EntityWithAliasedId(name = "Nigredo").save()
                expectList("Rubedo", "Nigredo") { EntityWithAliasedId.findAll().map { it.name } }
            }
            test("delete") {
                val p = EntityWithAliasedId(name = "Albedo")
                p.save()
                p.delete()
                expect(listOf()) { EntityWithAliasedId.findAll() }
            }
            test("JsonSerializationIgnoresMeta") {
                expect("""{"name":"Zaphod"}""") { Gson().toJson(EntityWithAliasedId(name = "Zaphod")) }
            }
            test("Meta") {
                val meta = EntityWithAliasedId.meta
                expect("EntityWithAliasedId") { meta.databaseTableName }
                expect("myid") { meta.idProperty.dbColumnName }
                expect(EntityWithAliasedId::class.java) { meta.entityClass }
                expect(Long::class.java) { meta.idProperty.valueType }
                expect(setOf("myid", "name")) { meta.persistedFieldDbNames }
            }
        }
        group("NaturalPerson") {
            test("save fails") {
                val p = NaturalPerson(id = "12345678", name = "Albedo")
                expectThrows(IllegalStateException::class, message = "We expected to update only one row but we updated 0 - perhaps there is no row with id 12345678?") {
                    p.save()
                }
            }
            test("Save") {
                val p = NaturalPerson(id = "12345678", name = "Albedo")
                p.create()
                expectList("Albedo") { NaturalPerson.findAll().map { it.name } }
                p.name = "Rubedo"
                p.save()
                expectList("Rubedo") { NaturalPerson.findAll().map { it.name } }
                NaturalPerson(id = "aaa", name = "Nigredo").create()
                expectList("Rubedo", "Nigredo") { NaturalPerson.findAll().map { it.name } }
            }
            test("delete") {
                val p = NaturalPerson(id = "foo", name = "Albedo")
                p.create()
                p.delete()
                expectList() { NaturalPerson.findAll() }
            }
        }
        group("LogRecord") {
            test("save fails") {
                val p = LogRecord(id = UUID.randomUUID(), text = "foo", bytes = byteArrayOf(5))
                expectThrows(IllegalStateException::class, message = "We expected to update only one row but we updated 0 - perhaps there is no row with id") {
                    p.save()
                }
            }
            test("Save") {
                val p = LogRecord(id = UUID.randomUUID(), text = "Albedo", bytes = byteArrayOf(5))
                p.create()
                expectList("Albedo") { LogRecord.findAll().map { it.text } }
                p.text = "Rubedo"
                p.save()
                expectList("Rubedo") { LogRecord.findAll().map { it.text } }
                LogRecord(id = UUID.randomUUID(), text = "Nigredo", bytes = byteArrayOf(5)).create()
                expect(setOf("Rubedo", "Nigredo")) { LogRecord.findAll().map { it.text } .toSet() }
            }
            test("delete") {
                val p = LogRecord(id = UUID.randomUUID(), text = "foo", bytes = byteArrayOf(5))
                p.create()
                p.delete()
                expectList() { LogRecord.findAll() }
            }
        }
    }
})

