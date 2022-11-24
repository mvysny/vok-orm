package com.github.vokorm.dataloader

import com.github.mvysny.dynatest.*
import com.github.mvysny.vokdataloader.SortClause
import com.github.mvysny.vokdataloader.buildFilter
import com.github.vokorm.*
import com.gitlab.mvysny.jdbiorm.Table
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import org.jdbi.v3.core.mapper.reflect.ColumnName
import kotlin.test.expect

data class SelectResult(val id: Long? = null, val name: String? = null)
@Table("Test")
data class SelectResult2(@field:ColumnName("name") var personName: String = "")

class SqlDataLoaderTest : DynaTest({

    test("serializable") {
        val dp = SqlDataLoader(SelectResult::class.java,
                """select p.id as id, p.name as name from ${Person.meta.databaseTableName} p where 1=1 {{WHERE}} order by 1=1{{ORDER}} {{PAGING}}""")
        dp.serializeToBytes()
    }
})

@DynaTestDsl
fun DynaNodeGroup.dbSqlDataLoaderTests(info: DatabaseInfo) {
    val nameAsc: List<SortClause> = listOf("name".asc)
    val nameDesc: List<SortClause> = listOf("name".desc)
    val top: String = if (info.variant == DatabaseVariant.MSSQL) "TOP 2147483647" else ""
    val orderBy: String = if (info.variant == DatabaseVariant.MSSQL) "(select 1)" else "1=1"

    test("EmptyDataProvider") {
        val dp: SqlDataLoader<SelectResult> = SqlDataLoader(SelectResult::class.java,
            """select $top p.id as id, p.name as name from ${Person.meta.databaseTableName} p where 1=1 {{WHERE}} order by $orderBy{{ORDER}} {{PAGING}}""")

        expect(0) { dp.getCount() }
        expectList() { dp.fetch() }
        val f = buildFilter<SelectResult> { SelectResult::id gt 2 }
        expect(0) { dp.getCount(f) }
        expectList() { dp.fetch(f) }
        expect(0) { dp.getCount() }
        expectList() { dp.fetch(sortBy = nameAsc, range = 0L..19) }
        expect(0) { dp.getCount(f) }
        expectList() { dp.fetch(f, nameAsc, 0L..19) }
        expect(0) { dp.getCount(f) }
        expectList() { dp.fetch(f, nameAsc, 0L..Long.MAX_VALUE) }
        expect(0) { dp.getCount(f) }
        expectList() { dp.fetch(f, range = 0L..Long.MAX_VALUE) }
        expect(0) { dp.getCount() }
        expectList() { dp.fetch(range = 0L..Long.MAX_VALUE) }
        expectList() { dp.fetch(range = 0L..(Long.MAX_VALUE - 1)) }
    }

    test("overwriting parameters is forbidden") {
        expectThrows(IllegalArgumentException::class) {
            val dp = SqlDataLoader(SelectResult::class.java,
                """select $top p.id as id, p.name as name from ${Person.meta.databaseTableName} p where age > :age {{WHERE}} order by $orderBy{{ORDER}} {{PAGING}}""",
                mapOf("age" to 25))
            val f = buildFilter<SelectResult> { "age<:age"("age" to 48) }
            // this must fail because the filter also introduces parameter "age" which is already introduced in the SqlDataLoader
            dp.getCount(f)
        }
    }

    test("parametrized DP") {
        db { (0..50).forEach { Person(name = "name $it", age = it).save() } }
        val dp = SqlDataLoader(SelectResult::class.java,
            """select $top p.id as id, p.name as name from ${Person.meta.databaseTableName} p where age > :age {{WHERE}} order by $orderBy{{ORDER}} {{PAGING}}""",
            mapOf("age" to 25))
        val f = buildFilter<SelectResult> { "age<:age_f"("age_f" to 48) }
        expect(25) { dp.getCount() }
        expect((26..50).map { "name $it" }) { dp.fetch().map { it.name } }
        expect(25) { dp.getCount() }
        expect((26..45).map { "name $it" }) { dp.fetch(range = 0L..19L).map { it.name } }

        // limit is ignored with size queries; also test that filter f ANDs with SqlDataLoader's filter
        expect(22) { dp.getCount(f) }
        expect((47 downTo 28).map { "name $it" }) { dp.fetch(f, nameDesc, 0L..19).map { it.name } }
        expect(22) { dp.getCount(f) }

        // limit of Int.MAX_VALUE works as if no limit was specified
        expect((26..47).map { "name $it" }) { dp.fetch(f, nameAsc).map { it.name } }
        expect(22) { dp.getCount(f) }
        expect((26..47).map { "name $it" }) { dp.fetch(f, range = 0L..Long.MAX_VALUE).map { it.name } }

        expect(25) { dp.getCount() }
        expect((26..50).map { "name $it" }) { dp.fetch(range = 0L..Long.MAX_VALUE).map { it.name } }
    }

    // https://github.com/mvysny/vok-orm/issues/5
    test("alias") {
        db { (0..49).forEach { Person(name = "name $it", age = it).save() } }
        val loader = SqlDataLoader(
            SelectResult2::class.java,
            "select $top p.name from Test p where 1=1 {{WHERE}} order by $orderBy{{ORDER}} {{PAGING}}"
        )
        expect(50) { loader.getCount(null) }
        expect(1) { loader.getCount(buildFilter { SelectResult2::personName eq "name 20" })}
        expectList(SelectResult2("name 20")) { loader.fetch(buildFilter { SelectResult2::personName eq "name 20" }) }
    }
}
