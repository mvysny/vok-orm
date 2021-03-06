package com.github.vokorm.dataloader

import com.github.mvysny.vokdataloader.DataLoader
import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.SortClause
import com.github.vokorm.*
import com.gitlab.mvysny.jdbiorm.DaoOfAny
import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.quirks.DatabaseVariant
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.statement.Query

/**
 * Allows the coder to write any SQL he wishes. This provider must be simple enough to not to get in the way by smart (complex) Kotlin language features.
 * It should support any SQL select, but should allow for adding custom filters and orderings (since this is plugged into Grid after all).
 *
 * The provider is bound to a *holder class* which holds the values (any POJO). Sql2o is used to map the result set to the class. For example:
 *
 * ```
 * data class CustomerAddress(val customerName: String, val address: String)
 *
 * val provider = SqlDataLoader(CustomerAddress::class.java, """select c.name as customerName, a.street || ' ' || a.city as address
 *     from Customer c inner join Address a on c.address_id=a.id where 1=1 {{WHERE}} order by 1=1{{ORDER}} {{PAGING}}""")
 * ```
 *
 * (Note how select column names must correspond to field names in the `CustomerAddress` class)
 *
 * Now SqlDataLoader can hot-patch the `where` clause computed from Grid's filters into `{{WHERE}}` (as a simple string replacement),
 * and the `order by` and `offset`/`limit` into the `{{ORDER}}` and `{{PAGING}}`, as follows:
 *
 * * `{{WHERE}}` will be replaced by something like this: `"and name=:pqw5as and age>:p123a"` - note the auto-generated parameter
 *   names starting with `p`. If there are no filters, will be replaced by an empty string.
 * * `{{ORDER}}` will be replaced by `", customerName ASC, street ASC"` or by an empty string if there is no ordering requirement.
 * * `{{PAGING}}` will be replaced by `"offset 0 limit 100"` or by an empty string if there are no limitations.
 *
 * Note that the Grid will display fields present in the `CustomerAddress` holder class and will also auto-generate filters
 * for them, based on the type of the field.
 *
 * No bloody annotations! Work in progress. It is expected that a holder class is written for every select, tailored to show the outcome
 * of that particular select.
 *
 * ## Property Names And Mapping Details
 *
 * The `NativePropertyName` is the database column name of the database table linked to by this entity (using the
 * [com.gitlab.mvysny.jdbiorm.Table] mapping). The `DataLoaderPropertyName` is the Java Bean Property Name or the Kotlin Property name of the [com.gitlab.mvysny.jdbiorm.Entity],
 * but it also accepts the database column name.
 *
 * The database column name is mapped 1:1 to the Java Bean Property Name. If you however use `UPPER_UNDERSCORE` naming scheme
 * in your database, you can map it to `camelCase` using the [org.jdbi.v3.core.mapper.reflect.ColumnName] annotation.
 *
 * ## Database-specific notes
 *
 * ### Microsoft SQL
 *
 * MSSQL doesn't really like the `order by 1=1` hence you should replace that by `order by (select 1)`.
 *
 * MSSQL can't execute `SELECT count(*) FROM (... order by)` unless TOP is used: see [sql-error-in-subquery](https://stackoverflow.com/questions/985921/sql-error-with-order-by-in-subquery/2437298)
 * for more details. Therefore make sure to use `TOP 2147483647` in your queries, as follows:
 * `select TOP 2147483647 p.id, p.name from Test p where...`.
 *
 * MSSQL however can't paginate when the `TOP` clause is present. That's why [sqlPostProcessor] will remove the `TOP` clause
 * on non-count queries on MSSQL by default.
 *
 * @param dao loads instances of [T]
 * @param sql the select which can map into the holder class (that is, it selects columns whose names match the holder class fields). It should contain
 * `{{WHERE}}`, `{{ORDER}}` and `{{PAGING}}` strings which will be replaced by a simple substring replacement.
 * @param params the [sql] may be parametrized; this map holds all the parameters present in the sql itself.
 * @param T the type of the holder class.
 * @author mavi
 */
