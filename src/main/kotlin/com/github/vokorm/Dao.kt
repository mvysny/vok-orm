package com.github.vokorm

import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.FilterBuilder
import com.github.mvysny.vokdataloader.SortClause
import com.github.mvysny.vokdataloader.length
import com.gitlab.mvysny.jdbiorm.DaoOfAny
import com.gitlab.mvysny.jdbiorm.EntityMeta
import com.gitlab.mvysny.jdbiorm.JdbiOrm
import org.jdbi.v3.core.statement.Query
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

internal val <E> DaoOfAny<E>.meta: EntityMeta<E> get() = EntityMeta(entityClass)

/**
 * Retrieves single entity matching given [filter]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findOneBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
public fun <T : Any> DaoOfAny<T>.getOneBy(filter: Filter<T>): T {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass, JdbiOrm.databaseVariant!!)
    return getOneBy(sql.sql92) { query: Query -> query.bind(sql) }
}

/**
 * Retrieves single entity matching given criteria [block]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * Example:
 * ```
 * Person.getOneBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, the preferred way
 * Person.getOneBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programatically.
 * ```
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findOneBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
public fun <T : Any> DaoOfAny<T>.getOneBy(block: FilterBuilder<T>.() -> Filter<T>): T =
        getOneBy(block(FilterBuilder(entityClass)))

/**
 * Retrieves specific entity matching given [filter]. Returns `null` if there is no such entity.
 * Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getOneBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
public fun <T : Any> DaoOfAny<T>.findOneBy(filter: Filter<T>): T? {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass, JdbiOrm.databaseVariant!!)
    return findOneBy(sql.sql92) { query: Query -> query.bind(sql) }
}

/**
 * Retrieves specific entity matching given [filter]. Returns `null` if there is no such entity.
 * Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getOneBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
public fun <T : Any> DaoOfAny<T>.findOneBy(block: FilterBuilder<T>.() -> Filter<T>): T? =
        findOneBy(block(FilterBuilder(entityClass)))

/**
 * Counts all rows in given table which matches given [block] clause.
 */
public fun <T : Any> DaoOfAny<T>.count(block: FilterBuilder<T>.() -> Filter<T>): Long =
        count(FilterBuilder<T>(entityClass).block())

/**
 * Counts all rows in given table which matches given [filter].
 */
public fun <T : Any> DaoOfAny<T>.count(filter: Filter<T>): Long {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass, JdbiOrm.databaseVariant!!)
    return countBy(sql.sql92) { query: Query -> query.bind(sql) }
}

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
 * Allows you to find rows by given where clause, with the maximum of [range] rows:
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
 */
public fun <T : Any> DaoOfAny<T>.findAllBy(
        vararg orderBy: SortClause = arrayOf(),
        range: IntRange = IntRange(0, Int.MAX_VALUE),
        block: FilterBuilder<T>.() -> Filter<T>
): List<T> = findAllBy(orderBy = orderBy, range, block(FilterBuilder<T>(entityClass)))

/**
 * Finds all rows in given table. Fails if there is no table in the database with the
 * name of {@link EntityMeta#getDatabaseTableName()}. If both offset and limit
 * are specified, then the LIMIT and OFFSET sql paging is used.
 * @param orderBy if not null, this is passed in as the ORDER BY clause, e.g. {@code surname ASC, name ASC}. Careful: this goes into the SQL as-is - could be misused for SQL injection!
 * @param offset start from this row. If not null, must be 0 or greater.
 * @param limit return this count of row at most. If not null, must be 0 or greater.
 */
public fun <T : Any> DaoOfAny<T>.findAll(vararg orderBy: SortClause, range: IntRange = IntRange(0, Int.MAX_VALUE)): List<T> {
    val orderByClause: String? = orderBy.toList().toSql92OrderByClause(entityClass)
    val offset: Long? = if (range == IntRange(0, Int.MAX_VALUE)) null else range.start.toLong()
    val limit: Long? = if (range == IntRange(0, Int.MAX_VALUE)) null else range.length.toLong()
    return findAll(orderByClause, offset, limit)
}

/**
 * Allows you to find rows by given [filter], with the maximum of [range] rows:
 *
 * If you want more complex stuff or even joins, fall back and just write
 * SQL:
 *
 * ```
 * db { con.createQuery("select * from Foo where name = :name").addParameter("name", name).executeAndFetch(Person::class.java) }
 * ```
 * @param orderBy if not empty, this is passed in as the ORDER BY clause.
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
 * Checks whether there is any instance matching given block:
 *
 * ```
 * Person.existsBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, preferred
 * Person.existsBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programatically.
 * ```
 *
 * If you want more complex stuff or even joins, fall back and just write SQL:
 *
 * ```
 * db { con.createQuery("select count(1) from Foo where name = :name").addParameter("name", name).executeScalar(Long::class.java) > 0 }
 * ```
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
