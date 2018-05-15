package com.github.vokorm.dataloader

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectList
import com.github.mvysny.dynatest.expectThrows
import com.github.vokorm.*
import kotlin.test.expect

class SqlDataLoaderTest : DynaTest({
    data class SelectResult(val id: Long, val name: String)

    withAllDatabases {
        val nameAsc: List<SortClause> = listOf("name".asc)
        val nameDesc: List<SortClause> = listOf("name".desc)

        test("EmptyDataProvider") {
            val dp = SqlDataLoader(SelectResult::class.java,
                """select p.id as id, p.name as name from ${Person.meta.databaseTableName} p where 1=1 {{WHERE}} order by 1=1{{ORDER}} {{PAGING}}""")

            expect(0) { dp.getCount() }
            expectList() { dp.fetch() }
            val f = buildFilter<SelectResult> { SelectResult::id gt 2 }
            expect(0) { dp.getCount(f) }
            expectList() { dp.fetch(f) }
            expect(0) { dp.getCount() }
            expectList() { dp.fetch(sortBy = nameAsc, range = 0..19) }
            expect(0) { dp.getCount(f) }
            expectList() { dp.fetch(f, nameAsc, 0..19) }
            expect(0) { dp.getCount(f) }
            expectList() { dp.fetch(f, nameAsc, 0..Int.MAX_VALUE) }
            expect(0) { dp.getCount(f) }
            expectList() { dp.fetch(f, range = 0..Int.MAX_VALUE) }
            expect(0) { dp.getCount() }
            expectList() { dp.fetch(range = 0..Int.MAX_VALUE) }
        }

        test("overwriting parameters is forbidden") {
            expectThrows(IllegalArgumentException::class) {
                val dp = SqlDataLoader(SelectResult::class.java,
                    """select p.id as id, p.name as name from ${Person.meta.databaseTableName} p where age > :age {{WHERE}} order by 1=1{{ORDER}} {{PAGING}}""",
                    mapOf("age" to 25))
                val f = buildFilter<SelectResult> { "age<:age"("age" to 48) }
                // this must fail because the filter also introduces parameter "age" which is already introduced in the SqlDataLoader
                dp.getCount(f)
            }
        }

        test("parametrized DP") {
            db { (0..50).forEach { Person(name = "name $it", age = it).save() } }
            val dp = SqlDataLoader(SelectResult::class.java,
                """select p.id as id, p.name as name from ${Person.meta.databaseTableName} p where age > :age {{WHERE}} order by 1=1{{ORDER}} {{PAGING}}""",
                mapOf("age" to 25))
            val f = buildFilter<SelectResult> { "age<:age_f"("age_f" to 48) }
            expect(25) { dp.getCount() }
            expect((26..50).map { "name $it" }) { dp.fetch().map { it.name } }
            expect(25) { dp.getCount() }
            expect((26..45).map { "name $it" }) { dp.fetch(range = 0..19).map { it.name } }

            // limit is ignored with size queries; also test that filter f ANDs with SqlDataLoader's filter
            expect(22) { dp.getCount(f) }
            expect((47 downTo 28).map { "name $it" }) { dp.fetch(f, nameDesc, 0..19).map { it.name } }
            expect(22) { dp.getCount(f) }

            // limit of Int.MAX_VALUE works as if no limit was specified
            expect((26..47).map { "name $it" }) { dp.fetch(f, nameAsc).map { it.name } }
            expect(22) { dp.getCount(f) }
            expect((26..47).map { "name $it" }) { dp.fetch(f, range = 0..Int.MAX_VALUE).map { it.name } }

            expect(25) { dp.getCount() }
            expect((26..50).map { "name $it" }) { dp.fetch(range = 0..Int.MAX_VALUE).map { it.name } }
        }
    }
})
