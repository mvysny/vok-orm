package com.github.vokorm

import com.github.mvysny.dynatest.DynaNodeGroup
import com.github.mvysny.dynatest.DynaTestDsl
import com.github.mvysny.dynatest.expectList

@DynaTestDsl
fun DynaNodeGroup.dbFiltersTest(info: DatabaseInfo) {
    test("api test") {
        Person.findAll(Person::age.asc, Person::created.desc)
        Person.findAllBy(Person::age.asc, Person::created.desc, condition = Person::age.exp.eq(5))
    }

    group("filter test") {
        beforeEach {
            // create a basic set of entities
            Person(name = "Moby", age = 25).create()
            Person(name = "Jerry", age = 26).create()
            Person(name = "Paul", age = 27).create()
        }

        test("eq filter test") {
            expectList() {
                Person.findAllBy { Person::age eq 40 }.map { it.name }
            }
            expectList("Jerry") {
                Person.findAllBy { Person::age eq 26 }.map { it.name }
            }
        }

        test("ne filter test") {
            expectList("Moby", "Jerry", "Paul") {
                Person.findAllBy { Person::age ne 40 }.map { it.name }
            }
            expectList("Jerry", "Paul") {
                Person.findAllBy { Person::age ne 25 }.map { it.name }
            }
        }

        test("le filter test") {
            expectList("Moby", "Jerry", "Paul") {
                Person.findAllBy { Person::age le 40 }.map { it.name }
            }
            expectList("Moby", "Jerry") {
                Person.findAllBy { Person::age le 26 }.map { it.name }
            }
        }

        test("lt filter test") {
            expectList("Moby", "Jerry", "Paul") {
                Person.findAllBy { Person::age lt 40 }.map { it.name }
            }
            expectList("Moby") {
                Person.findAllBy { Person::age lt 26 }.map { it.name }
            }
        }

        test("ge filter test") {
            expectList() {
                Person.findAllBy { Person::age ge 40 }.map { it.name }
            }
            expectList("Jerry", "Paul") {
                Person.findAllBy { Person::age ge 26 }.map { it.name }
            }
        }

        test("gt filter test") {
            expectList() {
                Person.findAllBy { Person::age gt 40 }.map { it.name }
            }
            expectList("Paul") {
                Person.findAllBy { Person::age gt 26 }.map { it.name }
            }
        }

        test("not filter test") {
            expectList("Moby", "Paul") {
                Person.findAllBy { !(Person::age eq 26) }.map { it.name }
            }
        }

        test("in filter test") {
            expectList("Moby", "Jerry") {
                Person.findAllBy { Person::age `in` listOf(25, 26, 28) }.map { it.name }
            }
        }
    }

    group("full-text search") {
        if (info.supportsFullText) {
            test("smoke test") {
                Person.findAllBy(Person::name.exp.fullTextMatches(""))
                Person.findAllBy(Person::name.exp.fullTextMatches("a"))
                Person.findAllBy(Person::name.exp.fullTextMatches("the"))
                Person.findAllBy(Person::name.exp.fullTextMatches("Moby"))
            }

            test("blank filter matches all records") {
                val moby = Person(name = "Moby")
                moby.create()
                expectList(moby) { Person.findAllBy(Person::name.exp.fullTextMatches("")) }
            }

            test("various queries matching/not matching Moby") {
                val moby = Person(name = "Moby")
                moby.create()
                expectList() { Person.findAllBy(Person::name.exp.fullTextMatches("foo")) }
                expectList(moby) { Person.findAllBy(Person::name.exp.fullTextMatches("Moby")) }
                expectList() { Person.findAllBy(Person::name.exp.fullTextMatches("Jerry")) }
                expectList() { Person.findAllBy(Person::name.exp.fullTextMatches("Jerry Moby")) }
            }

            test("partial match") {
                val moby = Person(name = "Moby")
                moby.create()
                expectList(moby) { Person.findAllBy(Person::name.exp.fullTextMatches("Mob")) }
            }
        }
    }
}
