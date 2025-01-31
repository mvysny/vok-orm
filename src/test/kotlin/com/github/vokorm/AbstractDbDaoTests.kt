package com.github.vokorm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.expect

abstract class AbstractDbDaoTests {
    @Nested inner class PersonTests {
        @Test fun FindById() {
            expect(null) { Person.findById(25) }
            val p = Person(name = "Albedo", age = 121)
            p.save()
            expect(p) { Person.findById(p.id!!) }
        }
        @Test fun GetById() {
            val p = Person(name = "Albedo", age = 122)
            p.save()
            expect(p) { Person.getById(p.id!!) }
        }
        @Test fun `GetById fails if there is no such entity`() {
            expectThrows<IllegalStateException>("There is no Person for id 25") {
                Person.getById(25L)
            }
        }
        @Nested inner class `singleBy() tests` {
            @Test fun `succeeds if there is exactly one matching entity`() {
                val p = Person(name = "Albedo", age = 123)
                p.save()
                expect(p) { Person.singleBy { Person::name eq "Albedo" } }
            }

            @Test fun `fails if there is no such entity`() {
                expectThrows<IllegalStateException>("no row matching Person: '(Test.name) = ") {
                    Person.singleBy { Person::name eq "Albedo" }
                }
            }

            @Test fun `fails if there are two matching entities`() {
                repeat(2) { Person(name = "Albedo", age = 124).save() }
                expectThrows<IllegalStateException>("too many rows matching Person: '(Test.name) = ") {
                    Person.singleBy { Person::name eq "Albedo" }
                }
            }

            @Test fun `fails if there are ten matching entities`() {
                repeat(10) { Person(name = "Albedo", age = 125).save() }
                expectThrows<IllegalStateException>("too many rows matching Person: '(Test.name) = ") {
                    Person.singleBy { Person::name eq "Albedo" }
                }
            }
        }
        @Nested inner class count {
            @Test fun `basic count`() {
                expect(0) { Person.count() }
                listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 126).save() }
                expect(3) { Person.count() }
            }
            @Test fun `count with filters`() {
                expect(0) { Person.count { Person::age gt 6 } }
                listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = it.length).save() }
                expect(1) { Person.count { Person::age gt 6 } }
            }
        }
        @Test fun DeleteAll() {
            listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 127).save() }
            expect(3) { Person.count() }
            Person.deleteAll()
            expect(0) { Person.count() }
        }
        @Nested inner class DeleteById {
            @Test fun simple() {
                listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 128).save() }
                expect(3) { Person.count() }
                Person.deleteById(Person.findAll().first { it.name == "Albedo" }.id!!)
                expect(listOf("Nigredo", "Rubedo")) { Person.findAll().map { it.name } }
            }
            @Test fun DoesNothingOnUnknownId() {
                db { com.github.vokorm.Person.deleteById(25L) }
                expect(listOf()) { Person.findAll() }
            }
        }
        @Test fun DeleteBy() {
            listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 129).save() }
            Person.deleteBy { "name = :name"("name" to "Albedo") }  // raw sql where
            expect(listOf("Nigredo", "Rubedo")) { Person.findAll().map { it.name } }
            Person.deleteBy { Person::name eq "Rubedo" }  // fancy type-safe criteria
            expect(listOf("Nigredo")) { Person.findAll().map { it.name } }
        }
        @Nested inner class `findOneBy() tests` {
            @Test fun `succeeds if there is exactly one matching entity`() {
                val p = Person(name = "Albedo", age = 130)
                p.save()
                expect(p) { Person.findSingleBy { Person::name eq "Albedo" } }
            }

            @Test fun `returns null if there is no such entity`() {
                expect(null) { Person.findSingleBy { Person::name eq "Albedo" } }
            }

            @Test fun `fails if there are two matching entities`() {
                repeat(2) { Person(name = "Albedo", age = 131).save() }
                expectThrows<IllegalStateException>("too many rows matching Person: '(Test.name) = ") {
                    Person.findSingleBy { Person::name eq "Albedo" }
                }
            }

            @Test fun `fails if there are ten matching entities`() {
                repeat(10) { Person(name = "Albedo", age = 132).save() }
                expectThrows<IllegalStateException>("too many rows matching Person: '(Test.name) = ") {
                    Person.findSingleBy { Person::name eq "Albedo" }
                }
            }

            @Test fun `test filter by date`() {
                val p = Person(name = "Albedo", age = 133, dateOfBirth = LocalDate.of(1980, 2, 2))
                p.save()
                expect(p) {
                    Person.findSingleBy {Person::dateOfBirth eq LocalDate.of(1980, 2, 2) }
                }
                // here I don't care about whether it selects something or not, I'm only testing the database compatibility
                Person.findSingleBy { Person::dateOfBirth eq Instant.now() }
                Person.findSingleBy { Person::dateOfBirth eq Date() }
            }
        }
        @Nested inner class exists {
            @Test fun `returns false on empty table`() {
                expect(false) { Person.existsAny() }
                expect(false) { Person.existsById(25) }
                expect(false) { Person.existsBy { Person::age le 26 } }
            }
            @Test fun `returns true on matching entity`() {
                val p = Person(name = "Albedo", age = 134)
                p.save()
                expect(true) { Person.existsAny() }
                expect(true) { Person.existsById(p.id!!) }
                expect(true) { Person.existsBy { Person::age ge 26 } }
            }
            @Test fun `returns false on non-matching entity`() {
                val p = Person(name = "Albedo", age = 135)
                p.save()
                expect(true) { Person.existsAny() }
                expect(false) { Person.existsById(p.id!! + 1) }
                expect(false) { Person.existsBy { Person::age le 26 } }
            }
        }
        @Test fun `findAll sorting`() {
            Person.findAll(Person::id.asc)
            Person.findAll(Person::id.desc)
        }
    }

    // quick tests which test that DAO methods generally work with entities with aliased ID columns
    @Nested inner class EntityWithAliasedIdTests {
        @Test fun FindById() {
            expect(null) { EntityWithAliasedId.findById(25) }
            val p = EntityWithAliasedId(name = "Albedo")
            p.save()
            expect(p) { EntityWithAliasedId.findById(p.id!!) }
        }
        @Test fun GetById() {
            val p = EntityWithAliasedId(name = "Albedo")
            p.save()
            expect(p) { EntityWithAliasedId.getById(p.id!!) }
        }
        @Nested inner class `singleBy() tests`() {
            @Test fun `succeeds if there is exactly one matching entity`() {
                val p = EntityWithAliasedId(name = "Albedo")
                p.save()
                expect(p) { EntityWithAliasedId.singleBy { EntityWithAliasedId::name eq "Albedo" } }
            }
        }
        @Nested inner class count {
            @Test fun `basic count`() {
                expect(0) { EntityWithAliasedId.count() }
                listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
                expect(3) { EntityWithAliasedId.count() }
            }
            @Test fun `count with filters`() {
                expect(0) { EntityWithAliasedId.count() }
                listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
                expect(1) { EntityWithAliasedId.count { EntityWithAliasedId::name eq "Albedo" } }
                val id = EntityWithAliasedId.findAll().first { it.name == "Albedo" }.id!!
            }
        }
        @Test fun DeleteAll() {
            listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
            expect(3) { EntityWithAliasedId.count() }
            EntityWithAliasedId.deleteAll()
            expect(0) { EntityWithAliasedId.count() }
        }
        @Test fun DeleteById() {
            listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
            expect(3) { EntityWithAliasedId.count() }
            EntityWithAliasedId.deleteById(EntityWithAliasedId.findAll().first { it.name == "Albedo" }.id!!)
            expect(listOf("Nigredo", "Rubedo")) { EntityWithAliasedId.findAll().map { it.name } }
        }
        @Test fun DeleteByIdDoesNothingOnUnknownId() {
            db { EntityWithAliasedId.deleteById(25L) }
            expect(listOf()) { EntityWithAliasedId.findAll() }
        }
        @Test fun DeleteBy() {
            listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
            EntityWithAliasedId.deleteBy { "name = :name"("name" to "Albedo") }  // raw sql where
            expect(listOf("Nigredo", "Rubedo")) { EntityWithAliasedId.findAll().map { it.name } }
        }
        @Nested inner class `findOneBy() tests` {
            @Test fun `succeeds if there is exactly one matching entity`() {
                val p = EntityWithAliasedId(name = "Albedo")
                p.save()
                expect(p) { EntityWithAliasedId.findSingleBy { EntityWithAliasedId::name eq "Albedo" } }
            }
        }
        @Nested inner class exists {
            @Test fun `returns false on empty table`() {
                expect(false) { EntityWithAliasedId.existsAny() }
                expect(false) { EntityWithAliasedId.existsById(25) }
                expect(false) { EntityWithAliasedId.existsBy { EntityWithAliasedId::name le "a" } }
            }
            @Test fun `returns true on matching entity`() {
                val p = EntityWithAliasedId(name = "Albedo")
                p.save()
                expect(true) { EntityWithAliasedId.existsAny() }
                expect(true) { EntityWithAliasedId.existsById(p.id!!) }
                expect(true) { EntityWithAliasedId.existsBy { EntityWithAliasedId::name eq "Albedo" } }
            }
        }
    }
}
