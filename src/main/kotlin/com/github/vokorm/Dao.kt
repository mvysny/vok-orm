package com.github.vokorm

import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.FilterBuilder
import com.github.mvysny.vokdataloader.length
import com.gitlab.mvysny.jdbiorm.DaoOfAny
import com.gitlab.mvysny.jdbiorm.EntityMeta
import org.jdbi.v3.core.statement.Query

internal val <E> DaoOfAny<E>.meta: EntityMeta<E> get() = EntityMeta(entityClass)

/**
 * Retrieves single entity matching given [filter]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findOneBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
@Deprecated("use getOneBy()")
fun <T: Any> DaoOfAny<T>.getBy(filter: Filter<T>): T = getOneBy(filter)

/**
 * Retrieves single entity matching given [filter]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findOneBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
fun <T: Any> DaoOfAny<T>.getOneBy(filter: Filter<T>): T {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass)
    return getOneBy(sql.sql92) { query -> query.bind(sql) }
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
fun <T: Any> DaoOfAny<T>.getOneBy(block: FilterBuilder<T>.()-> Filter<T>): T =
        getOneBy(block(FilterBuilder(entityClass)))

/**
 * Retrieves single entity matching given criteria [block]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * Example:
 * ```
 * Person.getBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, the preferred way
 * Person.getBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programatically.
 * ```
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findSpecificBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
@Deprecated("use getOneBy()")
fun <T: Any> DaoOfAny<T>.getBy(block: FilterBuilder<T>.()-> Filter<T>): T =
        getOneBy(block)

/**
 * Retrieves specific entity matching given [filter]. Returns `null` if there is no such entity.
 * Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getOneBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
@Deprecated("use findOneBy()")
fun <T: Any> DaoOfAny<T>.findSpecificBy(filter: Filter<T>): T? =
        findOneBy(filter)

/**
 * Retrieves specific entity matching given criteria [block]. Fails if there are two or more entities matching the criteria.
 *
 * Example:
 * ```
 * Person.findSingleBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, the preferred way
 * Person.findSingleBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programatically.
 * ```
 *
 * This function returns `null` if there is no such entity. Use [getOneBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
@Deprecated("use findOneBy()")
fun <T: Any> DaoOfAny<T>.findSpecificBy(block: FilterBuilder<T>.()-> Filter<T>): T? =
        findOneBy(block)

/**
 * Retrieves specific entity matching given [filter]. Returns `null` if there is no such entity.
 * Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getOneBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
fun <T: Any> DaoOfAny<T>.findOneBy(filter: Filter<T>): T? {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass)
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
fun <T: Any> DaoOfAny<T>.findOneBy(block: FilterBuilder<T>.()-> Filter<T>): T? =
        findOneBy(block(FilterBuilder(entityClass)))

/**
 * Counts all rows in given table which matches given [block] clause.
 */
fun <T: Any> DaoOfAny<T>.count(block: FilterBuilder<T>.()-> Filter<T>): Long =
        count(FilterBuilder<T>(entityClass).block())

/**
 * Counts all rows in given table which matches given [filter].
 */
fun <T: Any> DaoOfAny<T>.count(filter: Filter<T>): Long {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass)
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
fun <T: Any> DaoOfAny<T>.deleteBy(block: FilterBuilder<T>.()-> Filter<T>) {
    deleteBy(FilterBuilder<T>(entityClass).block())
}

fun <T: Any> DaoOfAny<T>.deleteBy(filter: Filter<T>) {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass)
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
 */
fun <T: Any> DaoOfAny<T>.findAllBy(range: IntRange = IntRange(0, Int.MAX_VALUE),
                                   block: FilterBuilder<T>.()-> Filter<T>): List<T> =
    findAllBy(range, block(FilterBuilder<T>(entityClass)))

/**
 * Allows you to find rows by given [filter], with the maximum of [range] rows:
 *
 * If you want more complex stuff or even joins, fall back and just write
 * SQL:
 *
 * ```
 * db { con.createQuery("select * from Foo where name = :name").addParameter("name", name).executeAndFetch(Person::class.java) }
 * ```
 */
fun <T: Any> DaoOfAny<T>.findAllBy(range: IntRange = IntRange(0, Int.MAX_VALUE),
                                   filter: Filter<T>): List<T> {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass)
    val offset: Long? = if (range == IntRange(0, Int.MAX_VALUE)) null else range.start.toLong()
    val limit: Long? = if (range == IntRange(0, Int.MAX_VALUE)) null else range.length.toLong()
    return findAllBy(sql.sql92, offset, limit) { query: Query -> query.bind(sql) }
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
fun <T: Any> DaoOfAny<T>.existsBy(block: FilterBuilder<T>.()-> Filter<T>): Boolean =
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
fun <T: Any> DaoOfAny<T>.existsBy(filter: Filter<T>): Boolean {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass)
    return existsBy(sql.sql92) { query -> query.bind(sql) }
}
