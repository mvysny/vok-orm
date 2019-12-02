package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectList
import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.FullTextFilter
import com.github.mvysny.vokdataloader.SqlWhereBuilder
import kotlin.test.expect

class FiltersTest : DynaTest({

    fun unmangleParameterNames(sql: ParametrizedSql): String {
        var s = sql.sql92
        sql.sql92Parameters.entries.forEach { (key, value) -> s = s.replace(":$key", ":$value") }
        return s
    }
    fun sql(block: SqlWhereBuilder<Person>.()-> Filter<Person>): String {
        val filter: Filter<Person> = block(SqlWhereBuilder(Person::class.java))
        return unmangleParameterNames(filter.toParametrizedSql(Person::class.java))
    }

    test("ToSQL92") {
        expect("age = :25") { sql { Person::age eq 25 } }
        expect("(age >= :25 and age <= :50)") { sql { Person::age between 25..50 } }
        expect("((age >= :25 and age <= :50) or alive = :true)") { sql { (Person::age between 25..50) or (Person::isAlive25 eq true) } }
    }

    withAllDatabases {
        group("full-text search") {
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
})
