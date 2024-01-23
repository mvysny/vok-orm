package com.github.vokorm

import com.github.mvysny.dynatest.DynaNodeGroup
import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.DynaTestDsl
import com.github.mvysny.dynatest.expectList
import com.github.mvysny.vokdataloader.*
import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.OrderBy
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import kotlin.test.expect

class FiltersTest : DynaTest({

    fun unmangleParameterNames(sql: ParametrizedSql): String {
        var s = sql.sql92
        sql.sql92Parameters.entries.forEach { (key, value) -> s = s.replace(":$key", ":$value") }
        return s
    }
    fun sql(block: FilterBuilder<Person>.()-> Filter<Person>): String {
        val filter: Filter<Person> = block(FilterBuilder(Person::class.java))
        return unmangleParameterNames(filter.toParametrizedSql(Person::class.java, DatabaseVariant.Unknown))
    }

    test("ToSQL92") {
        expect("age = :25") { sql { Person::age eq 25 } }
        expect("not (age = :25)") { sql { !(Person::age eq 25) } }
        expect("(age >= :25 and age <= :50)") { sql { Person::age between 25..50 } }
        expect("((age >= :25 and age <= :50) or alive = :true)") { sql { (Person::age between 25..50) or (Person::isAlive25 eq true) } }
        expect("age in (:25, :26)") { sql { Person::age `in` listOf(25, 26) } }
    }

})

val OrderBy.sortClause: SortClause get() = SortClause(name.name, order == OrderBy.Order.ASC)

@DynaTestDsl
fun DynaNodeGroup.dbFiltersTest(info: DatabaseInfo) {
    test("api test") {
        Person.findAll(Person::age.asc, Person::created.desc)
        Person.findAllBy(Person::age.asc.sortClause, Person::created.desc.sortClause, filter = FullTextFilter<Person>("name", ""))
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

        test("construct sql succeeds") {
            FullTextFilter<Person>("name", "").toParametrizedSql(Person::class.java, JdbiOrm.databaseVariant!!)
        }

        if (info.supportsFullText) {
            test("smoke test") {
                Person.findAllBy(filter = FullTextFilter<Person>("name", ""))
                Person.findAllBy(filter = FullTextFilter<Person>("name", "a"))
                Person.findAllBy(filter = FullTextFilter<Person>("name", "the"))
                Person.findAllBy(filter = FullTextFilter<Person>("name", "Moby"))
            }

            test("blank filter matches all records") {
                val moby = Person(name = "Moby")
                moby.create()
                expectList(moby) { Person.findAllBy(filter = FullTextFilter<Person>("name", "")) }
            }

            test("various queries matching/not matching Moby") {
                val moby = Person(name = "Moby")
                moby.create()
                expectList() { Person.findAllBy(filter = FullTextFilter<Person>("name", "foo")) }
                expectList(moby) { Person.findAllBy(filter = FullTextFilter<Person>("name", "Moby")) }
                expectList() { Person.findAllBy(filter = FullTextFilter<Person>("name", "Jerry")) }
                expectList() { Person.findAllBy(filter = FullTextFilter<Person>("name", "Jerry Moby")) }
            }

            test("partial match") {
                val moby = Person(name = "Moby")
                moby.create()
                expectList(moby) { Person.findAllBy(filter = FullTextFilter<Person>("name", "Mob")) }
            }
        }
    }
}
