package com.github.vokorm

import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.SqlWhereBuilder
import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.DaoOfAny
import com.gitlab.mvysny.jdbiorm.Entity
import org.jdbi.v3.core.statement.Query

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
inline fun <ID: Any, reified T: Entity<ID>> Dao<T, ID>.getBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): T =
        getBy(block(SqlWhereBuilder(T::class.java)))

/**
 * Retrieves single entity matching given [filter]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findSpecificBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
inline fun <ID: Any, reified T: Entity<ID>> Dao<T, ID>.getBy(filter: Filter<T>): T = db { handle.getBy(T::class.java, filter) }

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
inline fun <reified T: Any> DaoOfAny<T>.getBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): T =
        getBy(block(SqlWhereBuilder(T::class.java)))

/**
 * Retrieves single entity matching given [filter]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findSpecificBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
fun <T: Any> DaoOfAny<T>.getBy(filter: Filter<T>): T = db { handle.getBy(T::class.java, filter) }

/**
 * Retrieves specific entity matching given criteria [block]. Returns `null` if there is no such entity.
 * Fails if there are two or more entities matching the criteria.
 *
 * Example:
 * ```
 * Person.findSpecificBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, the preferred way
 * Person.findSpecificBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programatically.
 * ```
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
@Deprecated("use findOneBy()")
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.findSpecificBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): T? =
        findSpecificBy(block(SqlWhereBuilder(T::class.java)))

/**
 * Retrieves specific entity matching given [filter]. Returns `null` if there is no such entity.
 * Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
@Deprecated("use findOneBy()")
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.findSpecificBy(filter: Filter<T>): T? =
        db { handle.findSpecificBy(T::class.java, filter) }

/**
 * Retrieves specific entity matching given criteria [block]. Fails if there are two or more entities matching the criteria.
 *
 * Example:
 * ```
 * Person.findSingleBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, the preferred way
 * Person.findSingleBy { Person::name eq "Rubedo" }  // fancy type-safe criteria, useful when you need to construct queries programatically.
 * ```
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
@Deprecated("use findOneBy()")
inline fun <reified T: Any> DaoOfAny<T>.findSpecificBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): T? =
        findSpecificBy(block(SqlWhereBuilder(T::class.java)))

/**
 * Retrieves specific entity matching given [filter]. Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
@Deprecated("use findOneBy()")
inline fun <reified T: Any> DaoOfAny<T>.findSpecificBy(filter: Filter<T>): T? = findOneBy(filter)

fun <T: Any> DaoOfAny<T>.findOneBy(filter: Filter<T>): T? {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass)
    findOneBy(sql.sql92) { query -> query.bind(sql) }
}

/**
 * Counts all rows in given table which matches given [block] clause.
 */
