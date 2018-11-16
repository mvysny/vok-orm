package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.vokdataloader.Filter
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
})
