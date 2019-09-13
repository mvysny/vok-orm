package com.github.vokorm

import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.SqlWhereBuilder

/**
 * Data access object, provides instances of given [Entity]. To use, just let your [Entity]'s companion class implement this interface, say:
 *
 * ```
 * data class Person(...) : Entity<Long> {
 *   companion class : Dao<Person>
 * }
 * ```
 *
 * You can now use `Person.findAll()`, `Person.getById(25)` and other nice methods :)
 * @param T the type of the [Entity] provided by this Dao
 */
interface Dao<T: Entity<*>>

/**
 * Sometimes you don't want a class to be an entity for some reason (e.g. when it doesn't have a primary key),
 * but still it's mapped to a table and you would want to have Dao support for that class.
 * Just let your class's companion class implement this interface, say:
 *
 * ```
 * data class Log(...) {
 *   companion class : DaoOfAny<Log>
 * }
 * ```
 *
 * You can now use `Log.findAll()`, `Log.count()` and other nice methods.
 * @param T the type of the class provided by this Dao
 */
interface DaoOfAny<T: Any>

/**
 * Finds all rows in given table. Fails if there is no table in the database with the name of [databaseTableName]. The list is eager
 * and thus it's useful for small-ish tables only.
 */
inline fun <reified T: Any> DaoOfAny<T>.findAll(): List<T> = db { con.findAll(T::class.java) }

/**
 * Finds all rows in given table. Fails if there is no table in the database with the name of [databaseTableName]. The list is eager
 * and thus it's useful for small-ish tables only.
 */
inline fun <ID, reified T: Entity<ID>> Dao<T>.findAll(): List<T> = db { con.findAll(T::class.java) }

/**
 * Retrieves entity with given [id]. Fails if there is no such entity. See [Dao] on how to add this to your entities.
 * @throws IllegalArgumentException if there is no entity with given id.
 */
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.getById(id: ID): T =
    db { con.getById(T::class.java, id) }

/**
 * Retrieves entity with given [id]. Fails if there is no such entity. See [DaoOfAny] on how to add this to your entities.
 *
 * This will only work if there is a field named `id` in the class.
 * @throws IllegalArgumentException if there is no entity with given id.
 */
inline fun <reified T: Any> DaoOfAny<T>.getById(id: Any): T = db { con.getById(T::class.java, id) }

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
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.getBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): T =
        getBy(block(SqlWhereBuilder(T::class.java)))

/**
 * Retrieves single entity matching given [filter]. Fails if there is no such entity, or if there are two or more entities matching the criteria.
 *
 * This function fails if there is no such entity or there are 2 or more. Use [findSpecificBy] if you wish to return `null` in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there is no entity matching given criteria, or if there are two or more matching entities.
 */
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.getBy(filter: Filter<T>): T = db { con.getBy(T::class.java, filter) }

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
inline fun <reified T: Any> DaoOfAny<T>.getBy(filter: Filter<T>): T = db { con.getBy(T::class.java, filter) }

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
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.findSpecificBy(filter: Filter<T>): T? =
        db { con.findSpecificBy(T::class.java, filter) }

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
inline fun <reified T: Any> DaoOfAny<T>.findSpecificBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): T? =
        findSpecificBy(block(SqlWhereBuilder(T::class.java)))

/**
 * Retrieves specific entity matching given [filter]. Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
inline fun <reified T: Any> DaoOfAny<T>.findSpecificBy(filter: Filter<T>): T? =
        db { con.findSpecificBy(T::class.java, filter) }

/**
 * Retrieves entity with given [id]. Returns null if there is no such entity.
 */
inline fun <ID: Any, reified T : Entity<ID>> Dao<T>.findById(id: ID): T? =
    db { con.findById(T::class.java, id) }

/**
 * Retrieves entity with given [id]. Returns null if there is no such entity.
 */
inline fun <reified T : Any> DaoOfAny<T>.findById(id: Any): T? = db { con.findById(T::class.java, id) }

/**
 * Deletes all rows from given database table.
 */
inline fun <reified T: Any> DaoOfAny<T>.deleteAll(): Unit = db { con.deleteAll(T::class.java) }

/**
 * Deletes all rows from given database table.
 */
inline fun <reified T: Entity<*>> Dao<T>.deleteAll(): Unit = db { con.deleteAll(T::class.java) }

/**
 * Counts all rows in given table.
 */
inline fun <reified T: Entity<*>> Dao<T>.count(): Long = db { con.getCount(T::class.java) }

/**
 * Counts all rows in given table.
 */
inline fun <reified T: Any> DaoOfAny<T>.count(): Long = db { con.getCount(T::class.java) }

/**
 * Counts all rows in given table which matches given [block] clause.
 */
inline fun <reified T: Entity<*>> Dao<T>.count(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Long =
        count(SqlWhereBuilder(T::class.java).block())

/**
 * Counts all rows in given table which matches given [filter].
 */
inline fun <reified T: Entity<*>> Dao<T>.count(filter: Filter<T>): Long =
        db { con.getCount(T::class.java, filter) }

/**
 * Counts all rows in given table which matches given [block] clause.
 */
inline fun <reified T: Any> DaoOfAny<T>.count(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Long =
        count(SqlWhereBuilder(T::class.java).block())

/**
 * Counts all rows in given table which matches given [filter].
 */
inline fun <reified T: Any> DaoOfAny<T>.count(filter: Filter<T>): Long =
        db { con.getCount(T::class.java, filter) }

/**
 * Deletes row with given ID. Does nothing if there is no such row.
 */
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.deleteById(id: ID): Unit = db { con.deleteById(T::class.java, id) }

/**
 * Deletes row with given ID. Does nothing if there is no such row.
 */
inline fun <reified T: Any> DaoOfAny<T>.deleteById(id: Any): Unit = db { con.deleteById(T::class.java, id) }

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
    db { con.deleteBy(T::class.java, block) }

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
    db { con.deleteBy(T::class.java, block) }

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
        db { con.findBy(T::class.java, limit, filter) }

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
        db { con.findBy(T::class.java, limit, filter) }

/**
 * Checks whether there is any instance of this entity in the database.
 */
inline fun <ID, reified T: Entity<ID>> Dao<T>.existsAny(): Boolean = db { con.existsAny(T::class.java) }

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
inline fun <ID, reified T : Entity<ID>> Dao<T>.existsBy(filter: Filter<T>): Boolean = db { con.existsBy(T::class.java, filter) }

/**
 * Checks whether there is an instance of this entity in the database with given [id].
 */
inline fun <ID: Any, reified T: Entity<ID>> Dao<T>.existsById(id: ID): Boolean = db { con.existsById(T::class.java, id) }

/**
 * Checks whether there is any instance of this entity in the database.
 */
inline fun <reified T: Any> DaoOfAny<T>.existsAny(): Boolean = db { con.existsAny(T::class.java) }

inline fun <reified T: Any> DaoOfAny<T>.existsBy(noinline block: SqlWhereBuilder<T>.()-> Filter<T>): Boolean =
        existsBy(block(SqlWhereBuilder(T::class.java)))

inline fun <reified T: Any> DaoOfAny<T>.existsBy(filter: Filter<T>): Boolean = db {
    con.existsBy(T::class.java, filter)
}
