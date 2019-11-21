package com.github.vokorm

import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.SqlWhereBuilder
import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.DaoOfAny
import com.gitlab.mvysny.jdbiorm.Entity
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.result.ResultIterator
import org.jdbi.v3.core.statement.Query
import java.sql.ResultSet
import java.sql.ResultSetMetaData

/**
 * Finds all instances of given entity. Fails if there is no table in the database with the name of [EntityMeta.databaseTableName]. The list is eager
 * and thus it's useful for smallish tables only.
 */
@Deprecated("use DaoOfAny")
fun <T : Any> Handle.findAll(clazz: Class<T>): List<T> = DaoOfAny(clazz).findAll()

/**
 * Retrieves entity with given [id]. Returns null if there is no such entity.
 */
@Deprecated("use Dao")
fun <ID, T : Entity<ID>> Handle.findById(clazz: Class<T>, id: ID): T? = Dao<T, ID>(clazz).findById(id)

/**
 * Retrieves entity with given [id]. Fails if there is no such entity.
 * @throws IllegalArgumentException if there is no entity with given id.
 */
@Deprecated("use Dao")
fun <ID, T : Entity<ID>> Handle.getById(clazz: Class<T>, id: ID): T = Dao<T, ID>(clazz).getById(id)

/**
 * Deletes all rows from given database table.
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.deleteAll(clazz: Class<T>) {
    DaoOfAny<T>(clazz).deleteAll()
}

/**
 * Counts all rows in given table [clazz].
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.getCount(clazz: Class<T>): Long =
        DaoOfAny<T>(clazz).count()

/**
 * Counts all rows in given table [clazz] satisfying given [filter].
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.getCount(clazz: Class<T>, filter: Filter<T>): Long =
        DaoOfAny<T>(clazz).count(filter)

/**
 * Retrieves specific entity matching given criteria [filter]. Fails if there are two or more entities matching the criteria.
 *
 * This function returns `null` if there is no such entity. Use [getBy] if you wish an exception to be thrown in case that
 * the entity does not exist.
 * @throws IllegalArgumentException if there are two or more matching entities.
 */
@Deprecated("use DaoOfAny.findOneBy()")
fun <T: Any> Handle.findSpecificBy(clazz: Class<T>, filter: Filter<T>): T? =
        DaoOfAny(clazz).findOneBy(filter)

/**
 * Returns the one entity with given [clazz] matching given [filter].
 * @throws IllegalArgumentException if there is none entity matching, or if there are two or more matching entities.
 */
@Deprecated("use DaoOfAny.getOneBy()")
fun <T: Any> Handle.getBy(clazz: Class<T>, filter: Filter<T>): T = DaoOfAny<T>(clazz).getOneBy(filter)

/**
 * Deletes all entities with given [clazz] matching given criteria [block].
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.deleteBy(clazz: Class<T>, block: SqlWhereBuilder<T>.()-> Filter<T>) =
        DaoOfAny<T>(clazz).deleteBy(block)

/**
 * Deletes all entities with given [clazz] matching given criteria [filter].
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.deleteBy(clazz: Class<T>, filter: Filter<T>) =
        DaoOfAny(clazz).deleteBy(filter)

/**
 * Checks whether there exists any instance of [clazz].
 */
@Deprecated("use DaoOfAny")
fun Handle.existsAny(clazz: Class<*>): Boolean =
        DaoOfAny(clazz).existsAny()

/**
 * Checks whether there exists any instance of [clazz] matching given criteria [filter].
 */
@Deprecated("use DaoOfAny")
fun <T: Any> Handle.existsBy(clazz: Class<T>, filter: Filter<T>): Boolean =
        DaoOfAny(clazz).existsBy(filter)

/**
 * Dumps the result of the query and returns it as a string formatted as follows:
 * ```
 * id, name, age, dateOfBirth, created, modified, alive, maritalStatus
 * -------------------------------------------------------------------
 * 1, Chuck Norris, 25, null, 2018-11-23 20:41:07.143, 2018-11-23 20:41:07.145, null, null
 * -------------------------------------------------------------------1 row(s)
 * ```
 * @return a pretty-printed outcome of given select
 */
fun Query.dump(): String {
    fun ResultSet.dumpCurrentRow(): String = (0 until metaData.columnCount).joinToString { "${getObject(it + 1)}" }

    val rows: ResultIterator<String> = map { rs, _ -> rs.dumpCurrentRow() }.iterator()
    val metadata: ResultSetMetaData = rows.context.statement.metaData
    return buildString {

        // draw the header and the separator
        val header: String = (0 until metadata.columnCount).joinToString { metadata.getColumnName(it + 1) }
        appendln(header)
        repeat(header.length) { append("-") }
        appendln()

        // draw the table body
        var rowCount = 0
        rows.forEach { row -> rowCount++; appendln(row) }

        // the bottom separator
        repeat(header.length) { append("-") }
        appendln("$rowCount row(s)")
    }
}
