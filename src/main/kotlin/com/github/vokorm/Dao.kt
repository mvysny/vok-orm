package com.github.vokorm

import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.FilterBuilder
import com.github.mvysny.vokdataloader.SortClause
import com.github.mvysny.vokdataloader.length
import com.gitlab.mvysny.jdbiorm.DaoOfAny
import com.gitlab.mvysny.jdbiorm.EntityMeta
import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.OrderBy
import com.gitlab.mvysny.jdbiorm.condition.Condition
import org.jdbi.v3.core.statement.Query

internal val <E> DaoOfAny<E>.meta: EntityMeta<E> get() = EntityMeta.of(entityClass)

/**
 * Retrieves single entity matching given criteria [block]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * Example:
 * ```
 * Person.getSingleBy { Person::name eq "Rubedo" }
 * ```
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findSingleBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
public fun <T : Any> DaoOfAny<T>.singleBy(block: ConditionBuilder<T>.() -> Condition): T =
        singleBy(block(ConditionBuilder(entityClass)))

/**
 * Retrieves specific entity matching given [filter]. Returns `null` if there is no such entity.
 * Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [singleBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
public fun <T : Any> DaoOfAny<T>.findSingleBy(block: ConditionBuilder<T>.() -> Condition): T? =
        findSingleBy(block(ConditionBuilder(entityClass)))

/**
 * Counts all rows in given table which matches given [block] clause.
 */
public fun <T : Any> DaoOfAny<T>.count(block: ConditionBuilder<T>.() -> Condition): Long =
        countBy(ConditionBuilder<T>(entityClass).block())

/**
 * Allows you to delete rows by given where clause:
 *
 * ```
 * Person.deleteBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, preferred
 * Person.deleteBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programatically.
 * ```
 *
 * If you want more complex stuff or even joins, fall back and just write SQL:
 *
 * ```
 * db { con.createQuery("delete from Foo where name = :name").addParameter("name", name).executeUpdate() }
 * ```
 */
public fun <T : Any> DaoOfAny<T>.deleteBy(block: FilterBuilder<T>.() -> Filter<T>) {
    deleteBy(FilterBuilder<T>(entityClass).block())
}

public fun <T : Any> DaoOfAny<T>.deleteBy(filter: Filter<T>) {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass, JdbiOrm.databaseVariant!!)
    deleteBy(sql.sql92) { query -> query.bind(sql) }
}

/**
 * Allows you to find rows by given where clause, fetching given [range] of rows:
 *
 * ```
 * Person.findAllBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, the preferred way
 * Person.findBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programatically
 * ```
 *
 * If you want more complex stuff or even joins, fall back and just write
 * SQL:
 *
 * ```
 * db { con.createQuery("select * from Foo where name = :name").addParameter("name", name).executeAndFetch(Person::class.java) }
 * ```
 * @param orderBy if not empty, this is passed in as the ORDER BY clause.
 * @param range use LIMIT+OFFSET to fetch given page of data. Defaults to all data.
 * @param block the filter to use.
 */
public fun <T : Any> DaoOfAny<T>.findAllBy(
        vararg orderBy: SortClause = arrayOf(),
        range: IntRange = IntRange(0, Int.MAX_VALUE),
        block: FilterBuilder<T>.() -> Filter<T>
): List<T> = findAllBy(orderBy = orderBy, range = range, filter = block(FilterBuilder<T>(entityClass)))

/**
 * Finds all rows in given table. Fails if there is no table in the database with the
 * name of {@link EntityMeta#getDatabaseTableName()}. If both offset and limit
 * are specified, then the LIMIT and OFFSET sql paging is used.
 * @param orderBy if not empty, this is passed in as the ORDER BY clause.
 * @param range use LIMIT+OFFSET to fetch given page of data. Defaults to all data.
 */
public fun <T : Any> DaoOfAny<T>.findAll(
    vararg orderBy: OrderBy = arrayOf(),
    range: IntRange = IntRange(0, Int.MAX_VALUE)
): List<T> {
    val offset: Long? = if (range == IntRange(0, Int.MAX_VALUE)) null else range.start.toLong()
    val limit: Long? = if (range == IntRange(0, Int.MAX_VALUE)) null else range.length.toLong()
    return findAll(orderBy.toList(), offset, limit)
}

/**
 * Allows you to find rows by given [filter], fetching given [range] of rows:
 *
 * If you want more complex stuff or even joins, fall back and just write
 * SQL:
 *
 * ```
 * db { con.createQuery("select * from Foo where name = :name").addParameter("name", name).executeAndFetch(Person::class.java) }
 * ```
 * @param orderBy if not empty, this is passed in as the ORDER BY clause.
 * @param range use LIMIT+OFFSET to fetch given page of data. Defaults to all data.
 * @param filter the filter to use.
 */
public fun <T : Any> DaoOfAny<T>.findAllBy(
            vararg orderBy: SortClause = arrayOf(),
            range: IntRange = IntRange(0, Int.MAX_VALUE),
            filter: Filter<T>
    ): List<T> {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass, JdbiOrm.databaseVariant!!)
    val offset: Long? = if (range == IntRange(0, Int.MAX_VALUE)) null else range.start.toLong()
    val limit: Long? = if (range == IntRange(0, Int.MAX_VALUE)) null else range.length.toLong()
    val orderByClause: String? = orderBy.toList().toSql92OrderByClause(entityClass)
    return findAllBy(sql.sql92, orderByClause, offset, limit) { query: Query -> query.bind(sql) }
}

/**
 * Checks whether there is any instance matching given [block]:
 *
 * ```
 * Person.existsBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, preferred
 * Person.existsBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programmatically.
 * ```
 *
 * If you want more complex stuff or even joins, fall back and just write SQL:
 *
 * ```
 * db { con.createQuery("select count(1) from Foo where name = :name").addParameter("name", name).executeScalar(Long::class.java) > 0 }
 * ```
 * @param block the filter to use.
 */
public fun <T : Any> DaoOfAny<T>.existsBy(block: FilterBuilder<T>.() -> Filter<T>): Boolean =
        existsBy(block(FilterBuilder(entityClass)))

/**
 * Checks whether there is any instance matching given [filter].
 *
 * If you want more complex stuff or even joins, fall back and just write SQL:
 *
 * ```
 * db { con.createQuery("select count(1) from Foo where name = :name").addParameter("name", name).executeScalar(Long::class.java) > 0 }
 * ```
 */
public fun <T : Any> DaoOfAny<T>.existsBy(filter: Filter<T>): Boolean {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass, JdbiOrm.databaseVariant!!)
    return existsBy(sql.sql92) { query -> query.bind(sql) }
}
