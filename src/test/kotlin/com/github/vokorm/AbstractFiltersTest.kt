package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

abstract class AbstractFiltersTest(val info: DatabaseInfo) {
    @Test fun `api test`() {
        Person.findAll(Person::age.asc, Person::created.desc)
        Person.findAllBy(Person::age.asc, Person::created.desc, condition = Person::age.exp.eq(5))
    }

    @Nested inner class `filter test` {
        @BeforeEach fun preCreateTestEntities() {
            // create a basic set of entities
            Person(name = "Moby", age = 25).create()
            Person(name = "Jerry", age = 26).create()
            Person(name = "Paul", age = 27).create()
        }

        @Test fun `eq filter test`() {
            expectList() {
                Person.findAllBy { Person::age eq 40 }.map { it.name }
            }
            expectList("Jerry") {
                Person.findAllBy { Person::age eq 26 }.map { it.name }
            }
        }

        @Test fun `ne filter test`() {
            expectList("Moby", "Jerry", "Paul") {
                Person.findAllBy { Person::age ne 40 }.map { it.name }
            }
            expectList("Jerry", "Paul") {
                Person.findAllBy { Person::age ne 25 }.map { it.name }
            }
        }

        @Test fun `le filter test`() {
            expectList("Moby", "Jerry", "Paul") {
                Person.findAllBy { Person::age le 40 }.map { it.name }
            }
            expectList("Moby", "Jerry") {
                Person.findAllBy { Person::age le 26 }.map { it.name }
            }
        }

        @Test fun `lt filter test`() {
            expectList("Moby", "Jerry", "Paul") {
                Person.findAllBy { Person::age lt 40 }.map { it.name }
            }
            expectList("Moby") {
                Person.findAllBy { Person::age lt 26 }.map { it.name }
            }
        }

        @Test fun `ge filter test`() {
            expectList() {
                Person.findAllBy { Person::age ge 40 }.map { it.name }
            }
            expectList("Jerry", "Paul") {
                Person.findAllBy { Person::age ge 26 }.map { it.name }
            }
        }

        @Test fun `gt filter test`() {
            expectList() {
                Person.findAllBy { Person::age gt 40 }.map { it.name }
            }
            expectList("Paul") {
                Person.findAllBy { Person::age gt 26 }.map { it.name }
            }
        }

        @Test fun `not filter test`() {
            expectList("Moby", "Paul") {
                Person.findAllBy { !(Person::age eq 26) }.map { it.name }
            }
        }

        @Test fun `in filter test`() {
            expectList("Moby", "Jerry") {
                Person.findAllBy { Person::age `in` listOf(25, 26, 28) }.map { it.name }
            }
        }
    }

    @Nested inner class `full-text search` {
        @BeforeEach fun assumeSupportsFullText() {
            Assumptions.assumeTrue(
                info.supportsFullText,
                "This database doesn't support full-text search, skipping tests"
            )
        }
        @Test fun `smoke test`() {
            Person.findAllBy(Person::name.exp.fullTextMatches(""))
            Person.findAllBy(Person::name.exp.fullTextMatches("a"))
            Person.findAllBy(Person::name.exp.fullTextMatches("the"))
            Person.findAllBy(Person::name.exp.fullTextMatches("Moby"))
        }

        @Test fun `blank filter matches all records`() {
            val moby = Person(name = "Moby")
            moby.create()
            expectList(moby) { Person.findAllBy(Person::name.exp.fullTextMatches("")) }
        }

        @Test fun `various queries matching-not matching Moby`() {
            val moby = Person(name = "Moby")
            moby.create()
            expectList() { Person.findAllBy(Person::name.exp.fullTextMatches("foobar")) }
            expectList(moby) { Person.findAllBy(Person::name.exp.fullTextMatches("Moby")) }
            expectList() { Person.findAllBy(Person::name.exp.fullTextMatches("Jerry")) }
            expectList() { Person.findAllBy(Person::name.exp.fullTextMatches("Jerry Moby")) }
        }

        @Test fun `partial match`() {
            val moby = Person(name = "Moby")
            moby.create()
            expectList(moby) { Person.findAllBy(Person::name.exp.fullTextMatches("Mob")) }
        }
    }
}