public class SqlDataLoader<T: Any>(
        public val dao: DaoOfAny<T>,
        public val sql: String,
        public val params: Map<String, Any?> = mapOf()
) : DataLoader<T> {

    public val clazz: Class<T> get() = dao.entityClass

    /**
     * Post-processes the SQL before it's being run. By default simply returns the `sql` itself unmodified;
     * however on MSSQL removes the `TOP 2147483647` clause on non-count queries.
     */
    public var sqlPostProcessor: (sql: String, isCountQuery: Boolean) -> String = { sql, isCountQuery ->
        if (JdbiOrm.databaseVariant == DatabaseVariant.MSSQL && !isCountQuery) {
            sql.replace("TOP 2147483647", "", true)
        } else {
            sql
        }
    }

    public constructor(clazz: Class<T>, sql: String, params: Map<String, Any?> = mapOf()) : this(DaoOfAny<T>(clazz), sql, params)

    override fun toString(): String = "SqlDataLoader($clazz:$sql($params))"

    override fun getCount(filter: Filter<T>?): Long = db {
        val sql: ParametrizedSql = filter?.toParametrizedSql(clazz, JdbiOrm.databaseVariant!!) ?: ParametrizedSql("", mapOf())
        val q: Query = handle.createQuery(handle.computeSQL(true, sql))
        params.entries.forEach { (name, value) -> q.bind(name, value) }
        q.fillInParamsFromFilters(sql)
        val count: Long = q.mapTo(Long::class.java).one() ?: 0
        count
    }

    override fun fetch(filter: Filter<T>?, sortBy: List<SortClause>, range: LongRange): List<T> = db {
        val sql: ParametrizedSql = filter?.toParametrizedSql(clazz, JdbiOrm.databaseVariant!!) ?: ParametrizedSql("", mapOf())
        val q: Query = handle.createQuery(handle.computeSQL(false, sql, sortBy, range))
        params.entries.forEach { (name, value) -> q.bind(name, value) }
        q.fillInParamsFromFilters(sql)
        q.map(dao.getRowMapper()).list()
    }

    private fun Query.fillInParamsFromFilters(filter: ParametrizedSql): Query {
        filter.sql92Parameters.entries.forEach { (name, value) ->
            require(!this@SqlDataLoader.params.containsKey(name)) { "Filters tries to set the parameter $name to $value but that parameter is already forced by SqlDataLoader to ${params[name]}: filter=$sql dp=${this@SqlDataLoader}" }
            bind(name, value)
        }
        return this
    }

    /**
     * Using [sql] as a template, computes the replacement strings for the `{{WHERE}}`, `{{ORDER}}` and `{{PAGING}}` replacement strings.
     */
    private fun Handle.computeSQL(isCountQuery: Boolean, filter: ParametrizedSql, sortOrders: List<SortClause> = listOf(), range: LongRange = 0..Long.MAX_VALUE): String {
        // compute the {{WHERE}} replacement
        var where: String = filter.sql92
        if (where.isNotBlank()) where = "and $where"

        // compute the {{ORDER}} replacement
        var orderBy: String = (if (isCountQuery) "" else sortOrders.toSql92OrderByClause(clazz)) ?: ""
        if (orderBy.isNotBlank()) orderBy = ", $orderBy"

        // compute the {{PAGING}} replacement
        val paging: String = if (!isCountQuery && range != 0..Long.MAX_VALUE) {
            " " + quirks.offsetLimit(range.start.coerceAtMost(Int.MAX_VALUE.toLong()), range.length.coerceAtMost(Int.MAX_VALUE.toLong()))
        } else {
            ""
        }

        var s: String = sql.replace("{{WHERE}}", where).replace("{{ORDER}}", orderBy).replace("{{PAGING}}", paging)
        s = sqlPostProcessor(s, isCountQuery)

        // the count was obtained by a dirty trick - the ResultSet was simply scrolled to the last line and the row number is obtained.
        // however, PostgreSQL doesn't seem to like this: https://github.com/mvysny/vaadin-on-kotlin/issues/19
        // anyway there is a better way: simply wrap the select with "SELECT count(*) FROM (select)"
        if (isCountQuery) {
            // subquery in FROM must have an alias
            s = "SELECT count(*) FROM ($s) AS Foo"
        }
        return s
    }
}
