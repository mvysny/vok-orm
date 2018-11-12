package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectThrows
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.test.expect

class DaoTest : DynaTest({
    withAllDatabases {
        test("FindById") {
            expect(null) { Person.findById(25) }
            val p = Person(name = "Albedo", age = 130)
            p.save()
            expect(p) { Person.findById(p.id!!) }
        }
        test("GetById") {
            val p = Person(name = "Albedo", age = 130)
            p.save()
            expect(p) { Person.getById(p.id!!) }
        }
        test("GetById fails if there is no such entity") {
            expectThrows(IllegalArgumentException::class, message = "There is no Person for id 25") {
                Person.getById(25L)
            }
        }
        group("getBy() tests") {
            test("succeeds if there is exactly one matching entity") {
                val p = Person(name = "Albedo", age = 130)
                p.save()
                expect(p) { Person.getBy { Person::name eq "Albedo" } }
            }

            test("fails if there is no such entity") {
                expectThrows(IllegalArgumentException::class, message = "no Person satisfying name = ") {
                    Person.getBy { Person::name eq "Albedo" }
                }
            }

            test("fails if there are two matching entities") {
                repeat(2) { Person(name = "Albedo", age = 130).save() }
                expectThrows(IllegalArgumentException::class, message = "too many Person satisfying name = ") {
                    Person.getBy { Person::name eq "Albedo" }
                }
            }

            test("fails if there are ten matching entities") {
                repeat(10) { Person(name = "Albedo", age = 130).save() }
                expectThrows(IllegalArgumentException::class, message = "too many Person satisfying name = ") {
                    Person.getBy { Person::name eq "Albedo" }
                }
            }
        }
        group("count") {
            test("basic count") {
                expect(0) { Person.count() }
                listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 130).save() }
                expect(3) { Person.count() }
            }
            test("count with filters") {
                expect(0) { Person.count() }
                listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = it.length).save() }
                expect(1) { Person.count { Person::age gt 6 } }
            }
        }
        test("DeleteAll") {
            listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 130).save() }
            expect(3) { Person.count() }
            Person.deleteAll()
            expect(0) { Person.count() }
        }
        test("DeleteById") {
            listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 130).save() }
            expect(3) { Person.count() }
            Person.deleteById(Person.findAll().first { it.name == "Albedo" }.id!!)
            expect(listOf("Nigredo", "Rubedo")) { Person.findAll().map { it.name } }
        }
        test("DeleteByIdDoesNothingOnUnknownId") {
            db { com.github.vokorm.Person.deleteById(25L) }
            expect(listOf()) { Person.findAll() }
        }
        test("DeleteBy") {
            listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 130).save() }
            Person.deleteBy { "name = :name"("name" to "Albedo") }  // raw sql where
            expect(listOf("Nigredo", "Rubedo")) { Person.findAll().map { it.name } }
            Person.deleteBy { Person::name eq "Rubedo" }  // fancy type-safe criteria
            expect(listOf("Nigredo")) { Person.findAll().map { it.name } }
        }
        group("findSpecificBy() tests") {
            test("succeeds if there is exactly one matching entity") {
                val p = Person(name = "Albedo", age = 130)
                p.save()
                expect(p) { Person.findSpecificBy { Person::name eq "Albedo" } }
            }

            test("returns null if there is no such entity") {
                expect(null) { Person.findSpecificBy { Person::name eq "Albedo" } }
            }

            test("fails if there are two matching entities") {
                repeat(2) { Person(name = "Albedo", age = 130).save() }
                expectThrows(IllegalArgumentException::class, "too many Person satisfying name =") { Person.findSpecificBy { Person::name eq "Albedo" } }
            }

            test("fails if there are ten matching entities") {
                repeat(10) { Person(name = "Albedo", age = 130).save() }
                expectThrows(IllegalArgumentException::class, "too many Person satisfying name =") { Person.findSpecificBy { Person::name eq "Albedo" } }
            }

            test("test filter by date") {
                val p = Person(name = "Albedo", age = 130, dateOfBirth = LocalDate.of(1980, 2, 2))
                p.save()
                expect(p) { Person.findSpecificBy { Person::dateOfBirth eq LocalDate.of(1980, 2, 2) } }
                // here I don't care about whether it selects something or not, I'm only testing the database compatibility
                Person.findSpecificBy { "dateOfBirth = :a"("a" to Instant.now()) }
                Person.findSpecificBy { "dateOfBirth = :a"("a" to Date()) }
            }
        }
        group("exists") {
            test("returns false on empty table") {
                expect(false) { Person.existsAny() }
                expect(false) { Person.existsById(25) }
                expect(false) { Person.existsBy { Person::age le 26 } }
            }
            test("returns true on matching entity") {
                val p = Person(name = "Albedo", age = 130)
                p.save()
                expect(true) { Person.existsAny() }
                expect(true) { Person.existsById(p.id!!) }
                expect(true) { Person.existsBy { Person::age ge 26 } }
            }
            test("returns true on non-matching entity") {
                val p = Person(name = "Albedo", age = 130)
                p.save()
                expect(true) { Person.existsAny() }
                expect(false) { Person.existsById(p.id!! + 1) }
                expect(false) { Person.existsBy { Person::age le 26 } }
            }
        }
    }
})
