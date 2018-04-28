package com.github.vokorm.dataloader

import com.github.vokorm.Filter
import com.github.vokorm.db
import org.sql2o.Query

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
 *     from Customer c inner join Address a on c.address_id=a.id where 1=1 {{WHERE}} order by 1=1{{ORDER}} {{PAGING}}""", idMapper = { it })
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
 * @param clazz the type of the holder class which will hold the result
 * @param sql the select which can map into the holder class (that is, it selects columns whose names match the holder class fields). It should contain
 * `{{WHERE}}`, `{{ORDER}}` and `{{PAGING}}` strings which will be replaced by a simple substring replacement.
 * @param params the [sql] may be parametrized; this map holds all the parameters present in the sql itself.
 * @param T the type of the holder class.
 * @author mavi
 */
class SqlDataLoader<T: Any>(val clazz: Class<T>, val sql: String, val params: Map<String, Any?> = mapOf()) : DataLoader<T> {
    override fun toString() = "SqlDataLoader($clazz:$sql($params))"

    override fun getCount(filter: Filter<T>?): Int = db {
        val q: Query = con.createQuery(computeSQL(true, filter))
        params.entries.forEach { (name, value) -> q.addParameter(name, value) }
        q.fillInParamsFromFilters(filter)
        val count: Int = q.executeScalar(Int::class.java) ?: 0
        count
    }

    override fun fetch(filter: Filter<T>?, sortBy: List<SortClause>, range: IntRange): List<T> = db {
        val q = con.createQuery(computeSQL(false, filter, sortBy, range))
        params.entries.forEach { (name, value) -> q.addParameter(name, value) }
        q.fillInParamsFromFilters(filter)
        q.executeAndFetch(clazz)
    }

    private fun Query.fillInParamsFromFilters(filter: Filter<T>?): org.sql2o.Query {
        val filters: Filter<T> = filter ?: return this
        val params: Map<String, Any?> = filters.getSQL92Parameters()
        params.entries.forEach { (name, value) ->
            require(!this@SqlDataLoader.params.containsKey(name)) { "Filters tries to set the parameter $name to $value but that parameter is already forced by SqlDataLoader to ${params[name]}: filter=$filters dp=${this@SqlDataLoader}" }
            addParameter(name, value)
        }
        return this
    }

    private val IntRange.length: Int get() = if (isEmpty()) 0 else endInclusive - start + 1

    /**
     * Using [sql] as a template, computes the replacement strings for the `{{WHERE}}`, `{{ORDER}}` and `{{PAGING}}` replacement strings.
     */
    private fun computeSQL(isCountQuery: Boolean, filter: Filter<T>?, sortOrders: List<SortClause> = listOf(), range: IntRange = 0..Int.MAX_VALUE): String {
        // compute the {{WHERE}} replacement
        var where: String = filter?.toSQL92() ?: ""
        if (where.isNotBlank()) where = "and $where"

        // compute the {{ORDER}} replacement
        var orderBy: String = if (isCountQuery) "" else sortOrders.toSql92OrderByClause()
        if (orderBy.isNotBlank()) orderBy = ", $orderBy"

        // compute the {{PAGING}} replacement
        // MariaDB requires LIMIT first, then OFFSET: https://mariadb.com/kb/en/library/limit/
        val paging: String = if (!isCountQuery && range != 0..Int.MAX_VALUE) " LIMIT ${range.length} OFFSET ${range.start}" else ""

        var s: String = sql.replace("{{WHERE}}", where).replace("{{ORDER}}", orderBy).replace("{{PAGING}}", paging)

        // the count was obtained by a dirty trick - the ResultSet was simply scrolled to the last line and the row number is obtained.
        // however, PostgreSQL doesn't seem to like this: https://github.com/mvysny/vaadin-on-kotlin/issues/19
        // anyway there is a better way: simply wrap the select with "SELECT count(*) FROM (select)"
        if (isCountQuery) {
            // subquery in FROM must have an alias
            s = "SELECT count(*) FROM ($s) AS Foo"
        }
        return s
    }

    /**
     * Converts [SortClause] to something like "name ASC".
     */
    private fun SortClause.toSql92OrderByClause(): String = "$property ${if (asc) "ASC" else "DESC"}"

    /**
     * Converts a list of [SortClause] to something like "name DESC, age ASC". If the list is empty, returns an empty string.
     */
    private fun List<SortClause>.toSql92OrderByClause(): String = joinToString { it.toSql92OrderByClause() }
}
