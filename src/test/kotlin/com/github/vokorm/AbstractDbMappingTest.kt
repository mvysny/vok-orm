@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.github.vokorm

import org.jdbi.v3.core.mapper.reflect.FieldMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException
import java.lang.Long
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.expect

class Foo(var maritalStatus: String? = null)

abstract class AbstractDbMappingTests() {
    @Test fun FindAll() {
        expectList() { Person.findAll() }
        val p = Person(name = "Zaphod", age = 42, ignored2 = Object())
        p.save()
        expect(true) { p.id != null }
        p.ignored2 = null
        expectList(p) { Person.findAll() }
    }
    @Nested inner class PersonTests {
        @Nested inner class SaveTests {
            @Test fun Save() {
                val p = Person(name = "Albedo", age = 130)
                p.save()
                expectList("Albedo") { Person.findAll().map { it.name } }
                p.name = "Rubedo"
                p.save()
                expectList("Rubedo") { Person.findAll().map { it.name } }
                Person(name = "Nigredo", age = 130).save()
                expectList("Rubedo", "Nigredo") { Person.findAll().map { it.name } }
            }
            @Test fun SaveEnum() {
                val p = Person(name = "Zaphod", age = 42, maritalStatus = MaritalStatus.Divorced)
                p.save()
                expectList("Divorced") {
                    db {
                        handle.createQuery("select maritalStatus from Test").map(FieldMapper.of(Foo::class.java)).list().map { it.maritalStatus }
                    }
                }
                expect(p) { db { Person.findAll()[0] } }
            }
            @Test fun SaveLocalDate() {
                val p = Person(name = "Zaphod", age = 42, dateOfBirth = LocalDate.of(1990, 1, 14))
                p.save()
                expect(LocalDate.of(1990, 1, 14)) { db { Person.findAll()[0].dateOfBirth!! } }
            }
            @Test fun `save date and instant`() {
                val p = Person(name = "Zaphod", age = 20, created = Date(1000), modified = Instant.ofEpochMilli(120398123))
                p.save()
                expect(1000) { db { Person.findAll()[0].created!!.time } }
                expect(Instant.ofEpochMilli(120398123)) { db { Person.findAll()[0].modified!! } }
            }
            @Test fun `updating non-existing row fails`() {
                val p = Person(id = 15, name = "Zaphod", age = 20, created = Date(1000), modified = Instant.ofEpochMilli(120398123))
                expectThrows<IllegalStateException>("We expected to update only one row but we updated 0 - perhaps there is no row with id 15?") {
                    p.save()
                }
            }
        }
        @Test fun delete() {
            val p = Person(name = "Albedo", age = 130)
            p.save()
            p.delete()
            expectList() { Person.findAll() }
        }
        @Test fun JsonSerializationIgnoresMeta() {
            expect("""{"name":"Zaphod","age":42}""") { gson.toJson(Person(name = "Zaphod", age = 42)) }
        }
        @Test fun Meta() {
            val meta = Person.meta
            expect("Test") { meta.databaseTableName }  // since Person is annotated with @Entity("Test")
            expect("Test.id") { meta.idProperty[0].dbName.qualifiedName }
            expect(Person::class.java) { meta.entityClass }
            expect(Long::class.java) { meta.idProperty[0].valueType }
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
            ) { meta.persistedFieldDbNames.map { it.unqualifiedName } .toSet() }
        }
    }
    @Nested inner class EntityWithAliasedIdTests {
        @Test fun Save() {
            val p = EntityWithAliasedId(name = "Albedo")
            p.save()
            expectList("Albedo") { EntityWithAliasedId.findAll().map { it.name } }
            p.name = "Rubedo"
            p.save()
            expectList("Rubedo") { EntityWithAliasedId.findAll().map { it.name } }
            EntityWithAliasedId(name = "Nigredo").save()
            expectList("Rubedo", "Nigredo") { EntityWithAliasedId.findAll().map { it.name } }
        }
        @Test fun delete() {
            val p = EntityWithAliasedId(name = "Albedo")
            p.save()
            p.delete()
            expect(listOf()) { EntityWithAliasedId.findAll() }
        }
        @Test fun JsonSerializationIgnoresMeta() {
            expect("""{"name":"Zaphod"}""") { gson.toJson(EntityWithAliasedId(name = "Zaphod")) }
        }
        @Test fun Meta() {
            val meta = EntityWithAliasedId.meta
            expect("EntityWithAliasedId") { meta.databaseTableName }
            expect("myid") { meta.idProperty[0].dbName.unqualifiedName }
            expect(EntityWithAliasedId::class.java) { meta.entityClass }
            expect(Long::class.java) { meta.idProperty[0].valueType }
            expect(setOf("myid", "name")) { meta.persistedFieldDbNames.map { it.unqualifiedName } .toSet() }
        }
    }
    @Nested inner class NaturalPersonTests {
        @Test fun `save fails`() {
            val p = NaturalPerson(id = "12345678", name = "Albedo", bytes = byteArrayOf(5))
            expectThrows<IllegalStateException>("We expected to update only one row but we updated 0 - perhaps there is no row with id 12345678?") {
                p.save()
            }
        }
        @Test fun Save() {
            val p = NaturalPerson(id = "12345678", name = "Albedo", bytes = byteArrayOf(5))
            p.create()
            expectList("Albedo") { NaturalPerson.findAll().map { it.name } }
            p.name = "Rubedo"
            p.save()
            expectList("Rubedo") { NaturalPerson.findAll().map { it.name } }
            NaturalPerson(id = "aaa", name = "Nigredo", bytes = byteArrayOf(5)).create()
            expectList("Rubedo", "Nigredo") { NaturalPerson.findAll().map { it.name } }
        }
        @Test fun delete() {
            val p = NaturalPerson(id = "foo", name = "Albedo", bytes = byteArrayOf(5))
            p.create()
            p.delete()
            expectList() { NaturalPerson.findAll() }
        }
    }
    @Nested inner class LogRecordTests() {
        @Test fun `save succeeds since create() auto-generates ID`() {
            val p = LogRecord(text = "foo")
            p.save()
            expectList("foo") { LogRecord.findAll().map { it.text } }
        }
        @Test fun Save() {
            val p = LogRecord(text = "Albedo")
            p.save()
            expectList("Albedo") { LogRecord.findAll().map { it.text } }
            p.text = "Rubedo"
            p.save()
            expectList("Rubedo") { LogRecord.findAll().map { it.text } }
            LogRecord(text = "Nigredo").save()
            expect(setOf("Rubedo", "Nigredo")) { LogRecord.findAll().map { it.text } .toSet() }
        }
        @Test fun delete() {
            val p = LogRecord(text = "foo")
            p.save()
            p.delete()
            expectList() { LogRecord.findAll() }
        }
    }
    @Nested inner class TypeMapping {
        @Test fun `java enum to native db enum`() {
            for (it in MaritalStatus.entries.plusNull) {
                val id: kotlin.Long? = TypeMappingEntity(enumTest = it).run { save(); id }
                val loaded = TypeMappingEntity.findById(id!!)!!
                expect(it) { loaded.enumTest }
            }
        }
    }
}
