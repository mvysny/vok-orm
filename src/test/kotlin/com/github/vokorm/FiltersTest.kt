package com.github.vokorm

import com.github.mvysny.dynatest.DynaTest
import kotlin.test.expect

class FiltersTest : DynaTest({

    fun unmangleParameterNames(sql: String, params: Map<String, Any?>): String {
        var sql = sql
        params.entries.forEach { (key, value) -> sql = sql.replace(":$key", ":$value") }
        return sql
    }
    fun sql(block: SqlWhereBuilder<Person>.()-> Filter<Person>): String {
        val filter: Filter<Person> = block(SqlWhereBuilder(Person::class.java))
        return unmangleParameterNames(filter.toSQL92(), filter.getSQL92Parameters())
    }

    test("ToSQL92") {
        expect("age = :25") { sql { Person::age eq 25 } }
        expect("(age >= :25 and age <= :50)") { sql { Person::age between 25..50 } }
        expect("((age >= :25 and age <= :50) or alive = :true)") { sql { (Person::age between 25..50) or (Person::isAlive25 eq true) } }
    }

    test("Equals") {
        expect(ILikeFilter("name", "A")) { ILikeFilter<Person>("name", "A") }
        expect(false) { ILikeFilter<Person>("name", "A") == LikeFilter<Person>("name", "A") }
    }
})
