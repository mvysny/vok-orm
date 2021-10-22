package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectThrows
import com.github.mvysny.vokdataloader.EqFilter
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.expect

class DaoTest : DynaTest({
    withAllDatabases {
        group("Person") {
            test("FindById") {
                expect(null) { Person.findById(25) }
                val p = Person(name = "Albedo", age = 121)
                p.save()
                expect(p.withZeroNanos()) { Person.findById(p.id!!)?.withZeroNanos() }
            }
            test("GetById") {
                val p = Person(name = "Albedo", age = 122)
                p.save()
                expect(p.withZeroNanos()) { Person.getById(p.id!!).withZeroNanos() }
            }
            test("GetById fails if there is no such entity") {
                expectThrows<IllegalStateException>("There is no Person for id 25") {
                    Person.getById(25L)
                }
            }
            group("getOneBy() tests") {
                test("succeeds if there is exactly one matching entity") {
                    val p = Person(name = "Albedo", age = 123)
                    p.save()
                    expect(p.withZeroNanos()) { Person.getOneBy { Person::name eq "Albedo" } .withZeroNanos() }
                }

                test("fails if there is no such entity") {
                    expectThrows<IllegalStateException>("no row matching Person: 'name = ") {
                        Person.getOneBy { Person::name eq "Albedo" }
                    }
                }

                test("fails if there are two matching entities") {
                    repeat(2) { Person(name = "Albedo", age = 124).save() }
                    expectThrows<IllegalStateException>("too many rows matching Person: 'name = ") {
                        Person.getOneBy { Person::name eq "Albedo" }
                    }
                }

                test("fails if there are ten matching entities") {
                    repeat(10) { Person(name = "Albedo", age = 125).save() }
                    expectThrows<IllegalStateException>("too many rows matching Person: 'name = ") {
                        Person.getOneBy { Person::name eq "Albedo" }
                    }
                }
            }
            group("count") {
                test("basic count") {
                    expect(0) { Person.count() }
                    listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 126).save() }
                    expect(3) { Person.count() }
                }
                test("count with filters") {
                    expect(0) { Person.count { Person::age gt 6 } }
                    listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = it.length).save() }
                    expect(1) { Person.count { Person::age gt 6 } }
                }
            }
            test("DeleteAll") {
                listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 127).save() }
                expect(3) { Person.count() }
                Person.deleteAll()
                expect(0) { Person.count() }
            }
            group("DeleteById") {
                test("simple") {
                    listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 128).save() }
                    expect(3) { Person.count() }
                    Person.deleteById(Person.findAll().first { it.name == "Albedo" }.id!!)
                    expect(listOf("Nigredo", "Rubedo")) { Person.findAll().map { it.name } }
                }
                test("DoesNothingOnUnknownId") {
                    db { com.github.vokorm.Person.deleteById(25L) }
                    expect(listOf()) { Person.findAll() }
                }
            }
            test("DeleteBy") {
                listOf("Albedo", "Nigredo", "Rubedo").forEach { Person(name = it, age = 129).save() }
                Person.deleteBy { "name = :name"("name" to "Albedo") }  // raw sql where
                expect(listOf("Nigredo", "Rubedo")) { Person.findAll().map { it.name } }
                Person.deleteBy { Person::name eq "Rubedo" }  // fancy type-safe criteria
                expect(listOf("Nigredo")) { Person.findAll().map { it.name } }
            }
            group("findOneBy() tests") {
                test("succeeds if there is exactly one matching entity") {
                    val p = Person(name = "Albedo", age = 130)
                    p.save()
                    expect(p.withZeroNanos()) { Person.findOneBy { Person::name eq "Albedo" } ?.withZeroNanos() }
                }

                test("returns null if there is no such entity") {
                    expect(null) { Person.findOneBy { Person::name eq "Albedo" } }
                }

                test("fails if there are two matching entities") {
                    repeat(2) { Person(name = "Albedo", age = 131).save() }
                    expectThrows(IllegalStateException::class, "too many rows matching Person: 'name = ") {
                        Person.findOneBy { Person::name eq "Albedo" }
                    }
                }

                test("fails if there are ten matching entities") {
                    repeat(10) { Person(name = "Albedo", age = 132).save() }
                    expectThrows(IllegalStateException::class, "too many rows matching Person: 'name = ") {
                        Person.findOneBy { Person::name eq "Albedo" }
                    }
                }

                test("test filter by date") {
                    val p = Person(name = "Albedo", age = 133, dateOfBirth = LocalDate.of(1980, 2, 2))
                    p.save()
                    expect(p.withZeroNanos()) { Person.findOneBy { Person::dateOfBirth eq LocalDate.of(1980, 2, 2) } ?.withZeroNanos() }
                    // here I don't care about whether it selects something or not, I'm only testing the database compatibility
                    Person.findOneBy { "dateOfBirth = :a"("a" to Instant.now()) }
                    Person.findOneBy { "dateOfBirth = :a"("a" to Date()) }
                }
            }
            group("exists") {
                test("returns false on empty table") {
                    expect(false) { Person.existsAny() }
                    expect(false) { Person.existsById(25) }
                    expect(false) { Person.existsBy { Person::age le 26 } }
                }
                test("returns true on matching entity") {
                    val p = Person(name = "Albedo", age = 134)
                    p.save()
                    expect(true) { Person.existsAny() }
                    expect(true) { Person.existsById(p.id!!) }
                    expect(true) { Person.existsBy { Person::age ge 26 } }
                }
                test("returns false on non-matching entity") {
                    val p = Person(name = "Albedo", age = 135)
                    p.save()
                    expect(true) { Person.existsAny() }
                    expect(false) { Person.existsById(p.id!! + 1) }
                    expect(false) { Person.existsBy { Person::age le 26 } }
                }
            }
            test("sql92 filter works") {
                val p = Person(name = "Albedo", age = 136, dateOfBirth = LocalDate.of(1980, 2, 2), isAlive25 = true)
                p.save()
                expect(p.withZeroNanos()) { db { Person.findOneBy(EqFilter("alive", true))?.withZeroNanos() } }
            }
        }

        // quick tests which test that DAO methods generally work with entities with aliased ID columns
        group("EntityWithAliasedId") {
            test("FindById") {
                expect(null) { EntityWithAliasedId.findById(25) }
                val p = EntityWithAliasedId(name = "Albedo")
                p.save()
                expect(p) { EntityWithAliasedId.findById(p.id!!) }
            }
            test("GetById") {
                val p = EntityWithAliasedId(name = "Albedo")
                p.save()
                expect(p) { EntityWithAliasedId.getById(p.id!!) }
            }
            group("getOneBy() tests") {
                test("succeeds if there is exactly one matching entity") {
                    val p = EntityWithAliasedId(name = "Albedo")
                    p.save()
                    expect(p) { EntityWithAliasedId.getOneBy { EntityWithAliasedId::name eq "Albedo" } }
                }
            }
            group("count") {
                test("basic count") {
                    expect(0) { EntityWithAliasedId.count() }
                    listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
                    expect(3) { EntityWithAliasedId.count() }
                }
                test("count with filters") {
                    expect(0) { EntityWithAliasedId.count() }
                    listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
                    expect(1) { EntityWithAliasedId.count { EntityWithAliasedId::name eq "Albedo" } }
                    val id = EntityWithAliasedId.findAll().first { it.name == "Albedo" }.id!!
                }
            }
            test("DeleteAll") {
                listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
                expect(3) { EntityWithAliasedId.count() }
                EntityWithAliasedId.deleteAll()
                expect(0) { EntityWithAliasedId.count() }
            }
            test("DeleteById") {
                listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
                expect(3) { EntityWithAliasedId.count() }
                EntityWithAliasedId.deleteById(EntityWithAliasedId.findAll().first { it.name == "Albedo" }.id!!)
                expect(listOf("Nigredo", "Rubedo")) { EntityWithAliasedId.findAll().map { it.name } }
            }
            test("DeleteByIdDoesNothingOnUnknownId") {
                db { EntityWithAliasedId.deleteById(25L) }
                expect(listOf()) { EntityWithAliasedId.findAll() }
            }
            test("DeleteBy") {
                listOf("Albedo", "Nigredo", "Rubedo").forEach { EntityWithAliasedId(name = it).save() }
                EntityWithAliasedId.deleteBy { "name = :name"("name" to "Albedo") }  // raw sql where
                expect(listOf("Nigredo", "Rubedo")) { EntityWithAliasedId.findAll().map { it.name } }
            }
            group("findOneBy() tests") {
                test("succeeds if there is exactly one matching entity") {
                    val p = EntityWithAliasedId(name = "Albedo")
                    p.save()
                    expect(p) { EntityWithAliasedId.findOneBy { EntityWithAliasedId::name eq "Albedo" } }
                }
            }
            group("exists") {
                test("returns false on empty table") {
                    expect(false) { EntityWithAliasedId.existsAny() }
                    expect(false) { EntityWithAliasedId.existsById(25) }
                    expect(false) { EntityWithAliasedId.existsBy { EntityWithAliasedId::name le "a" } }
                }
                test("returns true on matching entity") {
                    val p = EntityWithAliasedId(name = "Albedo")
                    p.save()
                    expect(true) { EntityWithAliasedId.existsAny() }
                    expect(true) { EntityWithAliasedId.existsById(p.id!!) }
                    expect(true) { EntityWithAliasedId.existsBy { EntityWithAliasedId::name eq "Albedo" } }
                }
            }
        }
    }
})