inline fun <reified T: Entity<*>> Dao<T, *>.count(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Long =
        count(SqlWhereBuilder(T::class.java).block())

/**
 * Counts all rows in given table which matches given [block] clause.
 */
inline fun <reified T: Any> DaoOfAny<T>.count(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Long =
        count(SqlWhereBuilder(T::class.java).block())

/**
 * Counts all rows in given table which matches given [filter].
 */
fun <T: Any> DaoOfAny<T>.count(filter: Filter<T>): Long = db {
    val sql: ParametrizedSql = filter.toParametrizedSql(entityClass)
    val query: Query = handle.createQuery("select count(*) from <TABLE> where <WHERE>")
            .define("TABLE", meta.databaseTableName)
            .define("WHERE", sql.sql92)
    sql.sql92Parameters.entries.forEach { (name, value) -> query.bind(name, value) }
    query.mapTo(Long::class.java).one()
}

/**
 * Deletes row with given ID. Does nothing if there is no such row.
 */
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.deleteById(id: ID): Unit = db { handle.deleteById(T::class.java, id) }

/**
 * Deletes row with given ID. Does nothing if there is no such row.
 */
inline fun <reified T: Any> DaoOfAny<T>.deleteById(id: Any): Unit = db { handle.deleteById(T::class.java, id) }

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
inline fun <reified T: Entity<*>> Dao<T>.deleteBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Unit =
    db { handle.deleteBy(T::class.java, block) }

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
inline fun <reified T: Any> DaoOfAny<T>.deleteBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Unit =
    db { handle.deleteBy(T::class.java, block) }

/**
 * Returns the metadata for this entity.
 */
inline val <reified T: Entity<*>> Dao<T>.meta: EntityMeta
    get() = EntityMeta(T::class.java)

/**
 * Allows you to find rows by given where clause, with the maximum of [limit] rows:
 *
 * ```
 * Person.findBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, the preferred way
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
inline fun <reified T: Any> DaoOfAny<T>.findBy(limit: Int = Int.MAX_VALUE, noinline block: SqlWhereBuilder<T>.()-> Filter<T>): List<T> =
    findBy(limit, block(SqlWhereBuilder(T::class.java)))

/**
 * Allows you to find rows by given [filter], with the maximum of [limit] rows:
 *
 * If you want more complex stuff or even joins, fall back and just write
 * SQL:
 *
 * ```
 * db { con.createQuery("select * from Foo where name = :name").addParameter("name", name).executeAndFetch(Person::class.java) }
 * ```
 */
inline fun <reified T: Any> DaoOfAny<T>.findBy(limit: Int = Int.MAX_VALUE, filter: Filter<T>): List<T> =
        db { handle.findBy(T::class.java, limit, filter) }

/**
 * Allows you to find rows by given `where` clause, with the maximum of [limit] rows:
 *
 * ```
 * Person.findBy { "name = :name"("name" to "Albedo") }  // raw sql where clause with parameters, the preferred way
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
inline fun <ID, reified T: Entity<ID>> Dao<T>.findBy(limit: Int = Int.MAX_VALUE, noinline block: SqlWhereBuilder<T>.()-> Filter<T>): List<T> =
    findBy(limit, block(SqlWhereBuilder(T::class.java)))

/**
 * Allows you to find rows by given [filter], with the maximum of [limit] rows:
 *
 * If you want more complex stuff or even joins, fall back and just write
 * SQL:
 *
 * ```
 * db { con.createQuery("select * from Foo where name = :name").addParameter("name", name).executeAndFetch(Person::class.java) }
 * ```
 */
inline fun <ID, reified T: Entity<ID>> Dao<T>.findBy(limit: Int = Int.MAX_VALUE, filter: Filter<T>): List<T> =
        db { handle.findBy(T::class.java, limit, filter) }

/**
 * Checks whether there is any instance of this entity in the database.
 */
inline fun <ID, reified T: Entity<ID>> Dao<T>.existsAny(): Boolean = db { handle.existsAny(T::class.java) }

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
inline fun <ID, reified T: Entity<ID>> Dao<T>.existsBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Boolean =
        existsBy(block(SqlWhereBuilder(T::class.java)))

/**
 * Checks whether there is any instance matching given [filter].
 *
 * If you want more complex stuff or even joins, fall back and just write SQL:
 *
 * ```
 * db { con.createQuery("select count(1) from Foo where name = :name").addParameter("name", name).executeScalar(Long::class.java) > 0 }
 * ```
 */
inline fun <ID, reified T : Entity<ID>> Dao<T>.existsBy(filter: Filter<T>): Boolean = db { handle.existsBy(T::class.java, filter) }

/**
 * Checks whether there is an instance of this entity in the database with given [id].
 */
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.existsById(id: ID): Boolean = db { handle.existsById(T::class.java, id) }

/**
 * Checks whether there is any instance of this entity in the database.
 */
inline fun <reified T: Any> DaoOfAny<T>.existsAny(): Boolean = db { handle.existsAny(T::class.java) }

inline fun <reified T: Any> DaoOfAny<T>.existsBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Boolean =
        existsBy(block(SqlWhereBuilder(T::class.java)))

inline fun <reified T: Any> DaoOfAny<T>.existsBy(filter: Filter<T>): Boolean = db {
    handle.existsBy(T::class.java, filter)
}
